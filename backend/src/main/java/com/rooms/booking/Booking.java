package com.rooms.booking;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings")
public class Booking {
  @Id private UUID id;
  private UUID roomId;
  private UUID organizerId;
  private String title;
  private Instant startsAt;
  private Instant endsAt;

  @Enumerated(EnumType.STRING)
  private BookingStatus status = BookingStatus.CONFIRMED;

  private UUID seriesId;

  @Version private long version;

  private Instant checkedInAt;
  private Instant createdAt = Instant.now();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
  @JoinColumn(name = "booking_id", nullable = false)
  private List<BookingAttendee> attendees = new ArrayList<>();

  protected Booking() {}

  public Booking(
      UUID roomId,
      UUID organizerId,
      String title,
      Instant startsAt,
      Instant endsAt,
      UUID seriesId) {
    this.id = UUID.randomUUID();
    this.roomId = roomId;
    this.organizerId = organizerId;
    this.title = title;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.seriesId = seriesId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRoomId() {
    return roomId;
  }

  public UUID getOrganizerId() {
    return organizerId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public void reschedule(Instant start, Instant end) {
    this.startsAt = start;
    this.endsAt = end;
  }

  public BookingStatus getStatus() {
    return status;
  }

  public void setStatus(BookingStatus status) {
    this.status = status;
  }

  public UUID getSeriesId() {
    return seriesId;
  }

  public long getVersion() {
    return version;
  }

  public Instant getCheckedInAt() {
    return checkedInAt;
  }

  public void setCheckedInAt(Instant t) {
    this.checkedInAt = t;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<BookingAttendee> getAttendees() {
    return attendees;
  }
}
