create extension if not exists btree_gist;

create table users (
  id uuid primary key,
  name varchar(120) not null,
  email varchar(254) not null unique,
  password_hash varchar(100) not null,
  role varchar(20) not null,
  time_zone varchar(60) not null default 'Asia/Kolkata',
  created_at timestamptz not null default now()
);

create table refresh_tokens (
  id uuid primary key,
  user_id uuid not null references users(id) on delete cascade,
  token_hash varchar(64) not null unique,
  expires_at timestamptz not null,
  revoked boolean not null default false,
  created_at timestamptz not null default now()
);

create table rooms (
  id uuid primary key,
  name varchar(120) not null,
  floor int not null,
  capacity int not null check (capacity > 0),
  active boolean not null default true,
  open_time time not null default '09:00',
  close_time time not null default '18:00',
  check_in_required boolean not null default false,
  created_at timestamptz not null default now()
);

create table room_amenities (
  room_id uuid not null references rooms(id) on delete cascade,
  amenity varchar(30) not null,
  primary key (room_id, amenity)
);

create table booking_series (
  id uuid primary key,
  pattern varchar(20) not null,
  occurrences int not null check (occurrences between 1 and 12)
);

create table bookings (
  id uuid primary key,
  room_id uuid not null references rooms(id),
  organizer_id uuid not null references users(id),
  title varchar(200) not null,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  status varchar(20) not null,
  series_id uuid references booking_series(id),
  version bigint not null default 0,
  checked_in_at timestamptz,
  created_at timestamptz not null default now(),
  check (ends_at > starts_at),
  constraint bookings_no_overlap exclude using gist (
    room_id with =,
    tstzrange(starts_at, ends_at, '[)') with &&
  ) where (status = 'CONFIRMED')
);
create index bookings_organizer_idx on bookings(organizer_id, starts_at);
create index bookings_room_idx on bookings(room_id, starts_at);

create table booking_attendees (
  id uuid primary key,
  booking_id uuid not null references bookings(id) on delete cascade,
  email varchar(254) not null,
  name varchar(120),
  response_status varchar(20) not null default 'NEEDS_ACTION'
);
create index booking_attendees_booking_idx on booking_attendees(booking_id);

create table slot_holds (
  id uuid primary key,
  room_id uuid not null references rooms(id),
  user_id uuid not null references users(id),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  expires_at timestamptz not null
);
create index slot_holds_room_idx on slot_holds(room_id, starts_at);

create table email_outbox (
  id uuid primary key,
  type varchar(40) not null,
  booking_id uuid not null,
  booking_version bigint not null,
  recipient_email varchar(254) not null,
  payload text not null,
  status varchar(20) not null,
  attempts int not null default 0,
  next_attempt_at timestamptz not null default now(),
  last_error text,
  created_at timestamptz not null default now(),
  sent_at timestamptz,
  constraint email_outbox_unique unique (booking_id, type, recipient_email, booking_version)
);
create index email_outbox_due_idx on email_outbox(status, next_attempt_at);
