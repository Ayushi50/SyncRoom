# Decisions

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-11-17 | No Docker, no docker-compose, no Testcontainers. Postgres 16 runs locally (Homebrew) and tests use the `rooms_test` database. | The user asked to ignore everything about Docker. The exclusion constraint is still tested against a real PostgreSQL, not H2. |
| 2026-11-17 | Mail in dev goes to any SMTP sink on port 1025 (Mailpit binary if installed). Tests use GreenMail on port 3025. | Without Docker, Mailpit is optional. |
| 2026-11-17 | Compile with `release 21` on the installed JDK 27. | Only JDK 27 is installed; bytecode targets Java 21 as specified. |
| 2026-11-17 | Open hours and search dates are read in Asia/Kolkata (`OfficeTime.ZONE`). | One office, one zone. Times are stored in UTC and sent as ISO 8601. |
| 2026-11-17 | Seed amenities use the four enum values only. "TV" and "Phone booth" from the seed table are left out. | The spec's amenity enum has no such values. |
| 2026-11-17 | For a repeating series, one email goes out for the first occurrence, and for reschedule or cancel with scope "this and following" one email for the pivot booking. | Avoids up to 12 emails per recipient for one action. |
| 2026-11-17 | Room row is locked (`select for update`) while creating holds, bookings and reschedules. The exclusion constraint stays as the final guard. | Gives clean 409 responses with alternatives instead of constraint errors under load. |
| 2026-11-17 | Tests use IT suffix and run in surefire. | A single `./mvnw verify` runs everything. |
