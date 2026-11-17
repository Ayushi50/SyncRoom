import { useState } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle } from '@phosphor-icons/react';
import { api, ApiError } from '../lib/api';
import type { Booking, Scope } from '../lib/types';
import { useAuth } from '../auth/AuthContext';
import {
  dateInZone,
  formatDayLong,
  formatRange,
  OFFICE_ZONE,
  timeInZone,
  zonedToIso,
} from '../lib/time';
import {
  Button,
  EmptyState,
  Field,
  InlineError,
  Input,
  LinkButton,
  PageHeader,
  Skeleton,
} from '../components/ui';
import { StatusTag } from '../components/BookingBits';
import { MagnifyingGlass } from '@phosphor-icons/react';

function ScopePicker({
  value,
  onChange,
  name,
}: {
  value: Scope;
  onChange: (s: Scope) => void;
  name: string;
}) {
  return (
    <fieldset className="m-0 p-0 border-0 flex flex-col gap-2">
      <legend className="text-small font-semibold text-body mb-2">Apply to</legend>
      {(['THIS', 'FOLLOWING'] as Scope[]).map((s) => (
        <label key={s} className="flex items-center gap-3 min-h-11 text-body">
          <input
            type="radio"
            name={name}
            checked={value === s}
            onChange={() => onChange(s)}
            className="size-5 accent-primary"
          />
          {s === 'THIS' ? 'Only this meeting' : 'This and following meetings'}
        </label>
      ))}
    </fieldset>
  );
}

export function BookingDetail() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const { user } = useAuth();
  const zone = user?.timeZone ?? OFFICE_ZONE;
  const qc = useQueryClient();
  const [mode, setMode] = useState<'reschedule' | 'cancel' | null>(
    params.get('action') === 'reschedule'
      ? 'reschedule'
      : params.get('action') === 'cancel'
        ? 'cancel'
        : null,
  );
  const [scope, setScope] = useState<Scope>('THIS');
  const [now] = useState(() => Date.now());
  const [date, setDate] = useState('');
  const [start, setStart] = useState('');
  const [end, setEnd] = useState('');

  const q = useQuery({ queryKey: ['booking', id], queryFn: () => api<Booking>(`/bookings/${id}`) });
  const b = q.data;

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['booking', id] });
    void qc.invalidateQueries({ queryKey: ['bookings'] });
  };
  const move = useMutation({
    mutationFn: (w: { date: string; start: string; end: string }) =>
      api<Booking>(`/bookings/${id}`, {
        method: 'PATCH',
        body: {
          startsAt: zonedToIso(w.date, w.start, OFFICE_ZONE),
          endsAt: zonedToIso(w.date, w.end, OFFICE_ZONE),
          scope,
          version: b?.version,
        },
      }),
    onSuccess: () => {
      setMode(null);
      refresh();
    },
  });
  const cancel = useMutation({
    mutationFn: () => api<void>(`/bookings/${id}?scope=${scope}`, { method: 'DELETE' }),
    onSuccess: () => {
      setMode(null);
      refresh();
    },
  });
  const checkIn = useMutation({
    mutationFn: () => api<Booking>(`/bookings/${id}/check-in`, { method: 'POST' }),
    onSuccess: refresh,
  });

  if (q.isPending) {
    return (
      <div aria-busy="true" aria-label="Loading booking" className="flex flex-col gap-4 max-w-2xl">
        <Skeleton className="h-10 w-80" />
        <Skeleton className="h-48 w-full" />
      </div>
    );
  }
  if (q.isError || !b) {
    const notFound = q.error instanceof ApiError && q.error.status === 404;
    return (
      <EmptyState
        icon={<MagnifyingGlass size={24} aria-hidden="true" />}
        title={notFound ? 'Booking not found' : 'Could not load this booking'}
        action={<LinkButton to="/bookings">Back to my bookings</LinkButton>}
      >
        {notFound
          ? 'It may have been removed, or it belongs to someone else.'
          : (q.error as ApiError)?.message}
      </EmptyState>
    );
  }

  const active = b.status === 'CONFIRMED' && new Date(b.endsAt).getTime() > now;
  const canCheckIn =
    active &&
    b.checkInRequired &&
    !b.checkedInAt &&
    now >= new Date(b.startsAt).getTime() - 15 * 60_000;
  const openReschedule = () => {
    setDate(dateInZone(b.startsAt, OFFICE_ZONE));
    setStart(timeInZone(b.startsAt, OFFICE_ZONE));
    setEnd(timeInZone(b.endsAt, OFFICE_ZONE));
    setMode('reschedule');
  };
  const dateV = date || dateInZone(b.startsAt, OFFICE_ZONE);
  const startV = start || timeInZone(b.startsAt, OFFICE_ZONE);
  const endV = end || timeInZone(b.endsAt, OFFICE_ZONE);

  const rows: [string, string][] = [
    ['Room', `${b.roomName}, floor ${b.floor}`],
    ['When', `${formatDayLong(b.startsAt, zone)}, ${formatRange(b.startsAt, b.endsAt, zone)}`],
    ['Organizer', `${b.organizer.name ?? ''} ${b.organizer.email}`.trim()],
    [
      'Guests',
      b.attendees.length ? b.attendees.map((a) => a.email).join(', ') : 'No guests invited',
    ],
  ];
  if (b.repeatPattern)
    rows.push(['Repeats', `${b.repeatPattern.toLowerCase()} for ${b.repeatCount} meetings`]);
  if (b.checkedInAt)
    rows.push(['Checked in', formatRange(b.checkedInAt, b.checkedInAt, zone).split(' to ')[0]]);

  return (
    <>
      {params.get('created') === '1' && (
        <div
          role="status"
          className="flex items-center gap-3 p-4 rounded-xl bg-tint text-primary-hover font-semibold"
        >
          <CheckCircle size={24} aria-hidden="true" />
          Booked. A confirmation email with a calendar invite is on its way.
        </div>
      )}
      <PageHeader title={b.title} action={<StatusTag status={b.status} />} />
      <section
        aria-label="Booking details"
        className="max-w-2xl bg-card border border-line rounded-2xl p-6"
      >
        <dl className="m-0 grid gap-4 grid-cols-[minmax(96px,160px)_1fr]">
          {rows.map(([k, v]) => (
            <div key={k} className="contents">
              <dt className="text-small text-muted">{k}</dt>
              <dd className="m-0 text-body break-words">{v}</dd>
            </div>
          ))}
        </dl>
      </section>

      {checkIn.isError && (
        <InlineError title="Check in failed">{(checkIn.error as ApiError).message}</InlineError>
      )}
      {active && mode === null && (
        <div className="flex flex-wrap gap-3">
          {canCheckIn && (
            <Button onClick={() => checkIn.mutate()} disabled={checkIn.isPending}>
              Check in
            </Button>
          )}
          <Button variant="secondary" onClick={openReschedule}>
            Reschedule
          </Button>
          <Button variant="ghost" onClick={() => setMode('cancel')}>
            Cancel booking
          </Button>
        </div>
      )}

      {mode === 'reschedule' && (
        <form
          noValidate
          aria-label="Reschedule booking"
          className="max-w-2xl bg-card border border-line rounded-2xl p-6 flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            move.mutate({ date: dateV, start: startV, end: endV });
          }}
        >
          <h2 className="m-0 text-title font-semibold">Pick a new time</h2>
          <div className="flex flex-wrap gap-4">
            <Field label="Date" className="flex-[1.4_1_180px]">
              <Input type="date" value={dateV} onChange={(e) => setDate(e.target.value)} />
            </Field>
            <Field label="From" className="flex-[1_1_120px]">
              <Input
                type="time"
                step={900}
                value={startV}
                onChange={(e) => setStart(e.target.value)}
              />
            </Field>
            <Field label="To" className="flex-[1_1_120px]">
              <Input type="time" step={900} value={endV} onChange={(e) => setEnd(e.target.value)} />
            </Field>
          </div>
          {b.seriesId && <ScopePicker name="move-scope" value={scope} onChange={setScope} />}
          {move.isError && (
            <InlineError title={(move.error as ApiError).title}>
              {(move.error as ApiError).message}
            </InlineError>
          )}
          <div className="flex flex-wrap gap-3">
            <Button type="submit" disabled={move.isPending}>
              {move.isPending ? 'Saving' : 'Save new time'}
            </Button>
            <Button variant="ghost" onClick={() => setMode(null)}>
              Keep current time
            </Button>
          </div>
        </form>
      )}

      {mode === 'cancel' && (
        <section
          aria-label="Cancel booking"
          className="max-w-2xl bg-card border border-line rounded-2xl p-6 flex flex-col gap-4"
        >
          <h2 className="m-0 text-title font-semibold">Cancel this booking?</h2>
          <p className="m-0 text-body text-muted">
            Everyone invited gets a cancellation email and the room is free again.
          </p>
          {b.seriesId && <ScopePicker name="cancel-scope" value={scope} onChange={setScope} />}
          {cancel.isError && (
            <InlineError title="Could not cancel">{(cancel.error as ApiError).message}</InlineError>
          )}
          <div className="flex flex-wrap gap-3">
            <Button variant="danger" onClick={() => cancel.mutate()} disabled={cancel.isPending}>
              {cancel.isPending ? 'Cancelling' : 'Cancel booking'}
            </Button>
            <Button variant="ghost" onClick={() => setMode(null)}>
              Keep booking
            </Button>
          </div>
        </section>
      )}
    </>
  );
}
