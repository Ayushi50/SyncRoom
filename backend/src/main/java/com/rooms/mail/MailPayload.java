package com.rooms.mail;

import java.util.List;
import java.util.UUID;

/** Snapshot of everything a template needs, so rendering never reads live data. */
public record MailPayload(
    UUID bookingId,
    long sequence,
    String title,
    String roomName,
    int floor,
    int capacity,
    List<String> equipment,
    String startsAt,
    String endsAt,
    String organizerName,
    String organizerEmail,
    List<Person> invitees,
    boolean autoRelease,
    String recipientName,
    String recipientZone,
    String baseUrl) {
  public record Person(String name, String email) {}
}
