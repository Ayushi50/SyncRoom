package com.rooms.admin;

import com.rooms.auth.Role;
import com.rooms.auth.User;
import com.rooms.auth.UserRepository;
import com.rooms.common.RoomsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first admin from ADMIN_EMAIL and ADMIN_PASSWORD when that email does not exist yet.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
  private final UserRepository users;
  private final PasswordEncoder encoder;
  private final RoomsProperties props;

  public AdminBootstrap(UserRepository users, PasswordEncoder encoder, RoomsProperties props) {
    this.users = users;
    this.encoder = encoder;
    this.props = props;
  }

  @Override
  public void run(ApplicationArguments args) {
    String email = props.adminEmail();
    String password = props.adminPassword();
    if (email == null || email.isBlank() || password == null || password.isBlank()) return;
    if (password.length() < 12) {
      throw new IllegalStateException("ADMIN_PASSWORD must have at least 12 characters.");
    }
    if (users.existsByEmailIgnoreCase(email.trim())) return;
    users.save(
        new User(
            props.adminName(), email.trim().toLowerCase(), encoder.encode(password), Role.ADMIN));
    log.info("Created the first admin account");
  }
}
