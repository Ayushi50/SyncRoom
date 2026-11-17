package com.rooms.auth;

import com.rooms.common.RoomsProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  public static final Duration ACCESS_TTL = Duration.ofMinutes(15);
  private final SecretKey key;
  private final Clock clock;

  public JwtService(RoomsProperties props, Clock clock) {
    this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
    this.clock = clock;
  }

  public String issue(User user) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ACCESS_TTL)))
        .signWith(key)
        .compact();
  }

  public Optional<AuthUser> parse(String token) {
    try {
      Claims c =
          Jwts.parser()
              .verifyWith(key)
              .clock(() -> Date.from(clock.instant()))
              .build()
              .parseSignedClaims(token)
              .getPayload();
      return Optional.of(
          new AuthUser(
              UUID.fromString(c.getSubject()),
              c.get("email", String.class),
              Role.valueOf(c.get("role", String.class))));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
