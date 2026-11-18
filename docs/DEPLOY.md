# Deploying

The build makes one jar that serves both the API and the web app, so there is one service, one address and no CORS.

```bash
./build.sh                    # needs Node 22+ and Java 21+, writes backend/target/rooms-0.1.0.jar
java -jar backend/target/rooms-0.1.0.jar
```

## What the host needs

1. **Java 21 runtime** (or a build step that runs `./build.sh` and a start command `java -jar backend/target/rooms-0.1.0.jar`).
2. **PostgreSQL 16** with the `btree_gist` extension. Flyway creates all tables on first start. The database user needs permission to create the extension, or run `create extension btree_gist;` once as an admin.
3. **HTTPS** in front of the service. Most hosts do this for you.
4. **One running copy only.** The login rate limiter and the schedulers (email worker, hold purge, reminders, auto release) run inside the app.

## Environment variables

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod`. Turns off the dev seed and Swagger, forces secure cookies, and refuses to start with a weak `JWT_SECRET`. |
| `JWT_SECRET` | `openssl rand -hex 32` |
| `COOKIE_SECURE` | `true` |
| `APP_BASE_URL`, `FRONTEND_ORIGIN` | The public HTTPS address, for example `https://rooms.example.com`. Email links use `APP_BASE_URL`. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | JDBC form: `jdbc:postgresql://host:5432/db?sslmode=require`. Hosts often give a `postgres://user:pass@host/db` URL, so split it into these three. |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_NAME` | Creates the first admin at start if that email does not exist. Password needs 12 or more characters. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM` | A transactional mail provider (Brevo, Resend, SES). Set SPF and DKIM for your sending domain. |
| `PORT` | Set by most hosts. Defaults to 8080. |

## After the first deploy

1. Open the site, sign in with `ADMIN_EMAIL`, and add your rooms under Manage rooms. Production starts with no rooms.
2. Remove `ADMIN_PASSWORD` from the host settings once the admin exists, and change nothing else.
3. Make a test booking with a real invitee and check the email arrives and the link opens.
4. Turn on database backups at your provider.

## Not covered

No container files are included, as requested. Health check URL: `/actuator/health`.
