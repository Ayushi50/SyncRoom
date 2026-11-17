package com.rooms.room;

import com.rooms.common.ApiException;
import com.rooms.room.RoomDtos.*;
import jakarta.validation.Valid;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
  private final RoomRepository rooms;

  public RoomController(RoomRepository rooms) {
    this.rooms = rooms;
  }

  @GetMapping
  public List<RoomView> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
    var list = includeInactive ? rooms.findAll(org.springframework.data.domain.Sort.by("name")) : rooms.findByActiveTrueOrderByNameAsc();
    return list.stream().map(RoomView::of).toList();
  }

  @GetMapping("/{id}")
  public RoomView get(@PathVariable UUID id) {
    return RoomView.of(rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room")));
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.CREATED)
  public RoomView create(@Valid @RequestBody RoomRequest r) {
    Room room = new Room(r.name(), r.floor(), r.capacity(), amenities(r));
    apply(room, r);
    return RoomView.of(rooms.save(room));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public RoomView update(@PathVariable UUID id, @Valid @RequestBody RoomRequest r) {
    Room room = rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room"));
    apply(room, r);
    return RoomView.of(rooms.save(room));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    Room room = rooms.findById(id).orElseThrow(() -> ApiException.notFound("Room"));
    room.setActive(false);
    rooms.save(room);
  }

  private static java.util.Set<Amenity> amenities(RoomRequest r) {
    return r.amenities() == null ? EnumSet.noneOf(Amenity.class) : r.amenities();
  }

  private static void apply(Room room, RoomRequest r) {
    LocalTime open = r.openTime() == null ? LocalTime.of(9, 0) : r.openTime();
    LocalTime close = r.closeTime() == null ? LocalTime.of(18, 0) : r.closeTime();
    if (!close.isAfter(open)) {
      throw ApiException.badRequest("Close time must be after open time.");
    }
    room.update(
        r.name().trim(),
        r.floor(),
        r.capacity(),
        amenities(r),
        open,
        close,
        Boolean.TRUE.equals(r.checkInRequired()));
  }
}
