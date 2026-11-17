package com.rooms.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

  @Query(
      "select b from Booking b where b.roomId in :roomIds and b.status = 'CONFIRMED' "
          + "and b.startsAt < :to and b.endsAt > :from order by b.startsAt")
  List<Booking> findConfirmedOverlapping(Collection<UUID> roomIds, Instant from, Instant to);

  @Query(
      "select b from Booking b where b.organizerId = :uid and b.startsAt >= :from "
          + "order by b.startsAt asc")
  List<Booking> upcomingFor(UUID uid, Instant from);

  @Query(
      "select b from Booking b where b.organizerId = :uid and b.startsAt < :before "
          + "order by b.startsAt desc")
  List<Booking> pastFor(UUID uid, Instant before);

  @Query(
      "select b from Booking b where b.seriesId = :sid and b.startsAt >= :from "
          + "and b.status = 'CONFIRMED' order by b.startsAt")
  List<Booking> seriesFrom(UUID sid, Instant from);

  @Query(
      "select b from Booking b where b.status = 'CONFIRMED' and b.startsAt > :now "
          + "and b.startsAt <= :until")
  List<Booking> startingBetween(Instant now, Instant until);

  @Query(
      "select b from Booking b where b.status = 'CONFIRMED' and b.checkedInAt is null "
          + "and b.startsAt <= :cutoff and b.roomId in "
          + "(select r.id from com.rooms.room.Room r where r.checkInRequired = true)")
  List<Booking> unattended(Instant cutoff);

  @Query(
      "select b from Booking b where (:status is null or b.status = :status) order by b.startsAt desc")
  Page<Booking> adminList(BookingStatus status, Pageable pageable);

  @Query(
      "select b from Booking b where b.status = 'CONFIRMED' and b.startsAt < :to and b.endsAt > :from")
  List<Booking> confirmedBetween(Instant from, Instant to);
}
