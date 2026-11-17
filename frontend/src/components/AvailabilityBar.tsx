import type { Segment } from '../lib/types';
import { formatRange, hoursInZone, timeToHours } from '../lib/time';

interface Props {
  busy: Segment[];
  openTime: string;
  closeTime: string;
  selected?: Segment;
  zone: string;
  label?: string;
  compact?: boolean;
}

const hourLabel = (h: number) => {
  const hr = Math.floor(h);
  const suffix = hr >= 12 ? 'PM' : 'AM';
  return `${hr % 12 === 0 ? 12 : hr % 12} ${suffix}`;
};

/** A day bar for one room: grey where it is busy, a teal outline where the search window sits. */
export function AvailabilityBar({
  busy,
  openTime,
  closeTime,
  selected,
  zone,
  label = "Today's availability",
  compact,
}: Props) {
  const open = timeToHours(openTime);
  const close = timeToHours(closeTime);
  const span = close - open;
  const pct = (iso: string) =>
    Math.min(100, Math.max(0, ((hoursInZone(iso, zone) - open) / span) * 100));
  const seg = (s: Segment) => {
    const left = pct(s.start);
    const endH = hoursInZone(s.end, zone);
    const right = endH === 0 ? 100 : pct(s.end);
    return { left, width: Math.max(1, right - left) };
  };
  const mid = open + span / 2;
  const description = busy.length
    ? `Busy ${busy.map((b) => formatRange(b.start, b.end, zone)).join(', ')}`
    : 'Free all day';
  return (
    <div>
      {!compact && <div className="mb-2 text-caption text-muted">{label}</div>}
      <div
        role="img"
        aria-label={`${label}. ${description}`}
        className="relative h-2.5 rounded-full bg-track"
        data-testid="availability-bar"
      >
        {busy.map((b, i) => {
          const { left, width } = seg(b);
          return (
            <div
              key={i}
              data-testid="busy-segment"
              className="absolute top-0 bottom-0 rounded-full bg-busy"
              style={{ left: `${left}%`, width: `${width}%` }}
            />
          );
        })}
        {selected && (
          <div
            data-testid="selected-window"
            className="absolute -top-0.5 -bottom-0.5 box-border rounded-lg border-2 border-primary"
            style={{ left: `${seg(selected).left}%`, width: `${seg(selected).width}%` }}
          />
        )}
      </div>
      {!compact && (
        <div className="flex justify-between mt-2 text-caption text-muted" aria-hidden="true">
          <span>{hourLabel(open)}</span>
          <span>{hourLabel(mid)}</span>
          <span>{hourLabel(close)}</span>
        </div>
      )}
    </div>
  );
}
