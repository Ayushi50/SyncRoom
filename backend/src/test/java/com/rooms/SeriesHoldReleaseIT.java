package com.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.rooms.auth.Role;
import com.rooms.booking.*;
import com.rooms.support.AbstractIT;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SeriesHoldReleaseIT extends AbstractIT {
  @Autowired BookingService service;
  @Autowired BookingRepository bookings;

  private JsonNode post201(String t, String body) throws Exception {
    return json.readTree(
        mvc.perform(auth(post("/api/bookings"), t).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void weeklySeriesCreatesAllOccurrences() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    JsonNode res =
        post201(
            t,
            bookingJson(
                small,
                s,
                s.plus(Duration.ofHours(1)),
                "\"repeat\":{\"pattern\":\"WEEKLY\",\"count\":4}"));
    assertThat(res).hasSize(4);
    assertThat(Instant.parse(res.get(3).get("startsAt").asText()))
        .isEqualTo(s.plus(Duration.ofDays(21)));
    assertThat(res.get(0).get("seriesId").asText()).isEqualTo(res.get(3).get("seriesId").asText());
  }

  @Test
  void weekdaySeriesSkipsWeekends() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    JsonNode res =
        post201(
            t,
            bookingJson(
                small,
                s,
                s.plus(Duration.ofHours(1)),
                "\"repeat\":{\"pattern\":\"WEEKDAYS\",\"count\":8}"));
    for (JsonNode b : res) {
      var dow =
          Instant.parse(b.get("startsAt").asText())
              .atZone(java.time.ZoneId.of("Asia/Kolkata"))
              .getDayOfWeek();
      assertThat(dow).isNotIn(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY);
    }
    assertThat(res).hasSize(8);
  }

  @Test
  void seriesLongerThanTwelveIsRejected() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    mvc.perform(
            auth(post("/api/bookings"), t)
                .content(
                    bookingJson(
                        small,
                        s,
                        s.plus(Duration.ofHours(1)),
                        "\"repeat\":{\"pattern\":\"DAILY\",\"count\":13}")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void oneConflictingOccurrenceCreatesNoneAndNamesTheDate() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    String other = login("b@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    Instant second = s.plus(Duration.ofDays(7));
    post201(other, bookingJson(small, second, second.plus(Duration.ofHours(1)), null));
    mvc.perform(
            auth(post("/api/bookings"), t)
                .content(
                    bookingJson(
                        small,
                        s,
                        s.plus(Duration.ofHours(1)),
                        "\"repeat\":{\"pattern\":\"WEEKLY\",\"count\":3}")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.conflicts[0]").value(second.toString()));
    assertThat(bookings.count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from booking_series", Integer.class)).isZero();
  }

  @Test
  void cancelFollowingLeavesEarlierOccurrencesAndThisOneOnlyLeavesTheRest() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    JsonNode res =
        post201(
            t,
            bookingJson(
                small,
                s,
                s.plus(Duration.ofHours(1)),
                "\"repeat\":{\"pattern\":\"WEEKLY\",\"count\":4}"));
    mvc.perform(auth(delete("/api/bookings/" + res.get(1).get("id").asText() + "?scope=THIS"), t))
        .andExpect(status().isNoContent());
    assertThat(statuses()).containsExactly("CONFIRMED", "CANCELLED", "CONFIRMED", "CONFIRMED");
    mvc.perform(
            auth(delete("/api/bookings/" + res.get(2).get("id").asText() + "?scope=FOLLOWING"), t))
        .andExpect(status().isNoContent());
    assertThat(statuses()).containsExactly("CONFIRMED", "CANCELLED", "CANCELLED", "CANCELLED");
  }

  private java.util.List<String> statuses() {
    return jdbc.queryForList("select status from bookings order by starts_at", String.class);
  }

  @Test
  void rescheduleFollowingMovesTheRestOfTheSeries() throws Exception {
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    JsonNode res =
        post201(
            t,
            bookingJson(
                small,
                s,
                s.plus(Duration.ofHours(1)),
                "\"repeat\":{\"pattern\":\"WEEKLY\",\"count\":3}"));
    String second = res.get(1).get("id").asText();
    Instant ns = Instant.parse(res.get(1).get("startsAt").asText()).plus(Duration.ofHours(2));
    mvc.perform(
            auth(patch("/api/bookings/" + second), t)
                .content(
                    "{\"startsAt\":\""
                        + ns
                        + "\",\"endsAt\":\""
                        + ns.plus(Duration.ofHours(1))
                        + "\",\"scope\":\"FOLLOWING\"}"))
        .andExpect(status().isOk());
    var starts =
        jdbc.queryForList(
            "select starts_at from bookings order by starts_at", java.sql.Timestamp.class);
    assertThat(starts.get(0).toInstant()).isEqualTo(s);
    assertThat(starts.get(1).toInstant()).isEqualTo(ns);
    assertThat(starts.get(2).toInstant()).isEqualTo(ns.plus(Duration.ofDays(7)));
  }

  @Test
  void holdBlocksOthersUntilItExpires() throws Exception {
    String a = login("a@example.com", Role.EMPLOYEE);
    String b = login("b@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    String hold =
        mvc.perform(
                auth(post("/api/holds"), a)
                    .content(
                        "{\"roomId\":\""
                            + small.getId()
                            + "\",\"startsAt\":\""
                            + s
                            + "\",\"endsAt\":\""
                            + s.plus(Duration.ofHours(1))
                            + "\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode h = json.readTree(hold);
    Instant exp = Instant.parse(h.get("expiresAt").asText());
    assertThat(Duration.between(Instant.now(), exp).toSeconds()).isBetween(280L, 301L);

    mvc.perform(
            auth(post("/api/bookings"), b)
                .content(bookingJson(small, s, s.plus(Duration.ofHours(1)), null)))
        .andExpect(status().isConflict());
    mvc.perform(
            auth(post("/api/holds"), b)
                .content(
                    "{\"roomId\":\""
                        + small.getId()
                        + "\",\"startsAt\":\""
                        + s
                        + "\",\"endsAt\":\""
                        + s.plus(Duration.ofHours(1))
                        + "\"}"))
        .andExpect(status().isConflict());

    jdbc.update("update slot_holds set expires_at = now() - interval '1 second'");
    // expired holds no longer block, even before the scheduler has deleted them
    mvc.perform(
            auth(post("/api/holds"), b)
                .content(
                    "{\"roomId\":\""
                        + small.getId()
                        + "\",\"startsAt\":\""
                        + s
                        + "\",\"endsAt\":\""
                        + s.plus(Duration.ofHours(1))
                        + "\"}"))
        .andExpect(status().isCreated());
    assertThat(service.purgeExpiredHolds()).isEqualTo(1);
  }

  @Test
  void confirmingWithAHoldConsumesIt_andAnExpiredHoldCannotBeConfirmed() throws Exception {
    String a = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    String body =
        "{\"roomId\":\""
            + small.getId()
            + "\",\"startsAt\":\""
            + s
            + "\",\"endsAt\":\""
            + s.plus(Duration.ofHours(1))
            + "\"}";
    String holdId =
        json.readTree(
                mvc.perform(auth(post("/api/holds"), a).content(body))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();
    mvc.perform(
            auth(post("/api/bookings"), a)
                .content(
                    bookingJson(
                        small, s, s.plus(Duration.ofHours(1)), "\"holdId\":\"" + holdId + "\"")))
        .andExpect(status().isCreated());
    assertThat(jdbc.queryForObject("select count(*) from slot_holds", Integer.class)).isZero();

    Instant s2 = s.plus(Duration.ofHours(3));
    String body2 =
        "{\"roomId\":\""
            + small.getId()
            + "\",\"startsAt\":\""
            + s2
            + "\",\"endsAt\":\""
            + s2.plus(Duration.ofHours(1))
            + "\"}";
    String holdId2 =
        json.readTree(
                mvc.perform(auth(post("/api/holds"), a).content(body2))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asText();
    jdbc.update("update slot_holds set expires_at = now() - interval '1 second'");
    mvc.perform(
            auth(post("/api/bookings"), a)
                .content(
                    bookingJson(
                        small, s2, s2.plus(Duration.ofHours(1)), "\"holdId\":\"" + holdId2 + "\"")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Hold expired"));
  }

  private Booking past(Instant start, java.util.UUID organizer) {
    return past(checkIn, start, organizer);
  }

  private Booking past(com.rooms.room.Room room, Instant start, java.util.UUID organizer) {
    Booking b =
        new Booking(
            room.getId(), organizer, "Standup", start, start.plus(Duration.ofHours(1)), null);
    return bookings.saveAndFlush(b);
  }

  @Test
  void unattendedBookingIsReleasedAfterTenMinutesAndOrganizerIsEmailed() throws Exception {
    var u = user("a@example.com", Role.EMPLOYEE);
    Booking late = past(Instant.now().minus(Duration.ofMinutes(12)), u.getId());
    com.rooms.room.Room other = new com.rooms.room.Room("Sabarmati", 1, 6, none());
    other.setCheckInRequired(true);
    other = rooms.save(other);
    Booking fresh = past(other, Instant.now().minus(Duration.ofMinutes(5)), u.getId());
    // a second room that does not require check in is never released
    Booking noCheckIn =
        bookings.saveAndFlush(
            new Booking(
                small.getId(),
                u.getId(),
                "Chat",
                Instant.now().minus(Duration.ofMinutes(30)),
                Instant.now().plus(Duration.ofMinutes(30)),
                null));
    assertThat(service.releaseUnattended()).isEqualTo(1);
    assertThat(bookings.findById(late.getId()).orElseThrow().getStatus())
        .isEqualTo(BookingStatus.RELEASED);
    assertThat(bookings.findById(fresh.getId()).orElseThrow().getStatus())
        .isEqualTo(BookingStatus.CONFIRMED);
    assertThat(bookings.findById(noCheckIn.getId()).orElseThrow().getStatus())
        .isEqualTo(BookingStatus.CONFIRMED);
    assertThat(jdbc.queryForList("select type from email_outbox", String.class))
        .containsExactly("BOOKING_RELEASED");
  }

  @Test
  void checkInPreventsRelease() throws Exception {
    var u = user("a@example.com", Role.EMPLOYEE);
    String t = token("a@example.com", "password-123");
    Booking b = past(Instant.now().minus(Duration.ofMinutes(8)), u.getId());
    mvc.perform(auth(post("/api/bookings/" + b.getId() + "/check-in"), t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.checkedInAt").isNotEmpty());
    jdbc.update(
        "update bookings set starts_at = starts_at - interval '20 minutes', ends_at = ends_at");
    assertThat(service.releaseUnattended()).isZero();
  }

  @Test
  void onlyTheOrganizerCanCheckIn() throws Exception {
    var u = user("a@example.com", Role.EMPLOYEE);
    String other = login("b@example.com", Role.EMPLOYEE);
    Booking b = past(Instant.now().minus(Duration.ofMinutes(2)), u.getId());
    mvc.perform(auth(post("/api/bookings/" + b.getId() + "/check-in"), other))
        .andExpect(status().isForbidden());
  }

  @Test
  void remindersAreQueuedOncePerRecipientForBookingsStartingSoon() throws Exception {
    var u = user("a@example.com", Role.EMPLOYEE);
    Booking soon =
        bookings.saveAndFlush(
            new Booking(
                small.getId(),
                u.getId(),
                "Soon",
                Instant.now().plus(Duration.ofMinutes(10)),
                Instant.now().plus(Duration.ofMinutes(70)),
                null));
    Booking later =
        bookings.saveAndFlush(
            new Booking(
                big.getId(),
                u.getId(),
                "Later",
                Instant.now().plus(Duration.ofHours(3)),
                Instant.now().plus(Duration.ofHours(4)),
                null));
    assertThat(service.enqueueReminders()).isEqualTo(1);
    assertThat(service.enqueueReminders()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from email_outbox where type = 'BOOKING_REMINDER' and booking_id = ?",
                Integer.class,
                soon.getId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from email_outbox where booking_id = ?",
                Integer.class,
                later.getId()))
        .isZero();
  }

  @Test
  void adminUtilizationAndBookingsList() throws Exception {
    String admin = login("c@example.com", Role.ADMIN);
    String t = login("a@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    post201(t, bookingJson(small, s, s.plus(Duration.ofHours(1)), null));
    mvc.perform(auth(get("/api/admin/bookings"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(auth(get("/api/admin/utilization"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));
  }
}
