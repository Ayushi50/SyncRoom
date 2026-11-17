package com.rooms.auth;

import java.util.UUID;

public record AuthUser(UUID id, String email, Role role) {
  public boolean isAdmin() {
    return role == Role.ADMIN;
  }
}
