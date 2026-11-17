package com.rooms.mail;

public enum EmailType {
  BOOKING_CONFIRMED("Booked"),
  BOOKING_RESCHEDULED("Rescheduled"),
  BOOKING_CANCELLED("Cancelled"),
  BOOKING_REMINDER("Reminder"),
  BOOKING_RELEASED("Released");

  private final String subjectPrefix;

  EmailType(String subjectPrefix) {
    this.subjectPrefix = subjectPrefix;
  }

  public String subjectPrefix() {
    return subjectPrefix;
  }
}
