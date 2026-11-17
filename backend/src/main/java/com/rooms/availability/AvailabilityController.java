package com.rooms.availability;

import com.rooms.auth.AuthUser;
import com.rooms.availability.AvailabilityService.SearchResult;
import com.rooms.room.Amenity;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class AvailabilityController {
  private final AvailabilityService service;

  public AvailabilityController(AvailabilityService service) {
    this.service = service;
  }

  @GetMapping("/available")
  public SearchResult available(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime start,
      @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime end,
      @RequestParam(defaultValue = "1") int people,
      @RequestParam(required = false) Set<Amenity> amenities,
      @AuthenticationPrincipal AuthUser user) {
    return service.search(
        date,
        start,
        end,
        Math.max(1, people),
        amenities == null || amenities.isEmpty() ? EnumSet.noneOf(Amenity.class) : EnumSet.copyOf(amenities),
        user.id());
  }
}
