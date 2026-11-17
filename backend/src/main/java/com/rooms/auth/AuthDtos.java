package com.rooms.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class AuthDtos {
  private AuthDtos() {}

  public record RegisterRequest(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank @Size(min = 8, max = 72, message = "Use 8 to 72 characters") String password) {}

  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

  public record UserView(UUID id, String name, String email, Role role, String timeZone) {
    public static UserView of(User u) {
      return new UserView(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.getTimeZone());
    }
  }

  public record AuthResponse(String accessToken, long expiresInSeconds, UserView user) {}
}
