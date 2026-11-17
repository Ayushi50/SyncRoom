package com.rooms.booking;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "booking_attendees")
public class BookingAttendee {
  @Id private UUID id;
  private String email;
  private String name;
  private String responseStatus = "NEEDS_ACTION";

  protected BookingAttendee() {}

  public BookingAttendee(String email, String name) {
    this.id = UUID.randomUUID();
    this.email = email;
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public String getResponseStatus() {
    return responseStatus;
  }
}
