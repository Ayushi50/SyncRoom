# API

REST under `/api`, JSON, errors as RFC 7807 problem details. Full spec at `/swagger-ui.html` and `/v3/api-docs`. Times are ISO 8601 instants.

| Method and path | Who | Notes |
| --- | --- | --- |
| POST /auth/register, /auth/login | public | Returns `accessToken` (15 min) and sets an HttpOnly `refresh_token` cookie (7 days). Login is rate limited per IP and email (429). |
| POST /auth/refresh, /auth/logout | cookie | Refresh rotates the token. The old one stops working. |
| GET /me | user | Current user. |
| GET /rooms, /rooms/{id} | user | `includeInactive=true` lists out of service rooms. |
| GET /rooms/available | user | `date`, `start`, `end` (office time zone), `people`, `amenities`. Returns `rooms` (best fit first, each with `busy` segments) and `nearby` suggestions when nothing fits. |
| POST /rooms, PUT /rooms/{id}, DELETE /rooms/{id} | admin | DELETE is a soft delete. |
| POST /holds, DELETE /holds/{id} | user | Holds a slot for 5 minutes. One hold per user. 409 if taken. |
| POST /bookings | user | Optional `repeat` (`DAILY`, `WEEKDAYS`, `WEEKLY`, count up to 12) and `holdId`. 409 lists `conflicts` and `alternatives`. Nothing is created when any date conflicts. |
| GET /bookings/mine | user | `when=upcoming` (default), `past` or `all`. |
| GET /bookings/{id} | organizer, admin | |
| PATCH /bookings/{id} | organizer, admin | `startsAt`, `endsAt`, `title`, `scope` (`THIS` or `FOLLOWING`), `version` for optimistic locking. |
| DELETE /bookings/{id}?scope= | organizer, admin | Cancels. |
| POST /bookings/{id}/check-in | organizer | From 15 minutes before the start until the end. |
| GET /admin/bookings | admin | `status`, `page`, `size`. |
| GET /admin/utilization | admin | Booked hours against open hours per room, last 30 days. |

Status codes: 400 validation, 401 not signed in, 403 not allowed, 404 not found (also for someone else's booking), 409 conflict or stale update, 429 rate limit.
