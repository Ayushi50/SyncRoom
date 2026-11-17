package com.rooms.mail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Component
public class MailRenderer {
  public record Rendered(String subject, String html, String text) {}

  private final TemplateEngine html = engine(TemplateMode.HTML, ".html");
  private final TemplateEngine text = engine(TemplateMode.TEXT, ".txt");

  private static TemplateEngine engine(TemplateMode mode, String suffix) {
    ClassLoaderTemplateResolver r = new ClassLoaderTemplateResolver();
    r.setPrefix("templates/mail/");
    r.setSuffix(suffix);
    r.setTemplateMode(mode);
    r.setCharacterEncoding("UTF-8");
    r.setCacheable(true);
    SpringTemplateEngine e = new SpringTemplateEngine();
    e.setTemplateResolver(r);
    return e;
  }

  public Rendered render(EmailType type, MailPayload p) {
    ZoneId zone = ZoneId.of(p.recipientZone());
    ZonedDateTime start = Instant.parse(p.startsAt()).atZone(zone);
    ZonedDateTime end = Instant.parse(p.endsAt()).atZone(zone);
    String day = start.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH));
    boolean sameHalf =
        start
            .format(DateTimeFormatter.ofPattern("a"))
            .equals(end.format(DateTimeFormatter.ofPattern("a")));
    String startPart =
        start.format(DateTimeFormatter.ofPattern(sameHalf ? "h:mm" : "h:mm a", Locale.ENGLISH));
    String endPart = end.format(DateTimeFormatter.ofPattern("h:mm a z", Locale.ENGLISH));
    String when = day + ", " + startPart + " to " + endPart;
    String subject = type.subjectPrefix() + ": " + p.title() + ", " + p.roomName() + ", " + when;

    Context c = new Context(Locale.ENGLISH);
    c.setVariable("p", p);
    c.setVariable("type", type.name());
    c.setVariable("when", when);
    c.setVariable("heading", heading(type));
    c.setVariable("viewUrl", p.baseUrl() + "/bookings/" + p.bookingId());
    c.setVariable(
        "rescheduleUrl", p.baseUrl() + "/bookings/" + p.bookingId() + "?action=reschedule");
    c.setVariable("cancelUrl", p.baseUrl() + "/bookings/" + p.bookingId() + "?action=cancel");
    c.setVariable(
        "equipment", String.join(", ", p.equipment().stream().map(MailRenderer::pretty).toList()));
    return new Rendered(subject, html.process("booking", c), text.process("booking", c));
  }

  static String pretty(String amenity) {
    String s = amenity.replace('_', ' ').toLowerCase(Locale.ENGLISH);
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String heading(EmailType t) {
    return switch (t) {
      case BOOKING_CONFIRMED -> "Your room is booked";
      case BOOKING_RESCHEDULED -> "Your booking has a new time";
      case BOOKING_CANCELLED -> "Your booking was cancelled";
      case BOOKING_REMINDER -> "Your meeting starts in 15 minutes";
      case BOOKING_RELEASED -> "Your room was released";
    };
  }
}
