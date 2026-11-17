package com.rooms.mail;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_outbox")
public class EmailOutbox {
  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private EmailType type;

  private UUID bookingId;
  private long bookingVersion;
  private String recipientEmail;
  private String payload;

  @Enumerated(EnumType.STRING)
  private OutboxStatus status = OutboxStatus.PENDING;

  private int attempts;
  private Instant nextAttemptAt;
  private String lastError;
  private Instant createdAt = Instant.now();
  private Instant sentAt;

  protected EmailOutbox() {}

  public UUID getId() {
    return id;
  }

  public EmailType getType() {
    return type;
  }

  public UUID getBookingId() {
    return bookingId;
  }

  public long getBookingVersion() {
    return bookingVersion;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getPayload() {
    return payload;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public void markSent(Instant now) {
    this.status = OutboxStatus.SENT;
    this.sentAt = now;
    this.attempts++;
    this.lastError = null;
  }

  public void markFailedAttempt(Instant now, String error) {
    this.attempts++;
    this.lastError = error;
    if (attempts >= OutboxWorker.MAX_ATTEMPTS) {
      this.status = OutboxStatus.FAILED;
    } else {
      this.nextAttemptAt = now.plus(OutboxWorker.backoff(attempts));
    }
  }
}
