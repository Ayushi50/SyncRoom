import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { CalendarBlank, Repeat } from '@phosphor-icons/react';
import { api, ApiError } from '../lib/api';
import type { Booking } from '../lib/types';
import { useAuth } from '../auth/AuthContext';
import { formatWhen, OFFICE_ZONE } from '../lib/time';
import {
  Button,
  Chip,
  EmptyState,
  InlineError,
  LinkButton,
  PageHeader,
  Skeleton,
} from '../components/ui';
import { StatusTag } from '../components/BookingBits';

export function MyBookings() {
  const { user } = useAuth();
  const zone = user?.timeZone ?? OFFICE_ZONE;
  const [when, setWhen] = useState<'upcoming' | 'past'>('upcoming');
  const q = useQuery({
    queryKey: ['bookings', when],
    queryFn: () => api<Booking[]>(`/bookings/mine?when=${when}`),
  });

  return (
    <>
      <PageHeader
        title="My bookings"
        subtitle="Meetings you organized. Open one to move or cancel it."
        action={<LinkButton to="/rooms/find">Find a room</LinkButton>}
      />
      <div role="group" aria-label="Show" className="flex gap-3">
        <Chip pressed={when === 'upcoming'} onClick={() => setWhen('upcoming')}>
          Upcoming
        </Chip>
        <Chip pressed={when === 'past'} onClick={() => setWhen('past')}>
          Past
        </Chip>
      </div>

      {q.isPending && (
        <div aria-busy="true" aria-label="Loading bookings" className="flex flex-col gap-4">
          {[0, 1, 2].map((i) => (
            <div key={i} className="bg-card border border-line rounded-2xl p-6 flex flex-col gap-3">
              <Skeleton className="h-6 w-56" />
              <Skeleton className="h-5 w-72" />
            </div>
          ))}
        </div>
      )}
      {q.isError && (
        <InlineError
          title="Could not load your bookings"
          action={
            <Button variant="secondary" className="self-start" onClick={() => void q.refetch()}>
              Try again
            </Button>
          }
        >
          {(q.error as ApiError).message}
        </InlineError>
      )}
      {q.data && q.data.length === 0 && (
        <EmptyState
          icon={<CalendarBlank size={24} aria-hidden="true" />}
          title={when === 'upcoming' ? 'No upcoming bookings' : 'No past bookings yet'}
          action={
            when === 'upcoming' ? <LinkButton to="/rooms/find">Find a room</LinkButton> : undefined
          }
        >
          {when === 'upcoming'
            ? 'Search for a room to book your next meeting.'
            : 'Meetings you have organized will show up here once they are over.'}
        </EmptyState>
      )}
      {q.data && q.data.length > 0 && (
        <ul className="m-0 p-0 list-none flex flex-col gap-4">
          {q.data.map((b) => (
            <li
              key={b.id}
              className="bg-card border border-line rounded-2xl p-6 flex flex-wrap items-center justify-between gap-4"
            >
              <div className="flex flex-col gap-1 min-w-0">
                <div className="flex flex-wrap items-center gap-3">
                  <Link
                    to={`/bookings/${b.id}`}
                    className="text-title font-semibold text-ink no-underline hover:text-primary"
                  >
                    {b.title}
                  </Link>
                  <StatusTag status={b.status} />
                  {b.seriesId && (
                    <span className="inline-flex items-center gap-1 text-caption text-muted">
                      <Repeat size={14} aria-hidden="true" /> Repeats
                    </span>
                  )}
                </div>
                <div className="text-small text-muted">
                  {b.roomName}, floor {b.floor}. {formatWhen(b.startsAt, b.endsAt, zone)}
                </div>
                <div className="text-small text-muted">
                  {b.attendees.length === 0
                    ? 'Just you'
                    : `You and ${b.attendees.length} ${b.attendees.length === 1 ? 'guest' : 'guests'}`}
                </div>
              </div>
              {when === 'upcoming' && b.status === 'CONFIRMED' && (
                <div className="flex flex-wrap gap-2">
                  <LinkButton
                    variant="secondary"
                    to={`/bookings/${b.id}?action=reschedule`}
                    className="min-h-11"
                  >
                    Reschedule
                  </LinkButton>
                  <LinkButton
                    variant="ghost"
                    to={`/bookings/${b.id}?action=cancel`}
                    className="min-h-11"
                  >
                    Cancel
                  </LinkButton>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
