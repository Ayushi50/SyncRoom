# Morning report

## What works

- Public landing page at `/` built from the mockup, every call to action goes through sign in and back to `/rooms/find`.
- Register, sign in, refresh token rotation, sign out. Admin and employee roles.
- Finder at `/rooms/find` on the real API: date, time, people and equipment search, best fit first, day availability bar per room, nearby time suggestions, a 5 minute hold with countdown, booking panel with title, invitees, repeat, and conflict alternatives.
- My bookings, booking detail with reschedule, cancel, scope this or following, and check in.
- Admin: manage rooms, all bookings with a status filter, 30 day utilization.
- Email through an outbox: confirmed, rescheduled, cancelled, reminder and released, HTML plus plain text, `invite.ics` with REQUEST or CANCEL.
- Repeats (daily, weekdays, weekly, up to 12) are all or nothing. Holds expire. Rooms with check in required are released after 10 minutes.

## Test results

| Suite | Result |
| --- | --- |
| Backend `./mvnw verify` | 49 tests pass (AuthIT 11, BookingIT 13, SeriesHoldReleaseIT 13, EmailIT 12), spotless check passes |
| Backend line coverage | auth 97, availability 99, booking 90, mail 89, room 93, common 83, admin 52 percent |
| Concurrency | 50 parallel requests for one slot give exactly one 201 and 49 409s |
| Frontend `npm test` | 20 tests pass (search form, availability bar, booking panel, time helpers) |
| Frontend lint and build | clean |
| Playwright happy path | passes: landing, register, search, book, listed, two emails seen via the mail API |
| Overflow check at 390 px | no horizontal overflow on landing, login, register, 404, finder, my bookings |

## Screenshots

`docs/screenshots/landing-1280.png`, `landing-390.png`, `finder-1280.png`, `finder-390.png`. Compared with the mockups, layout, colors, type and copy match. Differences are deliberate: no "Invitations" nav item, no floor area names, no per booking auto release checkbox, because the backend has no such features.

## Decisions

See `docs/DECISIONS.md`. The big one: no Docker. Postgres runs locally, tests use `rooms_test` instead of Testcontainers, and `tools/mail-sink.mjs` stands in for Mailpit. There is no docker-compose.yml and no container step in CI services other than a Postgres service container on GitHub.

## Known gaps

- The CI workflow was not run on GitHub, only its commands locally. It targets Java 21 and Node 20, while this machine has JDK 27 and Node 26.
- A series sends one email for the first meeting, not one per occurrence.
- Invitees are plain email addresses. There is no accept or decline flow, so response status stays NEEDS_ACTION.
- Auto release is a room setting, not a per booking choice.
- The admin day view (timeline mockup) and the floor map are not built.
- Rescheduling a series forward by less than the gap between meetings may trip the database constraint mid update and return a 409.
- No accessibility tooling run (axe). Done by hand: labels, roles, focus rings, 44 px targets, contrast colors from the mockup palette.

## Next three things

1. Run the workflow on GitHub and add an axe check to the Playwright test.
2. One email for a series with the full list of dates, and invitee responses from the calendar reply.
3. Admin day view from `Main.dc.html`, plus Kafka events from the outbox.
