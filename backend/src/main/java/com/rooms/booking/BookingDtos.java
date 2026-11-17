package com.rooms.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BookingDtos {
  private BookingDtos() {}

  public enum Scope {
    THIS,
    FOLLOWING
  }

  public record AttendeeInput(
      @NotBlank @Email @Size(max = 254) String email, @Size(max = 120) String name) {}

  public record Repeat(@NotNull SeriesPattern pattern, @Min(1) @Max(12) int count) {}

  public record CreateBookingRequest(
      @NotNull UUID roomId,
      @NotBlank @Size(max = 200) String title,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt,
      @Valid @Size(max = 50) List<AttendeeInput> attendees,
      @Min(1) Integer people,
      @Valid Repeat repeat,
      UUID holdId) {}

  public record UpdateBookingRequest(
      Instant startsAt, Instant endsAt, @Size(max = 200) String title, Scope scope, Long version) {}

  public record HoldRequest(
      @NotNull UUID roomId, @NotNull Instant startsAt, @NotNull Instant endsAt) {}

  public record HoldView(
      UUID id, UUID roomId, Instant startsAt, Instant endsAt, Instant expiresAt) {}

  public record Person(String name, String email) {}

  public record BookingView(
      UUID id,
      UUID roomId,
      String roomName,
      int floor,
      String title,
      Instant startsAt,
      Instant endsAt,
      BookingStatus status,
      Person organizer,
      List<Person> attendees,
      UUID seriesId,
      SeriesPattern repeatPattern,
      Integer repeatCount,
      long version,
      Instant checkedInAt,
      boolean checkInRequired) {}
}
