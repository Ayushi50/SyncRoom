package com.rooms.availability;

import com.rooms.booking.Booking;
import com.rooms.booking.BookingRepository;
import com.rooms.booking.BookingRules;
import com.rooms.booking.SlotHold;
import com.rooms.booking.SlotHoldRepository;
import com.rooms.common.OfficeTime;
import com.rooms.room.Amenity;
import com.rooms.room.Room;
import com.rooms.room.RoomDtos.RoomView;
import com.rooms.room.RoomRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {
  public record Segment(Instant start, Instant end) {}

  public record RoomAvailability(RoomView room, List<Segment> busy) {}

  public record Suggestion(RoomView room, Instant startsAt, Instant endsAt) {}

  public record SearchResult(List<RoomAvailability> rooms, List<Suggestion> nearby) {}

  private final RoomRepository rooms;
  private final BookingRepository bookings;
  private final SlotHoldRepository holds;
  private final Clock clock;

  public AvailabilityService(
      RoomRepository rooms, BookingRepository bookings, SlotHoldRepository holds, Clock clock) {
    this.rooms = rooms;
    this.bookings = bookings;
    this.holds = holds;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public SearchResult search(
      LocalDate date,
      LocalTime start,
      LocalTime end,
      int people,
      Set<Amenity> amenities,
      UUID userId) {
    Instant from = date.atTime(start).atZone(OfficeTime.ZONE).toInstant();
    Instant to = date.atTime(end).atZone(OfficeTime.ZONE).toInstant();
    BookingRules.validateWindow(from, to, clock.instant());

    List<Room> candidates = candidates(people, amenities);
    Map<UUID, List<Segment>> busy = busyByRoom(candidates, date, userId);

    List<RoomAvailability> free = new ArrayList<>();
    for (Room r : candidates) {
      if (!insideHours(r, from, to)) continue;
      List<Segment> segs = busy.getOrDefault(r.getId(), List.of());
      if (segs.stream().noneMatch(s -> overlaps(s, from, to))) {
        free.add(new RoomAvailability(RoomView.of(r), segs));
      }
    }
    List<Suggestion> nearby = List.of();
    if (free.isEmpty()) {
      nearby = suggest(candidates, busy, date, from, Duration.between(from, to), 3);
    }
    return new SearchResult(free, nearby);
  }

  /** Next free slots of the same length, used when a booking conflicts. */
  @Transactional(readOnly = true)
  public List<Suggestion> alternatives(
      Instant from, Instant to, int people, UUID userId, int limit) {
    LocalDate date = from.atZone(OfficeTime.ZONE).toLocalDate();
    List<Room> candidates = candidates(people, Set.of());
    Map<UUID, List<Segment>> busy = busyByRoom(candidates, date, userId);
    return suggest(candidates, busy, date, from, Duration.between(from, to), limit);
  }

  private List<Room> candidates(int people, Set<Amenity> amenities) {
    return rooms.findByActiveTrueOrderByNameAsc().stream()
        .filter(r -> r.getCapacity() >= people)
        .filter(r -> r.getAmenities().containsAll(amenities))
        .sorted(Comparator.comparingInt(Room::getCapacity).thenComparing(Room::getName))
        .toList();
  }

  private Map<UUID, List<Segment>> busyByRoom(List<Room> candidates, LocalDate date, UUID userId) {
    if (candidates.isEmpty()) return Map.of();
    Instant dayStart = date.atStartOfDay(OfficeTime.ZONE).toInstant();
    Instant dayEnd = date.plusDays(1).atStartOfDay(OfficeTime.ZONE).toInstant();
    List<UUID> ids = candidates.stream().map(Room::getId).toList();
    Map<UUID, List<Segment>> out =
        bookings.findConfirmedOverlapping(ids, dayStart, dayEnd).stream()
            .collect(
                Collectors.groupingBy(
                    Booking::getRoomId,
                    Collectors.mapping(
                        b -> new Segment(b.getStartsAt(), b.getEndsAt()), Collectors.toList())));
    Map<UUID, List<Segment>> merged = new java.util.HashMap<>();
    out.forEach((k, v) -> merged.put(k, new ArrayList<>(v)));
    for (SlotHold h : holds.findActiveOverlapping(ids, clock.instant(), dayStart, dayEnd)) {
      if (h.getUserId().equals(userId)) continue;
      merged
          .computeIfAbsent(h.getRoomId(), k -> new ArrayList<>())
          .add(new Segment(h.getStartsAt(), h.getEndsAt()));
    }
    merged.values().forEach(l -> l.sort(Comparator.comparing(Segment::start)));
    return merged;
  }

  private List<Suggestion> suggest(
      List<Room> candidates,
      Map<UUID, List<Segment>> busy,
      LocalDate date,
      Instant notBefore,
      Duration len,
      int limit) {
    Instant now = clock.instant();
    List<Suggestion> out = new ArrayList<>();
    for (Room r : candidates) {
      Instant slot =
          earliest(r, busy.getOrDefault(r.getId(), List.of()), date, notBefore, len, now);
      if (slot == null)
        slot = earliest(r, busy.getOrDefault(r.getId(), List.of()), date, Instant.MIN, len, now);
      if (slot != null) out.add(new Suggestion(RoomView.of(r), slot, slot.plus(len)));
    }
    out.sort(Comparator.comparing(Suggestion::startsAt).thenComparingInt(s -> s.room().capacity()));
    return out.size() > limit ? out.subList(0, limit) : out;
  }

  private static Instant earliest(
      Room r, List<Segment> busy, LocalDate date, Instant notBefore, Duration len, Instant now) {
    ZonedDateTime open = date.atTime(r.getOpenTime()).atZone(OfficeTime.ZONE);
    ZonedDateTime close = date.atTime(r.getCloseTime()).atZone(OfficeTime.ZONE);
    Instant cursor = open.toInstant();
    Instant floor = notBefore.isAfter(now) ? notBefore : now;
    if (cursor.isBefore(floor)) cursor = ceil15(floor);
    while (!cursor.plus(len).isAfter(close.toInstant())) {
      final Instant s = cursor;
      final Instant e = cursor.plus(len);
      if (busy.stream().noneMatch(seg -> overlaps(seg, s, e))) return s;
      cursor = cursor.plus(Duration.ofMinutes(15));
    }
    return null;
  }

  private static Instant ceil15(Instant t) {
    long step = 900;
    long sec = t.getEpochSecond();
    long rounded = ((sec + step - 1) / step) * step;
    return Instant.ofEpochSecond(rounded);
  }

  private static boolean insideHours(Room r, Instant from, Instant to) {
    ZonedDateTime s = from.atZone(OfficeTime.ZONE);
    ZonedDateTime e = to.atZone(OfficeTime.ZONE);
    return !s.toLocalTime().isBefore(r.getOpenTime())
        && !e.toLocalTime().isAfter(r.getCloseTime())
        && s.toLocalDate().equals(e.toLocalDate());
  }

  private static boolean overlaps(Segment s, Instant from, Instant to) {
    return s.start().isBefore(to) && s.end().isAfter(from);
  }

  public static <T> Map<UUID, T> byId(List<Room> list, Function<Room, T> f) {
    return list.stream().collect(Collectors.toMap(Room::getId, f));
  }
}
