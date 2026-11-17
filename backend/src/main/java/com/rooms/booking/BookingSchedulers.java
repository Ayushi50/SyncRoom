package com.rooms.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingSchedulers {
  private static final Logger log = LoggerFactory.getLogger(BookingSchedulers.class);
  private final BookingService service;

  public BookingSchedulers(BookingService service) {
    this.service = service;
  }

  @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
  public void purgeHolds() {
    run("hold purge", service::purgeExpiredHolds);
  }

  @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
  public void release() {
    run("auto release", service::releaseUnattended);
  }

  @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
  public void reminders() {
    run("reminders", service::enqueueReminders);
  }

  private void run(String name, java.util.function.IntSupplier job) {
    try {
      job.getAsInt();
    } catch (Exception e) {
      log.warn("Scheduled {} failed with {}", name, e.getClass().getSimpleName());
    }
  }
}
