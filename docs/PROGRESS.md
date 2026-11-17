# Progress

All phases 0 to 8 are done, without Docker as requested. See docs/MORNING_REPORT.md for results.

Resume commands:

```bash
brew services start postgresql@16
node tools/mail-sink.mjs &
cd backend && ./mvnw spring-boot:run &
cd frontend && npm run dev
```

| Phase | State |
| --- | --- |
| 0 Scaffold | Done. docker-compose is intentionally absent. |
| 1 Data and auth | Done, AuthIT |
| 2 Search and booking core | Done, BookingIT including 50 parallel requests |
| 3 Email | Done, EmailIT, mail visible in the sink |
| 4 Landing and foundation | Done |
| 5 Finder and booking flow | Done, Playwright path passes |
| 6 Repeats, holds, release | Done, SeriesHoldReleaseIT |
| 7 Admin | Done |
| 8 Polish | Done |

Known gaps: see the morning report.
