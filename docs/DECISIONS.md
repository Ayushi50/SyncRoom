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
| 2026-11-17 | `tools/mail-sink.mjs` is a tiny SMTP catcher with a Mailpit style `/api/v1/messages` endpoint on port 8025. | Lets the Playwright path check emails without Docker. Real Mailpit works the same. |
| 2026-11-17 | Landing and finder copy that promised features the backend does not have was reworded: no waitlist, no limits by team, no "Invitations" nav item, no custom repeat pattern, no per booking auto release checkbox. | Rule: never invent features in the UI. Auto release is a room setting, so the booking panel shows a note for rooms that use it. |
| 2026-11-17 | Footer links to privacy, terms and contact were dropped. | There are no such pages. |
| 2026-11-17 | Spotless uses google-java-format 1.35.0. | 1.25 does not run on JDK 27. |
| 2026-11-17 | Refresh cookie has path `/api/auth` and SameSite Strict, and CSRF protection is off. | The API uses bearer tokens. The cookie can only be sent to the auth endpoints from the same site. |
