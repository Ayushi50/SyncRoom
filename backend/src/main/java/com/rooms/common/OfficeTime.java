package com.rooms.common;

import java.time.ZoneId;

/** Open hours and search dates are interpreted in the office time zone. */
public final class OfficeTime {
  public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  private OfficeTime() {}
}
