package com.rooms.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
  @Id private UUID id;
  private String name;

  @Column(unique = true)
  private String email;

  private String passwordHash;

  @Enumerated(EnumType.STRING)
  private Role role;

  private String timeZone = "Asia/Kolkata";
  private Instant createdAt = Instant.now();

  protected User() {}

  public User(String name, String email, String passwordHash, Role role) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public String getTimeZone() {
    return timeZone;
  }

  public void setTimeZone(String timeZone) {
    this.timeZone = timeZone;
  }
}
