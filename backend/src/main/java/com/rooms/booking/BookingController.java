package com.rooms.booking;

import com.rooms.auth.AuthUser;
import com.rooms.booking.BookingDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BookingController {
  private final BookingService service;

  public BookingController(BookingService service) {
    this.service = service;
  }

  @PostMapping("/holds")
  @ResponseStatus(HttpStatus.CREATED)
  public HoldView hold(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody HoldRequest r) {
    return service.createHold(u, r);
  }

  @DeleteMapping("/holds/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void releaseHold(@AuthenticationPrincipal AuthUser u, @PathVariable UUID id) {
    service.deleteHold(u, id);
  }

  @PostMapping("/bookings")
  @ResponseStatus(HttpStatus.CREATED)
  public List<BookingView> create(
      @AuthenticationPrincipal AuthUser u, @Valid @RequestBody CreateBookingRequest r) {
    return service.create(u, r);
  }

  @GetMapping("/bookings/mine")
  public List<BookingView> mine(
      @AuthenticationPrincipal AuthUser u, @RequestParam(required = false) String when) {
    return service.mine(u, when);
  }

  @GetMapping("/bookings/{id}")
  public BookingView get(@AuthenticationPrincipal AuthUser u, @PathVariable UUID id) {
    return service.get(u, id);
  }

  @PatchMapping("/bookings/{id}")
  public BookingView update(
      @AuthenticationPrincipal AuthUser u,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateBookingRequest r) {
    return service.update(u, id, r);
  }

  @DeleteMapping("/bookings/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancel(
      @AuthenticationPrincipal AuthUser u,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "THIS") Scope scope) {
    service.cancel(u, id, scope);
  }

  @PostMapping("/bookings/{id}/check-in")
  public BookingView checkIn(@AuthenticationPrincipal AuthUser u, @PathVariable UUID id) {
    return service.checkIn(u, id);
  }
}
