package com.rooms.mail;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Builds RFC 5545 calendar text. Times are UTC and lines end with CRLF. */
public final class IcsBuilder {
  private static final DateTimeFormatter UTC =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

  private IcsBuilder() {}

  public static String method(EmailType type) {
    return type == EmailType.BOOKING_CANCELLED ? "CANCEL" : "REQUEST";
  }

  public static String build(EmailType type, MailPayload p, String fromAddress) {
    String method = method(type);
    StringBuilder sb = new StringBuilder();
    line(sb, "BEGIN:VCALENDAR");
    line(sb, "VERSION:2.0");
    line(sb, "PRODID:-//Rooms//Booking//EN");
    line(sb, "CALSCALE:GREGORIAN");
    line(sb, "METHOD:" + method);
    line(sb, "BEGIN:VEVENT");
    line(sb, "UID:" + p.bookingId() + "@rooms");
    line(sb, "SEQUENCE:" + p.sequence());
    line(sb, "DTSTAMP:" + UTC.format(Instant.now()));
    line(sb, "DTSTART:" + UTC.format(Instant.parse(p.startsAt())));
    line(sb, "DTEND:" + UTC.format(Instant.parse(p.endsAt())));
    line(sb, "SUMMARY:" + escape(p.title()));
    line(sb, "LOCATION:" + escape(p.roomName() + ", floor " + p.floor()));
    line(sb, "STATUS:" + (method.equals("CANCEL") ? "CANCELLED" : "CONFIRMED"));
    line(sb, "ORGANIZER;CN=" + escapeParam(p.organizerName()) + ":mailto:" + p.organizerEmail());
    for (MailPayload.Person a : p.invitees()) {
      line(
          sb,
          "ATTENDEE;CN="
              + escapeParam(a.name() == null || a.name().isBlank() ? a.email() : a.name())
              + ";RSVP=TRUE:mailto:"
              + a.email());
    }
    line(sb, "END:VEVENT");
    line(sb, "END:VCALENDAR");
    return sb.toString();
  }

  private static void line(StringBuilder sb, String content) {
    // Fold at 75 octets as RFC 5545 section 3.1 requires.
    byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    if (bytes.length <= 75) {
      sb.append(content).append("\r\n");
      return;
    }
    StringBuilder cur = new StringBuilder();
    int octets = 0;
    boolean first = true;
    for (int i = 0; i < content.length(); ) {
      int cp = content.codePointAt(i);
      String ch = new String(Character.toChars(cp));
      int len = ch.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
      int limit = first ? 75 : 74;
      if (octets + len > limit) {
        sb.append(first ? "" : " ").append(cur).append("\r\n");
        cur.setLength(0);
        octets = 0;
        first = false;
      }
      cur.append(ch);
      octets += len;
      i += Character.charCount(cp);
    }
    sb.append(first ? "" : " ").append(cur).append("\r\n");
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r", "")
        .replace("\n", "\\n");
  }

  private static String escapeParam(String s) {
    return "\"" + s.replace("\"", "'") + "\"";
  }
}
