package com.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rooms.admin.AdminBootstrap;
import com.rooms.auth.Role;
import com.rooms.support.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {"rooms.admin-email=Boss@Example.com", "rooms.admin-password=a-long-enough-pass"})
class AdminBootstrapIT extends AbstractIT {
  @Autowired AdminBootstrap bootstrap;

  @Test
  void createsTheFirstAdminOnceAndTheAdminCanSignIn() throws Exception {
    bootstrap.run(null);
    bootstrap.run(null);
    assertThat(users.findAll()).hasSize(1);
    assertThat(users.findByEmailIgnoreCase("boss@example.com").orElseThrow().getRole())
        .isEqualTo(Role.ADMIN);
    String token = token("boss@example.com", "a-long-enough-pass");
    mvc.perform(auth(get("/api/admin/utilization"), token)).andExpect(status().isOk());
  }

  @Test
  void webAppRoutesAreNotBlockedButApiStillIs() throws Exception {
    mvc.perform(get("/rooms/find")).andExpect(status().is(org.hamcrest.Matchers.not(401)));
    mvc.perform(get("/api/bookings/mine")).andExpect(status().isUnauthorized());
  }
}
