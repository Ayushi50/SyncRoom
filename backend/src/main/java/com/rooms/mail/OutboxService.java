package com.rooms.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rooms.auth.User;
import com.rooms.auth.UserRepository;
import com.rooms.booking.Booking;
import com.rooms.booking.BookingAttendee;
import com.rooms.common.RoomsProperties;
import com.rooms.room.Room;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
  private final EmailOutboxRepository outbox;
  private final UserRepository users;
  private final ObjectMapper mapper;
  private final RoomsProperties props;

  public OutboxService(
      EmailOutboxRepository outbox,
      UserRepository users,
      ObjectMapper mapper,
      RoomsProperties props) {
    this.outbox = outbox;
    this.users = users;
    this.mapper = mapper;
    this.props = props;
  }

  /** Joins the caller's transaction so the rows commit with the booking change. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void enqueue(EmailType type, Booking b, Room room, User organizer) {
    Map<String, String> recipients = new LinkedHashMap<>();
    recipients.put(organizer.getEmail().toLowerCase(), organizer.getName());
    if (type != EmailType.BOOKING_RELEASED) {
      for (BookingAttendee a : b.getAttendees()) {
        recipients.putIfAbsent(
            a.getEmail().toLowerCase(),
            a.getName() == null || a.getName().isBlank() ? a.getEmail() : a.getName());
      }
    }
    List<MailPayload.Person> invitees =
        b.getAttendees().stream()
            .map(a -> new MailPayload.Person(a.getName(), a.getEmail()))
            .toList();
    recipients.forEach(
        (email, name) -> {
          String zone =
              users.findByEmailIgnoreCase(email).map(User::getTimeZone).orElse("Asia/Kolkata");
          MailPayload p =
              new MailPayload(
                  b.getId(),
                  b.getVersion(),
                  b.getTitle(),
                  room.getName(),
                  room.getFloor(),
                  room.getCapacity(),
                  room.getAmenities().stream().map(Enum::name).sorted().toList(),
                  b.getStartsAt().toString(),
                  b.getEndsAt().toString(),
                  organizer.getName(),
                  organizer.getEmail(),
                  invitees,
                  room.isCheckInRequired(),
                  name,
                  zone,
                  props.appBaseUrl());
          try {
            outbox.insertIgnore(
                UUID.randomUUID(),
                type.name(),
                b.getId(),
                b.getVersion(),
                email,
                mapper.writeValueAsString(p));
          } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
          }
        });
  }
}
