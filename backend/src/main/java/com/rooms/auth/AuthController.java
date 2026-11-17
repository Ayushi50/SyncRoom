package com.rooms.auth;

import com.rooms.auth.AuthDtos.*;
import com.rooms.common.RoomsProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
  static final String COOKIE = "refresh_token";
  private final AuthService auth;
  private final LoginRateLimiter limiter;
  private final RoomsProperties props;

  public AuthController(AuthService auth, LoginRateLimiter limiter, RoomsProperties props) {
    this.auth = auth;
    this.limiter = limiter;
    this.props = props;
  }

  @PostMapping("/auth/register")
  public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest r) {
    return respond(auth.register(r), 201);
  }

  @PostMapping("/auth/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest r, HttpServletRequest req) {
    limiter.check(req.getRemoteAddr(), r.email());
    return respond(auth.login(r), 200);
  }

  @PostMapping("/auth/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = COOKIE, required = false) String token) {
    return respond(auth.refresh(token), 200);
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(@CookieValue(name = COOKIE, required = false) String token) {
    auth.logout(token);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, cookie("", 0).toString())
        .build();
  }

  @GetMapping("/me")
  public UserView me(@AuthenticationPrincipal AuthUser user) {
    return UserView.of(auth.get(user.id()));
  }

  private ResponseEntity<AuthResponse> respond(AuthService.Session s, int status) {
    return ResponseEntity.status(status)
        .header(
            HttpHeaders.SET_COOKIE,
            cookie(s.refreshToken(), AuthService.REFRESH_TTL.toSeconds()).toString())
        .body(s.response());
  }

  private ResponseCookie cookie(String value, long maxAge) {
    return ResponseCookie.from(COOKIE, value)
        .httpOnly(true)
        .secure(props.cookieSecure())
        .sameSite("Strict")
        .path("/api/auth")
        .maxAge(maxAge)
        .build();
  }
}
