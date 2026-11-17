package com.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.rooms.auth.Role;
import com.rooms.booking.BookingService;
import com.rooms.support.AbstractIT;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class BookingIT extends AbstractIT {
  @Autowired BookingService service;

  private JsonNode searchJson(String token, Instant s, int people, String amenities) throws Exception {
    ZoneId z = ZoneId.of("Asia/Kolkata");
    String url =
        "/api/rooms/available?date="
            + s.atZone(z).toLocalDate()
            + "&start="
            + s.atZone(z).toLocalTime()
            + "&end="
            + s.plus(Duration.ofHours(1)).atZone(z).toLocalTime()
            + "&people="
            + people
            + (amenities == null ? "" : "&amenities=" + amenities);
    MvcResult r = mvc.perform(auth(get(url), token)).andExpect(status().isOk()).andReturn();
    return json.readTree(r.getResponse().getContentAsString());
  }

  private List<String> names(JsonNode result) {
    List<String> out = new ArrayList<>();
    result.get("rooms").forEach(n -> out.add(n.get("room").get("name").asText()));
    return out;
  }

  @Test
  void searchFiltersByCapacityAndSortsBestFitFirst() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    assertThat(names(searchJson(t, s, 3, null))).containsExactly("Kaveri", "Ganga", "Narmada");
    assertThat(names(searchJson(t, s, 6, null))).containsExactly("Ganga", "Narmada");
    assertThat(names(searchJson(t, s, 10, null))).containsExactly("Narmada");
  }

  @Test
  void searchFiltersByEquipment() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    assertThat(names(searchJson(t, s, 2, "PROJECTOR,VIDEO_CALL"))).containsExactly("Narmada");
    assertThat(names(searchJson(t, s, 2, "WHITEBOARD"))).containsExactly("Kaveri");
  }

  @Test
  void bookedRoomIsHiddenAndShowsAsBusyOnOthers() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s, s.plus(Duration.ofHours(1)), null)))
        .andExpect(status().isCreated());
    JsonNode res = searchJson(t, s, 2, null);
    assertThat(names(res)).containsExactly("Ganga", "Narmada");
    // the day availability bar for a free room still reports the other bookings of that day
    Instant overlapping = s.plus(Duration.ofMinutes(30));
    assertThat(names(searchJson(t, overlapping, 2, null))).doesNotContain("Kaveri");
  }

  @Test
  void searchOutsideOpenHoursReturnsNoRoomsAndSuggestsNearby() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 19, 0);
    JsonNode res = searchJson(t, s, 2, null);
    assertThat(res.get("rooms")).isEmpty();
    assertThat(res.get("nearby")).isNotEmpty();
  }

  @Test
  void whenEveryRoomIsBusyANearbyTimeIsSuggested() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    for (var room : List.of(small, big, checkIn)) {
      mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(room, s, s.plus(Duration.ofHours(1)), null)))
          .andExpect(status().isCreated());
    }
    JsonNode res = searchJson(t, s, 2, null);
    assertThat(res.get("rooms")).isEmpty();
    JsonNode first = res.get("nearby").get(0);
    assertThat(Instant.parse(first.get("startsAt").asText())).isEqualTo(s.plus(Duration.ofHours(1)));
  }

  @Test
  void overlapIsA409WithAlternatives() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s, s.plus(Duration.ofHours(1)), null)))
        .andExpect(status().isCreated());
    mvc.perform(
            auth(post("/api/bookings"), t)
                .content(bookingJson(small, s.plus(Duration.ofMinutes(30)), s.plus(Duration.ofMinutes(90)), null)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.alternatives").isNotEmpty())
        .andExpect(jsonPath("$.conflicts").isNotEmpty());
  }

  @Test
  void backToBackBookingsAreAllowed() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s, s.plus(Duration.ofHours(1)), null)))
        .andExpect(status().isCreated());
    mvc.perform(
            auth(post("/api/bookings"), t)
                .content(bookingJson(small, s.plus(Duration.ofHours(1)), s.plus(Duration.ofHours(2)), null)))
        .andExpect(status().isCreated());
  }

  @Test
  void fiftyParallelRequestsForOneSlotYieldExactlyOneSuccess() throws Exception {
    List<String> tokens = new ArrayList<>();
    for (int i = 0; i < 50; i++) tokens.add(login("u" + i + "@example.com", Role.EMPLOYEE));
    Instant s = slot(4, 11, 0);
    String body = bookingJson(small, s, s.plus(Duration.ofHours(1)), null);
    ExecutorService pool = Executors.newFixedThreadPool(50);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger created = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();
    AtomicInteger other = new AtomicInteger();
    List<Future<?>> fs = new ArrayList<>();
    for (String t : tokens) {
      fs.add(
          pool.submit(
              () -> {
                go.await();
                int st = mvc.perform(auth(post("/api/bookings"), t).content(body)).andReturn().getResponse().getStatus();
                if (st == 201) created.incrementAndGet();
                else if (st == 409) conflicts.incrementAndGet();
                else other.incrementAndGet();
                return null;
              }));
    }
    go.countDown();
    for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
    pool.shutdown();
    assertThat(created.get()).isEqualTo(1);
    assertThat(conflicts.get()).isEqualTo(49);
    assertThat(other.get()).isZero();
  }

  @Test
  void databaseConstraintRejectsOverlapEvenWithoutTheApplicationCheck() {
    Instant s = slot(3, 10, 0);
    var owner = user("a@example.com", Role.EMPLOYEE);
    String sql =
        "insert into bookings (id, room_id, organizer_id, title, starts_at, ends_at, status, version) values (gen_random_uuid(), ?, ?, 't', ?, ?, 'CONFIRMED', 0)";
    var ts = (java.util.function.Function<Instant, java.sql.Timestamp>) java.sql.Timestamp::from;
    jdbc.update(sql, small.getId(), owner.getId(), ts.apply(s), ts.apply(s.plus(Duration.ofHours(1))));
    org.springframework.dao.DataIntegrityViolationException ex =
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> jdbc.update(sql, small.getId(), owner.getId(), ts.apply(s.plus(Duration.ofMinutes(30))), ts.apply(s.plus(Duration.ofMinutes(90)))));
    assertThat(ex.getMessage()).contains("bookings_no_overlap");
  }

  @Test
  void validationRules() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    // too many people
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s, s.plus(Duration.ofHours(1)), "\"people\":9")))
        .andExpect(status().isBadRequest());
    // past
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, Instant.parse("2020-01-06T04:30:00Z"), Instant.parse("2020-01-06T05:30:00Z"), null)))
        .andExpect(status().isBadRequest());
    // too short and too long
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s, s.plus(Duration.ofMinutes(10)), null)))
        .andExpect(status().isBadRequest());
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s, s.plus(Duration.ofHours(9)), null)))
        .andExpect(status().isBadRequest());
    // not on a 15 minute step
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s, s.plus(Duration.ofMinutes(50)), null)))
        .andExpect(status().isBadRequest());
    // outside open hours
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, slot(3, 8, 0), slot(3, 9, 0), null)))
        .andExpect(status().isBadRequest());
    // more than 90 days out
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, slot(100, 10, 0), slot(100, 11, 0), null)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void organizerCanRescheduleAndCancelOthersCannot() throws Exception {
    String owner = login("a@example.com", Role.EMPLOYEE);
    String other = login("b@example.com", Role.EMPLOYEE);
    String admin = login("c@example.com", Role.ADMIN);
    Instant s = slot(3, 10, 0);
    MvcResult r =
        mvc.perform(auth(post("/api/bookings"), owner).content(bookingJson(small, s, s.plus(Duration.ofHours(1)), null)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode b = json.readTree(r.getResponse().getContentAsString()).get(0);
    String id = b.get("id").asText();

    mvc.perform(auth(get("/api/bookings/" + id), other)).andExpect(status().isNotFound());
    mvc.perform(auth(delete("/api/bookings/" + id), other)).andExpect(status().isNotFound());

    Instant moved = s.plus(Duration.ofHours(2));
    mvc.perform(
            auth(patch("/api/bookings/" + id), owner)
                .content("{\"startsAt\":\"" + moved + "\",\"endsAt\":\"" + moved.plus(Duration.ofHours(1)) + "\",\"version\":" + b.get("version").asLong() + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.startsAt").value(moved.toString()));

    // stale version
    mvc.perform(
            auth(patch("/api/bookings/" + id), owner)
                .content("{\"title\":\"New\",\"version\":" + b.get("version").asLong() + "}"))
        .andExpect(status().isConflict());

    mvc.perform(auth(delete("/api/bookings/" + id), admin)).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/bookings/" + id), owner)).andExpect(jsonPath("$.status").value("CANCELLED"));
    // slot is free again
    mvc.perform(auth(post("/api/bookings"), owner).content(bookingJson(small, moved, moved.plus(Duration.ofHours(1)), null)))
        .andExpect(status().isCreated());
  }

  @Test
  void rescheduleRerunsConflictChecks() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s, s.plus(Duration.ofHours(1)), null)));
    MvcResult r =
        mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s.plus(Duration.ofHours(2)), s.plus(Duration.ofHours(3)), null))).andReturn();
    String id = json.readTree(r.getResponse().getContentAsString()).get(0).get("id").asText();
    mvc.perform(
            auth(patch("/api/bookings/" + id), t)
                .content("{\"startsAt\":\"" + s + "\",\"endsAt\":\"" + s.plus(Duration.ofHours(1)) + "\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void myBookingsSplitsUpcomingAndPast() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    mvc.perform(auth(post("/api/bookings"), t).content(bookingJson(small, s, s.plus(Duration.ofHours(1)), null)));
    mvc.perform(auth(get("/api/bookings/mine"), t)).andExpect(jsonPath("$.length()").value(1));
    mvc.perform(auth(get("/api/bookings/mine?when=past"), t)).andExpect(jsonPath("$.length()").value(0));
  }
}
