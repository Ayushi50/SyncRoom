import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ClipboardText } from '@phosphor-icons/react';
import { api, ApiError } from '../lib/api';
import type { Booking, BookingStatus, PageView, Utilization } from '../lib/types';
import { formatWhen, OFFICE_ZONE } from '../lib/time';
import { Button, Chip, EmptyState, InlineError, PageHeader, Skeleton } from '../components/ui';
import { StatusTag } from '../components/BookingBits';

const STATUSES: { value: '' | BookingStatus; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'CONFIRMED', label: 'Confirmed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'RELEASED', label: 'Released' },
];

export function AdminBookings() {
  const [status, setStatus] = useState<'' | BookingStatus>('');
  const [page, setPage] = useState(0);
  const bookings = useQuery({
    queryKey: ['admin-bookings', status, page],
    queryFn: () =>
      api<PageView<Booking>>(
        `/admin/bookings?page=${page}&size=10${status ? `&status=${status}` : ''}`,
      ),
  });
  const util = useQuery({
    queryKey: ['utilization'],
    queryFn: () => api<Utilization[]>('/admin/utilization'),
  });
  const pages = bookings.data
    ? Math.max(1, Math.ceil(bookings.data.total / bookings.data.size))
    : 1;

  return (
    <>
      <PageHeader
        title="All bookings"
        subtitle="Every booking in the building, and how busy each room has been."
      />

      <section
        aria-label="Room utilization"
        className="bg-card border border-line rounded-2xl p-6 flex flex-col gap-4"
      >
        <h2 className="m-0 text-title font-semibold">Utilization, last 30 days</h2>
        {util.isPending && <Skeleton className="h-24 w-full" />}
        {util.isError && (
          <InlineError title="Could not load utilization">
            {(util.error as ApiError).message}
          </InlineError>
        )}
        {util.data && util.data.length === 0 && (
          <p className="m-0 text-small text-muted">No active rooms to report on yet.</p>
        )}
        {util.data && util.data.length > 0 && (
          <ul className="m-0 p-0 list-none flex flex-col gap-4">
            {util.data.map((u) => (
              <li key={u.roomId} className="flex flex-col gap-2">
                <div className="flex justify-between gap-4 text-small">
                  <span className="font-semibold">{u.roomName}</span>
                  <span className="text-muted">
                    {u.bookedHours} of {u.openHours} open hours, {u.percent}%
                  </span>
                </div>
                <div
                  role="img"
                  aria-label={`${u.roomName} booked ${u.percent} percent of open hours`}
                  className="h-2.5 rounded-full bg-track overflow-hidden"
                >
                  <div
                    className="h-full rounded-full bg-primary"
                    style={{ width: `${Math.min(100, u.percent)}%` }}
                  />
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <div role="group" aria-label="Filter by status" className="flex flex-wrap gap-3">
        {STATUSES.map((s) => (
          <Chip
            key={s.value}
            pressed={status === s.value}
            onClick={() => {
              setStatus(s.value);
              setPage(0);
            }}
          >
            {s.label}
          </Chip>
        ))}
      </div>

      {bookings.isPending && (
        <div aria-busy="true" aria-label="Loading bookings" className="flex flex-col gap-3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      )}
      {bookings.isError && (
        <InlineError title="Could not load bookings">
          {(bookings.error as ApiError).message}
        </InlineError>
      )}
      {bookings.data && bookings.data.items.length === 0 && (
        <EmptyState icon={<ClipboardText size={24} aria-hidden="true" />} title="No bookings match">
          Try another status filter.
        </EmptyState>
      )}
      {bookings.data && bookings.data.items.length > 0 && (
        <>
          <ul className="m-0 p-0 list-none flex flex-col gap-3">
            {bookings.data.items.map((b) => (
              <li
                key={b.id}
                className="bg-card border border-line rounded-2xl p-4 md:p-6 flex flex-wrap items-center justify-between gap-4"
              >
                <div className="flex flex-col gap-1 min-w-0">
                  <span className="text-title font-semibold">{b.title}</span>
                  <span className="text-small text-muted">
                    {b.roomName}, {formatWhen(b.startsAt, b.endsAt, OFFICE_ZONE)}
                  </span>
                  <span className="text-small text-muted">
                    Organized by {b.organizer.name ?? b.organizer.email}
                  </span>
                </div>
                <StatusTag status={b.status} />
              </li>
            ))}
          </ul>
          <nav aria-label="Pages" className="flex items-center gap-4">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span className="text-small text-muted">
              Page {page + 1} of {pages}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= pages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </nav>
        </>
      )}
    </>
  );
}
