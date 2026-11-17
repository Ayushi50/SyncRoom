package com.rooms.room;

import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public final class RoomDtos {
  private RoomDtos() {}

  public record RoomRequest(
      @NotBlank @Size(max = 120) String name,
      @Min(0) @Max(200) int floor,
      @Min(1) @Max(500) int capacity,
      Set<Amenity> amenities,
      LocalTime openTime,
      LocalTime closeTime,
      Boolean checkInRequired) {}

  public record RoomView(
      UUID id,
      String name,
      int floor,
      int capacity,
      Set<Amenity> amenities,
      LocalTime openTime,
      LocalTime closeTime,
      boolean checkInRequired,
      boolean active) {
    public static RoomView of(Room r) {
      return new RoomView(
          r.getId(),
          r.getName(),
          r.getFloor(),
          r.getCapacity(),
          r.getAmenities(),
          r.getOpenTime(),
          r.getCloseTime(),
          r.isCheckInRequired(),
          r.isActive());
    }
  }
}
