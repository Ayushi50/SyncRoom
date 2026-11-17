package com.rooms.booking;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeriesRepository extends JpaRepository<BookingSeries, UUID> {}
