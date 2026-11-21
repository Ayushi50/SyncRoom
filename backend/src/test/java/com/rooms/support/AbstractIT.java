package com.rooms.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetup;
import com.rooms.auth.LoginRateLimiter;
import com.rooms.auth.Role;
import com.rooms.auth.User;
import com.rooms.auth.UserRepository;
import com.rooms.room.Amenity;
import com.rooms.room.Room;
import com.rooms.room.RoomRepository;
import java.time.*;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIT {
  @RegisterExtension
  protected static GreenMailExtension mail =
      new GreenMailExtension(new ServerSetup(3025, "localhost", "smtp"))
          .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());

  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected RoomRepository rooms;
  @Autowired protected UserRepository users;
  @Autowired protected PasswordEncoder encoder;
  @Autowired protected LoginRateLimiter limiter;
  @Autowired protected com.rooms.auth.JwtService jwt;

  protected Room small;
  protected Room big;
  protected Room checkIn;

  @BeforeEach
  void resetDatabase() {
    jdbc.execute(
        "truncate email_outbox, slot_holds, booking_attendees, bookings, booking_series, room_amenities, rooms, refresh_tokens, users cascade");
    limiter.reset();
    small = rooms.save(new Room("Maple", 2, 4, EnumSet.of(Amenity.WHITEBOARD)));
    big = rooms.save(new Room("Pinehurst", 1, 12, EnumSet.of(Amenity.PROJECTOR, Amenity.VIDEO_CALL)));
    Room c = new Room("Cedar", 2, 8, EnumSet.of(Amenity.PROJECTOR));
    c.setCheckInRequired(true);
    checkIn = rooms.save(c);
    mail.reset();
  }

  protected User user(String email, Role role) {
    return users.save(new User(email.split("@")[0], email, encoder.encode("password-123"), role));
  }

  /** Signs in and returns the access token. */
  protected String token(String email, String password) throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andReturn();
    JsonNode n = json.readTree(r.getResponse().getContentAsString());
    return n.get("accessToken").asText();
  }

  /** Creates a user and issues an access token directly, bypassing the login rate limiter. */
  protected String login(String email, Role role) {
    return jwt.issue(user(email, role));
  }

  protected MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
    return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
  }

  /** The next Monday to Friday at least {@code daysAhead} days out, at the given hour in IST. */
  protected static Instant slot(int daysAhead, int hour, int minute) {
    LocalDate d = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(daysAhead);
    while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY)
      d = d.plusDays(1);
    return d.atTime(hour, minute).atZone(ZoneId.of("Asia/Kolkata")).toInstant();
  }

  protected String bookingJson(Room room, Instant start, Instant end, String extra) {
    return "{\"roomId\":\""
        + room.getId()
        + "\",\"title\":\"Planning\",\"startsAt\":\""
        + start
        + "\",\"endsAt\":\""
        + end
        + "\""
        + (extra == null ? "" : "," + extra)
        + "}";
  }

  protected static Set<Amenity> none() {
    return EnumSet.noneOf(Amenity.class);
  }
}
