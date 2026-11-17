package com.rooms.admin;

import com.rooms.booking.Booking;
import com.rooms.booking.BookingDtos.BookingView;
import com.rooms.booking.BookingRepository;
import com.rooms.booking.BookingService;
import com.rooms.booking.BookingStatus;
import com.rooms.room.Room;
import com.rooms.room.RoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  public record PageView<T>(List<T> items, int page, int size, long total) {}

  public record Utilization(UUID roomId, String roomName, double bookedHours, double openHours, double percent) {}

  private final BookingRepository bookings;
  private final BookingService service;
  private final RoomRepository rooms;
  private final Clock clock;

  public AdminController(
      BookingRepository bookings, BookingService service, RoomRepository rooms, Clock clock) {
    this.bookings = bookings;
    this.service = service;
    this.rooms = rooms;
    this.clock = clock;
  }

  @GetMapping("/bookings")
  @Transactional(readOnly = true)
  public PageView<BookingView> bookings(
      @RequestParam(required = false) BookingStatus status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    Page<Booking> p = bookings.adminList(status, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    return new PageView<>(service.views(p.getContent()), p.getNumber(), p.getSize(), p.getTotalElements());
  }

  @GetMapping("/utilization")
  @Transactional(readOnly = true)
  public List<Utilization> utilization() {
    Instant now = clock.instant();
    Instant from = now.minus(Duration.ofDays(30));
    Map<UUID, Double> booked = new HashMap<>();
    for (Booking b : bookings.confirmedBetween(from, now)) {
      Instant s = b.getStartsAt().isBefore(from) ? from : b.getStartsAt();
      Instant e = b.getEndsAt().isAfter(now) ? now : b.getEndsAt();
      booked.merge(b.getRoomId(), Duration.between(s, e).toMinutes() / 60.0, Double::sum);
    }
    long workDays = 0;
    LocalDate d = LocalDate.ofInstant(from, com.rooms.common.OfficeTime.ZONE);
    LocalDate end = LocalDate.ofInstant(now, com.rooms.common.OfficeTime.ZONE);
    for (; !d.isAfter(end); d = d.plusDays(1)) {
      if (d.getDayOfWeek().getValue() < 6) workDays++;
    }
    final long days = workDays;
    return rooms.findByActiveTrueOrderByNameAsc().stream()
        .map(
            r -> {
              double open = days * Duration.between(r.getOpenTime(), r.getCloseTime()).toMinutes() / 60.0;
              double b = booked.getOrDefault(r.getId(), 0.0);
              return new Utilization(
                  r.getId(), r.getName(), round(b), round(open), open == 0 ? 0 : round(b / open * 100));
            })
        .toList();
  }

  private static double round(double v) {
    return Math.round(v * 10) / 10.0;
  }
}
