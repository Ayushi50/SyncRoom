package com.rooms.common;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Refuses to start in production with the development JWT secret or a weak one. */
@Component
@Profile("prod")
public class ProdSafetyCheck {
  public ProdSafetyCheck(RoomsProperties props) {
    if (props.jwtSecret() == null
        || props.jwtSecret().length() < 32
        || props.jwtSecret().startsWith("dev-only")) {
      throw new IllegalStateException(
          "Set JWT_SECRET to a random string of at least 32 characters.");
    }
    if (!props.cookieSecure()) {
      throw new IllegalStateException("COOKIE_SECURE must be true in production.");
    }
  }
}
