import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CalendarX, Clock } from '@phosphor-icons/react';
import { useAuth } from '../auth/AuthContext';
import { api, ApiError } from '../lib/api';
import type { Amenity, Booking, Hold, Room, SearchResult, Suggestion } from '../lib/types';
import {
  dateInZone,
  formatDay,
  formatRange,
  nextQuarterHour,
  OFFICE_ZONE,
  timeInZone,
  zonedToIso,
  zoneLabel,
} from '../lib/time';
import { SearchForm } from '../components/SearchForm';
import type { SearchValues } from '../components/SearchForm';
import { RoomCard } from '../components/RoomCard';
import { BookingPanel } from '../components/BookingPanel';
import { EmptyState, InlineError, PageHeader, Skeleton, Button } from '../components/ui';

function readParams(p: URLSearchParams): SearchValues {
  const d = nextQuarterHour();
  return {
    date: p.get('date') ?? d.date,
    start: p.get('start') ?? d.start,
    end: p.get('end') ?? d.end,
    people: Math.max(1, Number(p.get('people') ?? 4) || 4),
    amenities: (p.get('amenities')?.split(',').filter(Boolean) ?? []) as Amenity[],
  };
}

function toParams(v: SearchValues): Record<string, string> {
  const out: Record<string, string> = {
    date: v.date,
    start: v.start,
    end: v.end,
    people: String(v.people),
  };
  if (v.amenities.length) out.amenities = v.amenities.join(',');
  return out;
}

export function Finder() {
  const { user } = useAuth();
  const nav = useNavigate();
  const qc = useQueryClient();
  const [params, setParams] = useSearchParams();
  const values = useMemo(() => readParams(params), [params]);
  const zone = user?.timeZone ?? OFFICE_ZONE;
  const [formKey, setFormKey] = useState(0);
  const [selection, setSelection] = useState<{ room: Room; hold: Hold } | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const hasSearched = params.has('date');
  const query = useMemo(() => new URLSearchParams(toParams(values)).toString(), [values]);
  const results = useQuery({
    queryKey: ['available', query],
    queryFn: () => api<SearchResult>(`/rooms/available?${query}`),
    enabled: hasSearched,
  });

  const startIso = zonedToIso(values.date, values.start, OFFICE_ZONE);
  const endIso = zonedToIso(values.date, values.end, OFFICE_ZONE);

  const hold = useMutation({
    mutationFn: (room: Room) =>
      api<Hold>('/holds', {
        method: 'POST',
        body: { roomId: room.id, startsAt: startIso, endsAt: endIso },
      }).then((h) => ({ room, hold: h })),
    onSuccess: (sel) => {
      setNotice(null);
      setSelection(sel);
    },
    onError: () => {
      setSelection(null);
      void qc.invalidateQueries({ queryKey: ['available'] });
    },
  });

  const search = (v: SearchValues) => {
    setSelection(null);
    setNotice(null);
    setParams(toParams(v));
  };

  const applySuggestion = (s: Suggestion) => {
    const next: SearchValues = {
      ...values,
      date: dateInZone(s.startsAt, OFFICE_ZONE),
      start: timeInZone(s.startsAt, OFFICE_ZONE),
      end: timeInZone(s.endsAt, OFFICE_ZONE),
    };
    setFormKey((k) => k + 1);
    search(next);
  };

  const onExpired = useCallback(() => {
    setSelection(null);
    setNotice('Your hold on the room ran out after 5 minutes. Select the room again to hold it.');
    void qc.invalidateQueries({ queryKey: ['available'] });
  }, [qc]);

  const onBooked = (b: Booking[]) => {
    void qc.invalidateQueries({ queryKey: ['bookings'] });
    nav(`/bookings/${b[0].id}?created=1`);
  };

  const data = results.data;
  const holdError = hold.error instanceof ApiError ? hold.error : null;

  return (
    <>
      <PageHeader
        title="Find a room"
        subtitle="Tell us when and how many. We show only rooms that fit and are free."
      />
      <SearchForm
        key={formKey}
        initial={values}
        zoneLabel={zoneLabel(zone)}
        onSearch={search}
        busy={results.isFetching}
      />

      {!hasSearched && (
        <EmptyState icon={<Clock size={24} aria-hidden="true" />} title="Pick a time to see rooms">
          Choose the date, the start and end time and how many people are coming, then search.
        </EmptyState>
      )}

      {hasSearched && (
        <div className="flex flex-wrap gap-8 items-start">
          <section aria-label="Results" className="flex-[999_1_560px] min-w-0 flex flex-col gap-4">
            {results.isPending && (
              <div
                aria-busy="true"
                aria-label="Loading rooms"
                className="grid gap-4 grid-cols-[repeat(auto-fit,minmax(min(100%,300px),1fr))]"
              >
                {[0, 1, 2].map((i) => (
                  <div
                    key={i}
                    className="bg-card border border-line rounded-2xl p-6 flex flex-col gap-4"
                  >
                    <Skeleton className="h-7 w-32" />
                    <Skeleton className="h-5 w-20" />
                    <Skeleton className="h-6 w-full" />
                    <Skeleton className="h-3 w-full" />
                    <Skeleton className="h-12 w-full" />
                  </div>
                ))}
              </div>
            )}

            {results.isError && (
              <InlineError
                title="Could not search for rooms"
                action={
                  <Button
                    variant="secondary"
                    onClick={() => void results.refetch()}
                    className="self-start"
                  >
                    Try again
                  </Button>
                }
              >
                {(results.error as ApiError).message}
              </InlineError>
            )}

            {data && (
              <>
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <h2 className="m-0 text-title font-semibold">
                    {data.rooms.length} {data.rooms.length === 1 ? 'room' : 'rooms'} available,{' '}
                    {formatDay(startIso, zone)} {formatRange(startIso, endIso, zone)}
                  </h2>
                  <div className="text-small text-muted">Sorted by best fit</div>
                </div>

                {notice && <InlineError title="Hold ended">{notice}</InlineError>}
                {holdError && (
                  <InlineError title={holdError.title}>
                    {holdError.message} The list has been refreshed.
                  </InlineError>
                )}

                {data.rooms.length === 0 && data.nearby.length === 0 && (
                  <EmptyState
                    icon={<CalendarX size={24} aria-hidden="true" />}
                    title="No room fits that window"
                  >
                    Try a different day, fewer people or fewer equipment filters. Rooms are open
                    during office hours only.
                  </EmptyState>
                )}

                <div className="grid gap-4 grid-cols-[repeat(auto-fit,minmax(min(100%,300px),1fr))]">
                  {data.rooms.map((item, i) => (
                    <RoomCard
                      key={item.room.id}
                      item={item}
                      best={i === 0}
                      selected={selection?.room.id === item.room.id}
                      pending={hold.isPending}
                      window={{ start: startIso, end: endIso }}
                      zone={zone}
                      onSelect={() => hold.mutate(item.room)}
                    />
                  ))}
                  {data.nearby.length > 0 && (
                    <div className="bg-sand border border-dashed border-[#bdb4a4] rounded-2xl p-6 flex flex-col gap-3 justify-center">
                      <h3 className="m-0 text-body font-semibold">
                        {data.rooms.length === 0
                          ? 'Nothing is free then. Nearest options'
                          : 'Nearby option'}
                      </h3>
                      {data.nearby.map((s) => (
                        <div key={s.room.id} className="flex flex-col gap-1">
                          <div className="text-small font-semibold">
                            {s.room.name} is free {formatDay(s.startsAt, zone)},{' '}
                            {formatRange(s.startsAt, s.endsAt, zone)}
                          </div>
                          <div className="text-small text-muted">
                            {s.room.capacity} seats, floor {s.room.floor}.
                          </div>
                          <button
                            type="button"
                            onClick={() => applySuggestion(s)}
                            className="self-start min-h-11 text-small font-semibold text-primary underline hover:text-primary-hover"
                          >
                            Use this time
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </>
            )}
          </section>

          {selection ? (
            <div className="flex-[0_0_340px] max-w-full min-w-0 max-lg:flex-[1_1_100%]">
              <BookingPanel
                key={selection.hold.id}
                room={selection.room}
                hold={selection.hold}
                zone={zone}
                onBooked={onBooked}
                onExpired={onExpired}
                onPickAlternative={applySuggestion}
              />
            </div>
          ) : (
            data &&
            data.rooms.length > 0 && (
              <aside className="flex-[0_0_340px] max-w-full max-lg:hidden bg-card border border-dashed border-field rounded-2xl p-6 text-small text-muted">
                Select a room to hold it for 5 minutes and add the meeting details.
              </aside>
            )
          )}
        </div>
      )}
    </>
  );
}
