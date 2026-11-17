import { useEffect, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation } from '@tanstack/react-query';
import { api, ApiError } from '../lib/api';
import type { Booking, Hold, Room, SeriesPattern, Suggestion } from '../lib/types';
import { formatWhen } from '../lib/time';
import { Button, Field, InlineError, Input, Select } from './ui';

const EMAIL = z.string().email();

export function parseInvitees(raw: string): { emails: string[]; invalid: string[] } {
  const parts = raw
    .split(/[\s,;]+/)
    .map((p) => p.trim().toLowerCase())
    .filter(Boolean);
  const emails: string[] = [];
  const invalid: string[] = [];
  for (const p of parts) {
    if (!EMAIL.safeParse(p).success) invalid.push(p);
    else if (!emails.includes(p)) emails.push(p);
  }
  return { emails, invalid };
}

export const REPEATS: { value: '' | SeriesPattern; label: string }[] = [
  { value: '', label: 'Does not repeat' },
  { value: 'DAILY', label: 'Every day' },
  { value: 'WEEKDAYS', label: 'Every weekday' },
  { value: 'WEEKLY', label: 'Every week' },
];

const schema = z.object({
  title: z
    .string()
    .trim()
    .min(1, 'Give the meeting a title')
    .max(200, 'Use 200 characters or fewer'),
  invitees: z.string(),
  repeat: z.string(),
  count: z.coerce.number().int().min(2).max(12),
});
type Shape = z.input<typeof schema>;

interface Props {
  room: Room;
  hold: Hold;
  zone: string;
  onBooked: (b: Booking[]) => void;
  onExpired: () => void;
  onPickAlternative: (s: Suggestion) => void;
}

export function useCountdown(expiresAt: string) {
  const [left, setLeft] = useState(() => Math.max(0, new Date(expiresAt).getTime() - Date.now()));
  useEffect(() => {
    const tick = () => setLeft(Math.max(0, new Date(expiresAt).getTime() - Date.now()));
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, [expiresAt]);
  return Math.ceil(left / 1000);
}

export function BookingPanel({ room, hold, zone, onBooked, onExpired, onPickAlternative }: Props) {
  const seconds = useCountdown(hold.expiresAt);
  const expired = seconds <= 0;
  useEffect(() => {
    if (expired) onExpired();
  }, [expired, onExpired]);

  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<Shape>({
    resolver: zodResolver(schema),
    defaultValues: { title: '', invitees: '', repeat: '', count: 4 },
  });
  const repeat = useWatch({ control, name: 'repeat' });

  const book = useMutation({
    mutationFn: (v: Shape) => {
      const { emails } = parseInvitees(v.invitees);
      return api<Booking[]>('/bookings', {
        method: 'POST',
        body: {
          roomId: room.id,
          title: v.title,
          startsAt: hold.startsAt,
          endsAt: hold.endsAt,
          holdId: hold.id,
          attendees: emails.map((email) => ({ email })),
          repeat: v.repeat ? { pattern: v.repeat, count: Number(v.count) } : undefined,
        },
      });
    },
    onSuccess: onBooked,
  });

  const submit = handleSubmit((v) => {
    const { emails, invalid } = parseInvitees(v.invitees);
    if (invalid.length) {
      setError('invitees', {
        message: `These do not look like email addresses: ${invalid.join(', ')}`,
      });
      return;
    }
    if (emails.length + 1 > room.capacity) {
      setError('invitees', {
        message: `${room.name} seats ${room.capacity}, including you. Remove some people or pick a larger room.`,
      });
      return;
    }
    book.mutate(v);
  });

  const err = book.error instanceof ApiError ? book.error : null;
  const alternatives = (err?.extras.alternatives as Suggestion[] | undefined) ?? [];
  const mm = String(Math.floor(seconds / 60));
  const ss = String(seconds % 60).padStart(2, '0');

  return (
    <aside
      aria-label="Your booking"
      className="box-border bg-card border border-line rounded-2xl p-6 flex flex-col gap-4 lg:sticky lg:top-6"
    >
      <div>
        <div className="text-caption font-semibold text-muted uppercase tracking-wider">
          Your booking
        </div>
        <div className="mt-1 text-title font-semibold">{room.name}</div>
        <div className="text-small text-muted">{formatWhen(hold.startsAt, hold.endsAt, zone)}</div>
        <div className="mt-2 text-caption text-muted" role="timer" aria-live="off">
          {expired ? 'Hold expired' : `Held for you for ${mm}:${ss}`}
        </div>
      </div>
      <form noValidate onSubmit={submit} className="flex flex-col gap-4">
        <Field label="Meeting title" error={errors.title?.message}>
          <Input
            aria-invalid={!!errors.title}
            placeholder="Backend design review"
            {...register('title')}
          />
        </Field>
        <Field
          label="Invite people"
          error={errors.invitees?.message}
          hint="Separate email addresses with commas or spaces."
        >
          <Input
            aria-invalid={!!errors.invitees}
            placeholder="priya@example.com, arjun@example.com"
            {...register('invitees')}
          />
        </Field>
        <Field label="Repeat">
          <Select {...register('repeat')}>
            {REPEATS.map((r) => (
              <option key={r.value} value={r.value}>
                {r.label}
              </option>
            ))}
          </Select>
        </Field>
        {repeat && (
          <Field label="Number of meetings" hint="Up to 12. If one date is taken, none are booked.">
            <Select {...register('count')}>
              {Array.from({ length: 11 }, (_, i) => i + 2).map((n) => (
                <option key={n} value={n}>
                  {n} meetings
                </option>
              ))}
            </Select>
          </Field>
        )}
        {room.checkInRequired && (
          <p className="m-0 text-small text-muted">
            This room is released if nobody checks in within 10 minutes of the start.
          </p>
        )}
        {err && (
          <InlineError title={err.title}>
            {err.message}
            {alternatives.length > 0 && (
              <ul className="m-0 mt-2 p-0 list-none flex flex-col gap-2">
                {alternatives.slice(0, 3).map((s) => (
                  <li key={s.room.id + s.startsAt}>
                    <button
                      type="button"
                      onClick={() => onPickAlternative(s)}
                      className="min-h-11 text-left text-small font-semibold text-primary underline"
                    >
                      {s.room.name}, {formatWhen(s.startsAt, s.endsAt, zone)}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </InlineError>
        )}
        <Button type="submit" className="min-h-[52px]" disabled={book.isPending || expired}>
          {book.isPending ? 'Confirming' : 'Confirm booking'}
        </Button>
      </form>
    </aside>
  );
}
