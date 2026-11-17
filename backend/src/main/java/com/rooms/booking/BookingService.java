package com.rooms.booking;

import com.rooms.auth.AuthUser;
import com.rooms.auth.User;
import com.rooms.auth.UserRepository;
import com.rooms.availability.AvailabilityService;
import com.rooms.booking.BookingDtos.*;
import com.rooms.common.ApiException;
import com.rooms.common.OfficeTime;
import com.rooms.mail.EmailType;
import com.rooms.mail.OutboxService;
import com.rooms.room.Room;
import com.rooms.room.RoomRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {
  static final Duration HOLD_TTL = Duration.ofMinutes(5);
  static final Duration RELEASE_AFTER = Duration.ofMinutes(10);

  private final BookingRepository bookings;
  private final BookingSeriesRepository seriesRepo;
  private final SlotHoldRepository holds;
  private final RoomRepository rooms;
  private final UserRepository users;
  private final OutboxService outbox;
  private final AvailabilityService availability;
  private final TransactionTemplate tx;
  private final Clock clock;

  public BookingService(
      BookingRepository bookings,
      BookingSeriesRepository seriesRepo,
      SlotHoldRepository holds,
      RoomRepository rooms,
      UserRepository users,
      OutboxService outbox,
      AvailabilityService availability,
      TransactionTemplate tx,
      Clock clock) {
    this.bookings = bookings;
    this.seriesRepo = seriesRepo;
    this.holds = holds;
    this.rooms = rooms;
    this.users = users;
    this.outbox = outbox;
    this.availability = availability;
    this.tx = tx;
    this.clock = clock;
  }

  // ---------- holds ----------

  public HoldView createHold(AuthUser u, HoldRequest r) {
    try {
      return tx.execute(
          s -> {
            Room room = lockRoom(r.roomId());
            Instant now = clock.instant();
            BookingRules.validateWindow(r.startsAt(), r.endsAt(), now);
            BookingRules.validateOpenHours(room, r.startsAt(), r.endsAt());
            holds.deleteAll(holds.findByUserId(u.id()));
            holds.flush();
            ensureFree(room, List.of(new Slot(r.startsAt(), r.endsAt())), u.id(), 1);
            SlotHold h =
                holds.save(new SlotHold(room.getId(), u.id(), r.startsAt(), r.endsAt(), now.plus(HOLD_TTL)));
            return new HoldView(h.getId(), h.getRoomId(), h.getStartsAt(), h.getEndsAt(), h.getExpiresAt());
          });
    } catch (DataIntegrityViolationException e) {
      throw overlap(r.startsAt(), r.endsAt(), 1, u.id(), List.of(r.startsAt()));
    }
  }

  public void deleteHold(AuthUser u, UUID id) {
    tx.executeWithoutResult(
        s ->
            holds
                .findById(id)
                .filter(h -> h.getUserId().equals(u.id()))
                .ifPresent(holds::delete));
  }

  public int purgeExpiredHolds() {
    Integer n = tx.execute(s -> holds.deleteExpired(clock.instant()));
    return n == null ? 0 : n;
  }

  // ---------- create ----------

  public List<BookingView> create(AuthUser u, CreateBookingRequest r) {
    int people = people(r);
    List<Slot> slots = expand(r);
    try {
      List<UUID> ids = tx.execute(s -> doCreate(u, r, slots, people));
      return ids.stream().map(id -> view(bookings.findById(id).orElseThrow())).toList();
    } catch (DataIntegrityViolationException e) {
      throw overlap(r.startsAt(), r.endsAt(), people, u.id(), slots.stream().map(Slot::start).toList());
    }
  }

  private List<UUID> doCreate(AuthUser u, CreateBookingRequest r, List<Slot> slots, int people) {
    Room room = lockRoom(r.roomId());
    if (!room.isActive()) throw ApiException.badRequest("This room is no longer available.");
    if (people > room.getCapacity()) {
      throw ApiException.badRequest(
          room.getName() + " seats " + room.getCapacity() + ". Pick a larger room or fewer people.");
    }
    Instant now = clock.instant();
    for (Slot s : slots) {
      BookingRules.validateWindow(s.start(), s.end(), now);
      BookingRules.validateOpenHours(room, s.start(), s.end());
    }
    if (r.holdId() != null) {
      SlotHold h = holds.findById(r.holdId()).orElse(null);
      if (h == null
          || !h.getUserId().equals(u.id())
          || !h.getExpiresAt().isAfter(now)
          || !h.getRoomId().equals(room.getId())
          || !h.getStartsAt().equals(r.startsAt())
          || !h.getEndsAt().equals(r.endsAt())) {
        throw ApiException.conflict(
            "Hold expired",
            "Your hold on this room has expired. Search again to pick a room.",
            Map.of());
      }
    }
    holds.deleteAll(holds.findByUserId(u.id()));
    holds.flush();
    ensureFree(room, slots, u.id(), people);

    UUID seriesId = null;
    if (r.repeat() != null && slots.size() > 1) {
      seriesId = seriesRepo.save(new BookingSeries(r.repeat().pattern(), slots.size())).getId();
    }
    User organizer = users.findById(u.id()).orElseThrow();
    List<UUID> created = new ArrayList<>();
    for (int i = 0; i < slots.size(); i++) {
      Slot s = slots.get(i);
      Booking draft = new Booking(room.getId(), u.id(), r.title().trim(), s.start(), s.end(), seriesId);
      draft.getAttendees().addAll(attendees(r, organizer));
      Booking b = bookings.saveAndFlush(draft);
      created.add(b.getId());
      if (i == 0) outbox.enqueue(EmailType.BOOKING_CONFIRMED, b, room, organizer);
    }
    return created;
  }

  private static List<BookingAttendee> attendees(CreateBookingRequest r, User organizer) {
    Map<String, BookingAttendee> out = new LinkedHashMap<>();
    if (r.attendees() != null) {
      for (AttendeeInput a : r.attendees()) {
        String email = a.email().trim().toLowerCase();
        if (!email.equalsIgnoreCase(organizer.getEmail())) {
          out.putIfAbsent(email, new BookingAttendee(email, a.name() == null ? null : a.name().trim()));
        }
      }
    }
    return new ArrayList<>(out.values());
  }

  private int people(CreateBookingRequest r) {
    int invited = 1;
    if (r.attendees() != null) {
      invited += (int) r.attendees().stream().map(a -> a.email().trim().toLowerCase()).distinct().count();
    }
    return Math.max(invited, r.people() == null ? 1 : r.people());
  }

  record Slot(Instant start, Instant end) {}

  private List<Slot> expand(CreateBookingRequest r) {
    List<Slot> out = new ArrayList<>();
    out.add(new Slot(r.startsAt(), r.endsAt()));
    if (r.repeat() == null || r.repeat().count() <= 1) return out;
    ZonedDateTime s = r.startsAt().atZone(OfficeTime.ZONE);
    ZonedDateTime e = r.endsAt().atZone(OfficeTime.ZONE);
    ZonedDateTime cs = s;
    ZonedDateTime ce = e;
    while (out.size() < r.repeat().count()) {
      switch (r.repeat().pattern()) {
        case DAILY -> {
          cs = cs.plusDays(1);
          ce = ce.plusDays(1);
        }
        case WEEKLY -> {
          cs = cs.plusWeeks(1);
          ce = ce.plusWeeks(1);
        }
        case WEEKDAYS -> {
          do {
            cs = cs.plusDays(1);
            ce = ce.plusDays(1);
          } while (cs.getDayOfWeek() == DayOfWeek.SATURDAY || cs.getDayOfWeek() == DayOfWeek.SUNDAY);
        }
      }
      out.add(new Slot(cs.toInstant(), ce.toInstant()));
    }
    return out;
  }

  /** Throws 409 listing every conflicting date when any slot is taken or held by someone else. */
  private void ensureFree(Room room, List<Slot> slots, UUID userId, int people) {
    ensureFree(room, slots, userId, people, Set.of());
  }

  private void ensureFree(Room room, List<Slot> slots, UUID userId, int people, Set<UUID> ignore) {
    Instant now = clock.instant();
    List<Instant> conflicts = new ArrayList<>();
    for (Slot s : slots) {
      boolean taken =
          bookings.findConfirmedOverlapping(List.of(room.getId()), s.start(), s.end()).stream()
              .anyMatch(b -> !ignore.contains(b.getId()));
      boolean held =
          holds.findActiveOverlapping(List.of(room.getId()), now, s.start(), s.end()).stream()
              .anyMatch(h -> !h.getUserId().equals(userId));
      if (taken || held) conflicts.add(s.start());
    }
    if (!conflicts.isEmpty()) {
      throw overlap(slots.get(0).start(), slots.get(0).end(), people, userId, conflicts);
    }
  }

  private ApiException overlap(Instant start, Instant end, int people, UUID userId, List<Instant> conflicts) {
    Instant from = conflicts.isEmpty() ? start : conflicts.get(0);
    Instant to = from.plus(Duration.between(start, end));
    List<AvailabilityService.Suggestion> alts = List.of();
    try {
      alts = availability.alternatives(from, to, people, userId, 5);
    } catch (RuntimeException ignored) {
      // alternatives are a courtesy; the conflict itself still has to be reported
    }
    Map<String, Object> extra = new HashMap<>();
    extra.put("conflicts", conflicts);
    extra.put("alternatives", alts);
    return ApiException.conflict(
        "Time slot taken",
        conflicts.size() > 1
            ? "Some dates in this series are already booked. Nothing was booked."
            : "This room is already booked for that time. Pick one of the nearby options.",
        extra);
  }

  private Room lockRoom(UUID id) {
    return rooms.lockById(id).orElseThrow(() -> ApiException.notFound("Room"));
  }

  // ---------- read ----------

  public BookingView get(AuthUser u, UUID id) {
    return tx.execute(
        s -> {
          Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking"));
          authorize(u, b);
          return view(b);
        });
  }

  public List<BookingView> mine(AuthUser u, String when) {
    return tx.execute(
        s -> {
          Instant now = clock.instant();
          List<Booking> list =
              switch (when == null ? "upcoming" : when) {
                case "past" -> bookings.pastFor(u.id(), now);
                case "all" -> {
                  List<Booking> all = new ArrayList<>(bookings.upcomingFor(u.id(), now));
                  all.addAll(bookings.pastFor(u.id(), now));
                  yield all;
                }
                default -> bookings.upcomingFor(u.id(), now);
              };
          return views(list);
        });
  }

  // ---------- change ----------

  public BookingView update(AuthUser u, UUID id, UpdateBookingRequest r) {
    try {
      UUID out = tx.execute(s -> doUpdate(u, id, r));
      return view(bookings.findById(out).orElseThrow());
    } catch (DataIntegrityViolationException e) {
      throw ApiException.conflict(
          "Time slot taken", "This room is already booked for that time. Pick another time.", Map.of());
    }
  }

  private UUID doUpdate(AuthUser u, UUID id, UpdateBookingRequest r) {
    Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking"));
    authorize(u, b);
    if (b.getStatus() != BookingStatus.CONFIRMED) {
      throw ApiException.conflict("Not active", "This booking is no longer active.", Map.of());
    }
    if (r.version() != null && r.version() != b.getVersion()) {
      throw ApiException.conflict(
          "Stale update", "This booking was changed by someone else. Reload it and try again.", Map.of());
    }
    Room room = lockRoom(b.getRoomId());
    Instant newStart = r.startsAt() == null ? b.getStartsAt() : r.startsAt();
    Instant newEnd = r.endsAt() == null ? b.getEndsAt() : r.endsAt();
    Duration shiftStart = Duration.between(b.getStartsAt(), newStart);
    Duration length = Duration.between(newStart, newEnd);
    boolean following = r.scope() == Scope.FOLLOWING && b.getSeriesId() != null;
    List<Booking> targets =
        following ? bookings.seriesFrom(b.getSeriesId(), b.getStartsAt()) : List.of(b);

    boolean timeChanged = !shiftStart.isZero() || !length.equals(Duration.between(b.getStartsAt(), b.getEndsAt()));
    Instant now = clock.instant();
    List<Slot> slots = new ArrayList<>();
    if (timeChanged) {
      for (Booking t : targets) {
        Instant s = t.getStartsAt().plus(shiftStart);
        slots.add(new Slot(s, s.plus(length)));
      }
      for (Slot s : slots) {
        BookingRules.validateWindow(s.start(), s.end(), now);
        BookingRules.validateOpenHours(room, s.start(), s.end());
      }
      holds.deleteAll(holds.findByUserId(u.id()));
      holds.flush();
      Set<UUID> ignore = targets.stream().map(Booking::getId).collect(Collectors.toSet());
      ensureFree(room, slots, u.id(), b.getAttendees().size() + 1, ignore);
    }
    User organizer = users.findById(b.getOrganizerId()).orElseThrow();
    for (int i = 0; i < targets.size(); i++) {
      Booking t = targets.get(i);
      if (timeChanged) t.reschedule(slots.get(i).start(), slots.get(i).end());
      if (r.title() != null && !r.title().isBlank()) t.setTitle(r.title().trim());
      t = bookings.saveAndFlush(t);
      if (i == 0) outbox.enqueue(EmailType.BOOKING_RESCHEDULED, t, room, organizer);
    }
    return b.getId();
  }

  public void cancel(AuthUser u, UUID id, Scope scope) {
    tx.executeWithoutResult(
        s -> {
          Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking"));
          authorize(u, b);
          if (b.getStatus() != BookingStatus.CONFIRMED) return;
          Room room = rooms.findById(b.getRoomId()).orElseThrow();
          User organizer = users.findById(b.getOrganizerId()).orElseThrow();
          List<Booking> targets =
              scope == Scope.FOLLOWING && b.getSeriesId() != null
                  ? bookings.seriesFrom(b.getSeriesId(), b.getStartsAt())
                  : List.of(b);
          for (int i = 0; i < targets.size(); i++) {
            Booking t = targets.get(i);
            t.setStatus(BookingStatus.CANCELLED);
            t = bookings.saveAndFlush(t);
            if (i == 0) outbox.enqueue(EmailType.BOOKING_CANCELLED, t, room, organizer);
          }
        });
  }

  public BookingView checkIn(AuthUser u, UUID id) {
    return tx.execute(
        s -> {
          Booking b = bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking"));
          if (!b.getOrganizerId().equals(u.id())) throw ApiException.forbidden("Only the organizer can check in.");
          Instant now = clock.instant();
          if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.conflict("Not active", "This booking is no longer active.", Map.of());
          }
          if (now.isBefore(b.getStartsAt().minus(Duration.ofMinutes(15))) || now.isAfter(b.getEndsAt())) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "Check in closed",
                "Check in opens 15 minutes before the start and closes at the end of the booking.");
          }
          b.setCheckedInAt(now);
          return view(bookings.saveAndFlush(b));
        });
  }

  // ---------- schedulers ----------

  public int releaseUnattended() {
    Integer n =
        tx.execute(
            s -> {
              List<Booking> list = bookings.unattended(clock.instant().minus(RELEASE_AFTER));
              for (Booking b : list) {
                b.setStatus(BookingStatus.RELEASED);
                b = bookings.saveAndFlush(b);
                outbox.enqueue(
                    EmailType.BOOKING_RELEASED,
                    b,
                    rooms.findById(b.getRoomId()).orElseThrow(),
                    users.findById(b.getOrganizerId()).orElseThrow());
              }
              return list.size();
            });
    return n == null ? 0 : n;
  }

  public int enqueueReminders() {
    Integer n =
        tx.execute(
            s -> {
              Instant now = clock.instant();
              List<Booking> list = bookings.startingBetween(now, now.plus(Duration.ofMinutes(15)));
              for (Booking b : list) {
                outbox.enqueue(
                    EmailType.BOOKING_REMINDER,
                    b,
                    rooms.findById(b.getRoomId()).orElseThrow(),
                    users.findById(b.getOrganizerId()).orElseThrow());
              }
              return list.size();
            });
    return n == null ? 0 : n;
  }

  // ---------- helpers ----------

  private void authorize(AuthUser u, Booking b) {
    if (!u.isAdmin() && !b.getOrganizerId().equals(u.id())) {
      throw ApiException.notFound("Booking");
    }
  }

  public List<BookingView> views(List<Booking> list) {
    if (list.isEmpty()) return List.of();
    Map<UUID, Room> roomMap =
        rooms.findAllById(list.stream().map(Booking::getRoomId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(Room::getId, Function.identity()));
    Map<UUID, User> userMap =
        users.findAllById(list.stream().map(Booking::getOrganizerId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    Map<UUID, BookingSeries> seriesMap =
        seriesRepo
            .findAllById(list.stream().map(Booking::getSeriesId).filter(java.util.Objects::nonNull).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(BookingSeries::getId, Function.identity()));
    return list.stream()
        .map(b -> toView(b, roomMap.get(b.getRoomId()), userMap.get(b.getOrganizerId()), seriesMap.get(b.getSeriesId())))
        .toList();
  }

  private BookingView view(Booking b) {
    return views(List.of(b)).get(0);
  }

  private static BookingView toView(Booking b, Room r, User o, BookingSeries s) {
    return new BookingView(
        b.getId(),
        b.getRoomId(),
        r.getName(),
        r.getFloor(),
        b.getTitle(),
        b.getStartsAt(),
        b.getEndsAt(),
        b.getStatus(),
        new Person(o.getName(), o.getEmail()),
        b.getAttendees().stream().map(a -> new Person(a.getName(), a.getEmail())).toList(),
        b.getSeriesId(),
        s == null ? null : s.getPattern(),
        s == null ? null : s.getOccurrences(),
        b.getVersion(),
        b.getCheckedInAt(),
        r.isCheckInRequired());
  }
}
