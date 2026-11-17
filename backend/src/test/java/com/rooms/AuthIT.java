package com.rooms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.rooms.auth.Role;
import com.rooms.support.AbstractIT;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class AuthIT extends AbstractIT {

  private static final String REGISTER =
      "{\"name\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"correct-horse\"}";

  @Test
  void registerReturnsTokenAndHttpOnlyRefreshCookie() throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(REGISTER))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
            .andReturn();
    String cookie = r.getResponse().getHeader("Set-Cookie");
    assertThat(cookie).contains("refresh_token=").contains("HttpOnly").contains("SameSite=Strict");
  }

  @Test
  void duplicateEmailIsAConflict() throws Exception {
    mvc.perform(
        post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER));
    mvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }

  @Test
  void weakPasswordIsRejectedWithFieldErrors() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"email\":\"a@b.co\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.password").exists());
  }

  @Test
  void loginWithWrongPasswordIs401() throws Exception {
    user("asha@example.com", Role.EMPLOYEE);
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"asha@example.com\",\"password\":\"nope-nope\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshRotatesTokenAndOldOneStopsWorking() throws Exception {
    MvcResult reg =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(REGISTER))
            .andReturn();
    Cookie first = reg.getResponse().getCookie("refresh_token");
    MvcResult second =
        mvc.perform(post("/api/auth/refresh").cookie(first))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();
    Cookie rotated = second.getResponse().getCookie("refresh_token");
    assertThat(rotated.getValue()).isNotEqualTo(first.getValue());
    mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/refresh").cookie(rotated)).andExpect(status().isOk());
  }

  @Test
  void logoutRevokesRefreshToken() throws Exception {
    MvcResult reg =
        mvc.perform(
                post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(REGISTER))
            .andReturn();
    Cookie c = reg.getResponse().getCookie("refresh_token");
    mvc.perform(post("/api/auth/logout").cookie(c)).andExpect(status().isNoContent());
    mvc.perform(post("/api/auth/refresh").cookie(c)).andExpect(status().isUnauthorized());
  }

  @Test
  void protectedEndpointNeedsToken() throws Exception {
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/bookings/mine")).andExpect(status().isUnauthorized());
  }

  @Test
  void meReturnsCurrentUser() throws Exception {
    String t = login("asha@example.com", Role.EMPLOYEE);
    mvc.perform(auth(get("/api/me"), t))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("asha@example.com"));
  }

  @Test
  void employeeCannotUseAdminEndpoints() throws Exception {
    String t = login("asha@example.com", Role.EMPLOYEE);
    mvc.perform(auth(get("/api/admin/bookings"), t)).andExpect(status().isForbidden());
    mvc.perform(auth(get("/api/admin/utilization"), t)).andExpect(status().isForbidden());
    mvc.perform(auth(post("/api/rooms"), t).content("{\"name\":\"X\",\"floor\":1,\"capacity\":4}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateAndSoftDeleteRooms() throws Exception {
    String t = login("boss@example.com", Role.ADMIN);
    mvc.perform(
            auth(post("/api/rooms"), t)
                .content(
                    "{\"name\":\"Sabarmati\",\"floor\":4,\"capacity\":6,\"amenities\":[\"PROJECTOR\"]}"))
        .andExpect(status().isCreated());
    mvc.perform(auth(get("/api/admin/utilization"), t)).andExpect(status().isOk());
    mvc.perform(auth(delete("/api/rooms/" + small.getId()), t)).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/rooms"), t)).andExpect(jsonPath("$[?(@.name=='Kaveri')]").isEmpty());
  }

  @Test
  void repeatedBadLoginsAreRateLimited() throws Exception {
    user("asha@example.com", Role.EMPLOYEE);
    int last = 0;
    for (int i = 0; i < 12; i++) {
      last =
          mvc.perform(
                  post("/api/auth/login")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"email\":\"asha@example.com\",\"password\":\"wrong-wrong\"}"))
              .andReturn()
              .getResponse()
              .getStatus();
    }
    assertThat(last).isEqualTo(429);
  }
}
