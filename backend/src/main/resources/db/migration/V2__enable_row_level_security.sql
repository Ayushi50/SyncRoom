-- The app connects as the table owner, which bypasses row level security.
-- With no policies, anyone else (for example a hosted data API using a public key) sees nothing.
alter table users enable row level security;
alter table refresh_tokens enable row level security;
alter table rooms enable row level security;
alter table room_amenities enable row level security;
alter table booking_series enable row level security;
alter table bookings enable row level security;
alter table booking_attendees enable row level security;
alter table slot_holds enable row level security;
alter table email_outbox enable row level security;
