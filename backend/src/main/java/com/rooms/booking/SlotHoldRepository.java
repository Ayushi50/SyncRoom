package com.rooms.booking;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SlotHoldRepository extends JpaRepository<SlotHold, UUID> {

  @Query(
      "select h from SlotHold h where h.roomId in :roomIds and h.expiresAt > :now "
          + "and h.startsAt < :to and h.endsAt > :from")
  List<SlotHold> findActiveOverlapping(
      Collection<UUID> roomIds, Instant now, Instant from, Instant to);

  List<SlotHold> findByUserId(UUID userId);

  @Modifying
  @Query("delete from SlotHold h where h.expiresAt <= :now")
  int deleteExpired(Instant now);
}
