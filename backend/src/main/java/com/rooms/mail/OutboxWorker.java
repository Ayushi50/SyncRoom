package com.rooms.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rooms.common.RoomsProperties;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class OutboxWorker {
  public static final int MAX_ATTEMPTS = 5;
  private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
  private static final List<Duration> BACKOFF =
      List.of(
          Duration.ofMinutes(1),
          Duration.ofMinutes(5),
          Duration.ofMinutes(30),
          Duration.ofHours(2));

  private final EmailOutboxRepository repo;
  private final JavaMailSender sender;
  private final MailRenderer renderer;
  private final ObjectMapper mapper;
  private final RoomsProperties props;
  private final TransactionTemplate tx;
  private final Clock clock;

  public OutboxWorker(
      EmailOutboxRepository repo,
      JavaMailSender sender,
      MailRenderer renderer,
      ObjectMapper mapper,
      RoomsProperties props,
      TransactionTemplate tx,
      Clock clock) {
    this.repo = repo;
    this.sender = sender;
    this.renderer = renderer;
    this.mapper = mapper;
    this.props = props;
    this.tx = tx;
    this.clock = clock;
  }

  public static Duration backoff(int attempts) {
    return BACKOFF.get(Math.min(attempts, BACKOFF.size()) - 1);
  }

  @Scheduled(fixedDelay = 5000, initialDelay = 5000)
  public void tick() {
    try {
      processBatch();
    } catch (Exception e) {
      log.warn("Outbox batch failed: {}", e.getClass().getSimpleName());
    }
  }

  /** Processes one batch of due rows and returns how many were handled. */
  public int processBatch() {
    Integer n =
        tx.execute(
            status -> {
              List<EmailOutbox> due = repo.lockDue();
              for (EmailOutbox row : due) {
                try {
                  send(row);
                  row.markSent(clock.instant());
                } catch (Exception e) {
                  row.markFailedAttempt(clock.instant(), e.getClass().getSimpleName());
                  log.warn(
                      "Email {} attempt {} failed with {}",
                      row.getId(),
                      row.getAttempts(),
                      e.getClass().getSimpleName());
                }
              }
              repo.saveAll(due);
              return due.size();
            });
    return n == null ? 0 : n;
  }

  private void send(EmailOutbox row) throws Exception {
    MailPayload p = mapper.readValue(row.getPayload(), MailPayload.class);
    MailRenderer.Rendered r = renderer.render(row.getType(), p);
    MimeMessage msg = sender.createMimeMessage();
    MimeMessageHelper h = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
    h.setFrom(props.mailFrom());
    h.setTo(row.getRecipientEmail());
    h.setSubject(r.subject());
    h.setText(r.text(), r.html());
    if (row.getType() != EmailType.BOOKING_REMINDER
        && row.getType() != EmailType.BOOKING_RELEASED) {
      String ics = IcsBuilder.build(row.getType(), p, props.mailFrom());
      h.addAttachment(
          "invite.ics",
          new ByteArrayResource(ics.getBytes(StandardCharsets.UTF_8)),
          "text/calendar; method=" + IcsBuilder.method(row.getType()) + "; charset=UTF-8");
    }
    sender.send(msg);
  }
}
