package com.rooms.booking;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "booking_series")
public class BookingSeries {
  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  private SeriesPattern pattern;

  private int occurrences;

  protected BookingSeries() {}

  public BookingSeries(SeriesPattern pattern, int occurrences) {
    this.id = UUID.randomUUID();
    this.pattern = pattern;
    this.occurrences = occurrences;
  }

  public UUID getId() {
    return id;
  }

  public SeriesPattern getPattern() {
    return pattern;
  }

  public int getOccurrences() {
    return occurrences;
  }
}
