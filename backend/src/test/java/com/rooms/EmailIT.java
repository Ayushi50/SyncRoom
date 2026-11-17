package com.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rooms.auth.Role;
import com.rooms.mail.*;
import com.rooms.support.AbstractIT;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EmailIT extends AbstractIT {
  @Autowired OutboxWorker worker;
  @Autowired EmailOutboxRepository outbox;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;

  private String bookWithInvitees(String token, Instant s) throws Exception {
    String extra = "\"attendees\":[{\"email\":\"x@example.com\",\"name\":\"Xavier\"},{\"email\":\"y@example.com\"}]";
    return mvc.perform(auth(post("/api/bookings"), token).content(bookingJson(big, s, s.plus(Duration.ofHours(1)), extra)))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void bookingSendsExactlyOneEmailPerRecipient() throws Exception {
    String t = login("org@example.com", Role.EMPLOYEE);
    bookWithInvitees(t, slot(3, 10, 0));
    assertThat(outbox.count()).isEqualTo(3);
    worker.processBatch();
    worker.processBatch();
    MimeMessage[] msgs = mail.getReceivedMessages();
    assertThat(msgs).hasSize(3);
    List<String> to = java.util.Arrays.stream(msgs).map(m -> {
      try { return m.getAllRecipients()[0].toString(); } catch (Exception e) { throw new IllegalStateException(e); }
    }).sorted().toList();
    assertThat(to).containsExactly("org@example.com", "x@example.com", "y@example.com");
    assertThat(msgs[0].getSubject()).startsWith("Booked: Planning, Narmada, ");
  }

  @Test
  void messageHasHtmlTextAndCalendarInvite() throws Exception {
    String t = login("org@example.com", Role.EMPLOYEE);
    bookWithInvitees(t, slot(3, 10, 0));
    worker.processBatch();
    String raw = com.icegreen.greenmail.util.GreenMailUtil.getWholeMessage(mail.getReceivedMessages()[0]);
    assertThat(raw).contains("text/html").contains("text/plain").contains("invite.ics").contains("method=REQUEST");
    assertThat(raw).contains("View booking");
  }

  @Test
  void bookingSucceedsWhileSmtpIsDownAndEmailArrivesLater() throws Exception {
    String t = login("org@example.com", Role.EMPLOYEE);
    mail.stop();
    bookWithInvitees(t, slot(3, 10, 0));
    worker.processBatch();
    var rows = outbox.findAll();
    assertThat(rows).hasSize(3).allSatisfy(r -> {
      assertThat(r.getStatus()).isEqualTo(OutboxStatus.PENDING);
      assertThat(r.getAttempts()).isEqualTo(1);
      assertThat(r.getNextAttemptAt()).isAfter(Instant.now().plus(Duration.ofSeconds(30)));
    });
    mail.start();
    jdbc.update("update email_outbox set next_attempt_at = now()");
    worker.processBatch();
    assertThat(mail.getReceivedMessages()).hasSize(3);
    assertThat(outbox.findAll()).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(OutboxStatus.SENT));
  }

  @Test
  void retriesNeverDuplicateAnEmail() throws Exception {
    String t = login("org@example.com", Role.EMPLOYEE);
    mail.stop();
    String body = bookWithInvitees(t, slot(3, 10, 0));
    worker.processBatch();
    mail.start();
    jdbc.update("update email_outbox set next_attempt_at = now()");
    worker.processBatch();
    worker.processBatch();
    worker.processBatch();
    assertThat(mail.getReceivedMessages()).hasSize(3);
    // re enqueueing the same key is a no op
    var row = outbox.findAll().get(0);
    int inserted =
        tx.execute(st -> outbox.insertIgnore(UUID.randomUUID(), row.getType().name(), row.getBookingId(), row.getBookingVersion(), row.getRecipientEmail(), "{}"));
    assertThat(inserted).isZero();
    assertThat(body).isNotBlank();
  }

  @Test
  void rowBecomesFailedAfterFiveAttempts() throws Exception {
    String t = login("org@example.com", Role.EMPLOYEE);
    mail.stop();
    bookWithInvitees(t, slot(3, 10, 0));
    for (int i = 0; i < 5; i++) {
      worker.processBatch();
      jdbc.update("update email_outbox set next_attempt_at = now() where status = 'PENDING'");
    }
    assertThat(outbox.findAll()).allSatisfy(r -> {
      assertThat(r.getStatus()).isEqualTo(OutboxStatus.FAILED);
      assertThat(r.getAttempts()).isEqualTo(5);
    });
    assertThat(OutboxWorker.backoff(1)).isEqualTo(Duration.ofMinutes(1));
    assertThat(OutboxWorker.backoff(2)).isEqualTo(Duration.ofMinutes(5));
    assertThat(OutboxWorker.backoff(3)).isEqualTo(Duration.ofMinutes(30));
    assertThat(OutboxWorker.backoff(4)).isEqualTo(Duration.ofHours(2));
  }

  @Test
  void rescheduleAndCancelEachSendTheirOwnEmail() throws Exception {
    String t = login("org@example.com", Role.EMPLOYEE);
    Instant s = slot(3, 10, 0);
    String resp = bookWithInvitees(t, s);
    String id = json.readTree(resp).get(0).get("id").asText();
    long version = json.readTree(resp).get(0).get("version").asLong();
    Instant moved = s.plus(Duration.ofHours(2));
    mvc.perform(auth(patch("/api/bookings/" + id), t).content("{\"startsAt\":\"" + moved + "\",\"endsAt\":\"" + moved.plus(Duration.ofHours(1)) + "\",\"version\":" + version + "}")).andExpect(status().isOk());
    mvc.perform(auth(delete("/api/bookings/" + id), t)).andExpect(status().isNoContent());
    assertThat(jdbc.queryForList("select distinct type from email_outbox order by type", String.class))
        .containsExactly("BOOKING_CANCELLED", "BOOKING_CONFIRMED", "BOOKING_RESCHEDULED");
    worker.processBatch();
    worker.processBatch();
    assertThat(mail.getReceivedMessages()).hasSize(9);
  }

  // ---- rendering and ics ----

  private MailPayload payload(boolean autoRelease) {
    Instant s = Instant.parse("2026-11-18T04:30:00Z");
    return new MailPayload(
        UUID.fromString("11111111-1111-1111-1111-111111111111"), 2, "Sprint planning", "Narmada", 1, 12,
        List.of("PROJECTOR", "VIDEO_CALL"), s.toString(), s.plus(Duration.ofHours(1)).toString(),
        "Asha Rao", "asha@example.com",
        List.of(new MailPayload.Person("Ravi", "ravi@example.com")), autoRelease, "Ravi", "Asia/Kolkata", "http://localhost:5173");
  }

  @Autowired MailRenderer renderer;

  @Test
  void everyEventRendersHtmlAndPlainText() {
    for (EmailType type : EmailType.values()) {
      var r = renderer.render(type, payload(true));
      assertThat(r.subject()).startsWith(type.subjectPrefix() + ": Sprint planning, Narmada, Wed 18 Nov, 10:00 to 11:00 AM IST");
      assertThat(r.html()).contains("Sprint planning").contains("Narmada").contains("#0f766e").contains("View booking").contains("max-width:560px");
      assertThat(r.text()).contains("Sprint planning").contains("Room: Narmada, floor 1").contains("View booking: http://localhost:5173/bookings/");
      assertThat(r.subject()).doesNotContain(" - ");
    }
  }

  @Test
  void autoReleaseLineAppearsOnlyWhenItApplies() {
    assertThat(renderer.render(EmailType.BOOKING_CONFIRMED, payload(true)).html()).contains("released if nobody checks in");
    assertThat(renderer.render(EmailType.BOOKING_CONFIRMED, payload(false)).html()).doesNotContain("released if nobody checks in");
    assertThat(renderer.render(EmailType.BOOKING_CANCELLED, payload(true)).html()).doesNotContain("released if nobody checks in");
  }

  @Test
  void timesAreShownInTheRecipientsTimeZone() {
    MailPayload p = payload(false);
    MailPayload utc = new MailPayload(p.bookingId(), p.sequence(), p.title(), p.roomName(), p.floor(), p.capacity(), p.equipment(), p.startsAt(), p.endsAt(), p.organizerName(), p.organizerEmail(), p.invitees(), false, "Ravi", "UTC", p.baseUrl());
    assertThat(renderer.render(EmailType.BOOKING_CONFIRMED, utc).subject()).contains("Wed 18 Nov, 4:30 to 5:30 AM UTC");
  }

  @Test
  void icsRequestFollowsRfc5545() {
    String ics = IcsBuilder.build(EmailType.BOOKING_CONFIRMED, payload(false), "rooms@rooms.local");
    assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
    assertThat(ics).contains("METHOD:REQUEST\r\n").contains("UID:11111111-1111-1111-1111-111111111111@rooms\r\n")
        .contains("SEQUENCE:2\r\n").contains("DTSTART:20261118T043000Z\r\n").contains("DTEND:20261118T053000Z\r\n")
        .contains("STATUS:CONFIRMED").contains("ATTENDEE;CN=\"Ravi\";RSVP=TRUE:mailto:ravi@example.com");
    assertThat(ics.split("\r\n")).allSatisfy(l -> assertThat(l.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75));
  }

  @Test
  void icsCancelUsesMethodCancelAndSameUid() {
    String ics = IcsBuilder.build(EmailType.BOOKING_CANCELLED, payload(false), "rooms@rooms.local");
    assertThat(ics).contains("METHOD:CANCEL\r\n").contains("STATUS:CANCELLED").contains("UID:11111111-1111-1111-1111-111111111111@rooms");
  }

  @Test
  void icsEscapesSpecialCharacters() {
    MailPayload p = payload(false);
    MailPayload tricky = new MailPayload(p.bookingId(), 0, "Plan; scope, budget", p.roomName(), 1, 12, p.equipment(), p.startsAt(), p.endsAt(), p.organizerName(), p.organizerEmail(), List.of(), false, "R", "UTC", p.baseUrl());
    assertThat(IcsBuilder.build(EmailType.BOOKING_CONFIRMED, tricky, "x@y.z")).contains("SUMMARY:Plan\\; scope\\, budget");
  }
}
