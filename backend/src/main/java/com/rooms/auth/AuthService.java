package com.rooms.auth;

import com.rooms.auth.AuthDtos.*;
import com.rooms.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  public static final Duration REFRESH_TTL = Duration.ofDays(7);
  private static final SecureRandom RANDOM = new SecureRandom();

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder encoder;
  private final JwtService jwt;
  private final Clock clock;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder encoder,
      JwtService jwt,
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.encoder = encoder;
    this.jwt = jwt;
    this.clock = clock;
  }

  public record Session(AuthResponse response, String refreshToken) {}

  @Transactional
  public Session register(RegisterRequest r) {
    if (users.existsByEmailIgnoreCase(r.email())) {
      throw ApiException.conflict(
          "Email already registered",
          "An account with this email already exists. Sign in instead.",
          java.util.Map.of());
    }
    User u =
        users.save(
            new User(
                r.name().trim(),
                r.email().trim().toLowerCase(),
                encoder.encode(r.password()),
                Role.EMPLOYEE));
    return open(u);
  }

  @Transactional
  public Session login(LoginRequest r) {
    User u = users.findByEmailIgnoreCase(r.email().trim()).orElse(null);
    if (u == null || !encoder.matches(r.password(), u.getPasswordHash())) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "Sign in failed",
          "The email or password is not correct. Check them and try again.");
    }
    return open(u);
  }

  @Transactional
  public Session refresh(String token) {
    if (token == null || token.isBlank()) throw invalidRefresh();
    RefreshToken rt =
        refreshTokens.findByTokenHash(hash(token)).orElseThrow(AuthService::invalidRefresh);
    if (rt.isRevoked() || rt.getExpiresAt().isBefore(clock.instant())) throw invalidRefresh();
    rt.revoke();
    User u = users.findById(rt.getUserId()).orElseThrow(AuthService::invalidRefresh);
    return open(u);
  }

  @Transactional
  public void logout(String token) {
    if (token == null || token.isBlank()) return;
    refreshTokens.findByTokenHash(hash(token)).ifPresent(RefreshToken::revoke);
  }

  public User get(java.util.UUID id) {
    return users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
  }

  private Session open(User u) {
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    refreshTokens.save(new RefreshToken(u.getId(), hash(token), clock.instant().plus(REFRESH_TTL)));
    return new Session(
        new AuthResponse(jwt.issue(u), JwtService.ACCESS_TTL.toSeconds(), UserView.of(u)), token);
  }

  private static ApiException invalidRefresh() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "Session expired", "Your session has expired. Sign in again.");
  }

  static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
