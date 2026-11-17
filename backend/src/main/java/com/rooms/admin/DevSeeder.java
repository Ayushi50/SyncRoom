package com.rooms.admin;

import com.rooms.auth.Role;
import com.rooms.auth.User;
import com.rooms.auth.UserRepository;
import com.rooms.room.Amenity;
import com.rooms.room.Room;
import com.rooms.room.RoomRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "rooms.seed", havingValue = "true")
public class DevSeeder implements ApplicationRunner {
  private final RoomRepository rooms;
  private final UserRepository users;
  private final PasswordEncoder encoder;

  public DevSeeder(RoomRepository rooms, UserRepository users, PasswordEncoder encoder) {
    this.rooms = rooms;
    this.users = users;
    this.encoder = encoder;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (rooms.count() == 0) {
      room("Kaveri", 2, 4, false);
      room("Ganga", 2, 8, true, Amenity.PROJECTOR, Amenity.VIDEO_CALL, Amenity.WHITEBOARD);
      room("Narmada", 1, 12, true, Amenity.PROJECTOR, Amenity.VIDEO_CALL, Amenity.ACCESSIBLE);
      room("Godavari", 3, 6, false, Amenity.VIDEO_CALL, Amenity.WHITEBOARD);
      room("Yamuna", 2, 2, false);
      room("Brahmaputra", 3, 16, true, Amenity.PROJECTOR, Amenity.VIDEO_CALL);
    }
    if (!users.existsByEmailIgnoreCase("admin@rooms.local")) {
      users.save(new User("Admin", "admin@rooms.local", encoder.encode("admin-dev-pass"), Role.ADMIN));
    }
    if (!users.existsByEmailIgnoreCase("employee@rooms.local")) {
      users.save(new User("Employee", "employee@rooms.local", encoder.encode("employee-dev-pass"), Role.EMPLOYEE));
    }
  }

  private void room(String name, int floor, int cap, boolean checkIn, Amenity... a) {
    Set<Amenity> set = a.length == 0 ? EnumSet.noneOf(Amenity.class) : EnumSet.copyOf(java.util.List.of(a));
    Room r = new Room(name, floor, cap, set);
    r.setCheckInRequired(checkIn);
    rooms.save(r);
  }
}
