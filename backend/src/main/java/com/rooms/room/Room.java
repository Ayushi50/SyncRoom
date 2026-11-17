package com.rooms.room;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {
  @Id private UUID id;
  private String name;
  private int floor;
  private int capacity;
  private boolean active = true;
  private LocalTime openTime = LocalTime.of(9, 0);
  private LocalTime closeTime = LocalTime.of(18, 0);
  private boolean checkInRequired;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "room_amenities", joinColumns = @JoinColumn(name = "room_id"))
  @Column(name = "amenity")
  @Enumerated(EnumType.STRING)
  private Set<Amenity> amenities = EnumSet.noneOf(Amenity.class);

  protected Room() {}

  public Room(String name, int floor, int capacity, Set<Amenity> amenities) {
    this.id = UUID.randomUUID();
    update(name, floor, capacity, amenities, LocalTime.of(9, 0), LocalTime.of(18, 0), false);
  }

  public void update(
      String name,
      int floor,
      int capacity,
      Set<Amenity> amenities,
      LocalTime open,
      LocalTime close,
      boolean checkInRequired) {
    this.name = name;
    this.floor = floor;
    this.capacity = capacity;
    this.amenities = amenities.isEmpty() ? EnumSet.noneOf(Amenity.class) : EnumSet.copyOf(amenities);
    this.openTime = open;
    this.closeTime = close;
    this.checkInRequired = checkInRequired;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getFloor() {
    return floor;
  }

  public int getCapacity() {
    return capacity;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public LocalTime getOpenTime() {
    return openTime;
  }

  public LocalTime getCloseTime() {
    return closeTime;
  }

  public boolean isCheckInRequired() {
    return checkInRequired;
  }

  public Set<Amenity> getAmenities() {
    return amenities;
  }

  public void setCheckInRequired(boolean v) {
    this.checkInRequired = v;
  }
}
