package com.rooms.booking;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot_holds")
public class SlotHold {
  @Id private UUID id;
  private UUID roomId;
  private UUID userId;
  private Instant startsAt;
  private Instant endsAt;
  private Instant expiresAt;

  protected SlotHold() {}

  public SlotHold(UUID roomId, UUID userId, Instant startsAt, Instant endsAt, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.roomId = roomId;
    this.userId = userId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.expiresAt = expiresAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRoomId() {
    return roomId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
