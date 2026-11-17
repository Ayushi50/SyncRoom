package com.rooms.mail;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

  @Query(
      value =
          "select * from email_outbox where status = 'PENDING' and next_attempt_at <= now() "
              + "order by next_attempt_at limit 20 for update skip locked",
      nativeQuery = true)
  List<EmailOutbox> lockDue();

  /** Idempotent insert: the unique key makes a repeat a no-op. */
  @Modifying
  @Query(
      value =
          "insert into email_outbox (id, type, booking_id, booking_version, recipient_email, payload, status, attempts, next_attempt_at, created_at) "
              + "values (:id, :type, :bookingId, :version, :recipient, :payload, 'PENDING', 0, now(), now()) "
              + "on conflict (booking_id, type, recipient_email, booking_version) do nothing",
      nativeQuery = true)
  int insertIgnore(
      UUID id, String type, UUID bookingId, long version, String recipient, String payload);

  List<EmailOutbox> findByBookingId(UUID bookingId);
}
