package com.rooms.booking;

import com.rooms.common.ApiException;
import com.rooms.common.OfficeTime;
import com.rooms.room.Room;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public final class BookingRules {
  public static final Duration MIN = Duration.ofMinutes(15);
  public static final Duration MAX = Duration.ofHours(8);
  public static final int MAX_DAYS_AHEAD = 90;

  private BookingRules() {}

  public static void validateWindow(Instant start, Instant end, Instant now) {
    if (start == null || end == null)
      throw ApiException.badRequest("Start and end times are required.");
    if (!end.isAfter(start))
      throw ApiException.badRequest("The end time must be after the start time.");
    Duration d = Duration.between(start, end);
    if (d.compareTo(MIN) < 0 || d.compareTo(MAX) > 0) {
      throw ApiException.badRequest("A booking must last between 15 minutes and 8 hours.");
    }
    if (d.toMinutes() % 15 != 0 || start.getEpochSecond() % 900 != 0) {
      throw ApiException.badRequest("Start and duration must be in 15 minute steps.");
    }
    if (start.isBefore(now))
      throw ApiException.badRequest("This time is in the past. Pick a later time.");
    if (start.isAfter(now.plus(Duration.ofDays(MAX_DAYS_AHEAD)))) {
      throw ApiException.badRequest("Bookings can be made up to 90 days ahead.");
    }
  }

  public static void validateOpenHours(Room room, Instant start, Instant end) {
    ZonedDateTime s = start.atZone(OfficeTime.ZONE);
    ZonedDateTime e = end.atZone(OfficeTime.ZONE);
    LocalTime st = s.toLocalTime();
    LocalTime et = e.toLocalTime();
    boolean sameDay = s.toLocalDate().equals(e.toLocalDate());
    if (!sameDay || st.isBefore(room.getOpenTime()) || et.isAfter(room.getCloseTime())) {
      throw ApiException.badRequest(
          room.getName()
              + " is open from "
              + room.getOpenTime()
              + " to "
              + room.getCloseTime()
              + ". Pick a time inside those hours.");
    }
  }
}
