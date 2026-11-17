package com.rooms.auth;

import com.rooms.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Sliding window limiter keyed by IP and by email. */
@Component
public class LoginRateLimiter {
  static final int MAX_ATTEMPTS = 10;
  static final Duration WINDOW = Duration.ofMinutes(5);
  private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
  private final Clock clock;

  public LoginRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public void check(String ip, String email) {
    record("ip:" + ip);
    record("email:" + email.toLowerCase());
  }

  private void record(String key) {
    Instant now = clock.instant();
    Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) q.pollFirst();
      if (q.size() >= MAX_ATTEMPTS) {
        throw new ApiException(
            HttpStatus.TOO_MANY_REQUESTS,
            "Too many attempts",
            "Too many sign in attempts. Wait a few minutes and try again.");
      }
      q.addLast(now);
    }
  }

  public void reset() {
    hits.clear();
  }
}
