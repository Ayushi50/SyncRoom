export const OFFICE_ZONE = 'Asia/Kolkata';

const partsOf = (iso: string | Date, zone: string, opts: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat('en-GB', { timeZone: zone, ...opts }).formatToParts(
    typeof iso === 'string' ? new Date(iso) : iso,
  );

/** Decimal hours since midnight in a zone, e.g. 10:30 gives 10.5. */
export function hoursInZone(iso: string, zone: string): number {
  const p = partsOf(iso, zone, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  const h = Number(p.find((x) => x.type === 'hour')?.value ?? 0);
  const m = Number(p.find((x) => x.type === 'minute')?.value ?? 0);
  return h + m / 60;
}

export function timeToHours(t: string): number {
  const [h, m] = t.split(':').map(Number);
  return h + (m ?? 0) / 60;
}

export const zoneLabel = (zone: string): string =>
  new Intl.DateTimeFormat('en-IN', { timeZone: zone, timeZoneName: 'short' })
    .formatToParts(new Date())
    .find((p) => p.type === 'timeZoneName')?.value ?? zone;

const clock = (iso: string, zone: string, withMeridiem = true) =>
  new Intl.DateTimeFormat('en-US', {
    timeZone: zone,
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  })
    .format(new Date(iso))
    .replace(withMeridiem ? '' : /\s?[AP]M/, '');

export const formatDay = (iso: string, zone: string) =>
  new Intl.DateTimeFormat('en-GB', {
    timeZone: zone,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  }).format(new Date(iso));

export const formatDayLong = (iso: string, zone: string) =>
  new Intl.DateTimeFormat('en-GB', {
    timeZone: zone,
    weekday: 'long',
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  }).format(new Date(iso));

export function formatRange(startIso: string, endIso: string, zone: string): string {
  const sameHalf = clock(startIso, zone).slice(-2) === clock(endIso, zone).slice(-2);
  const start = sameHalf ? clock(startIso, zone, false) : clock(startIso, zone);
  return `${start} to ${clock(endIso, zone)}`;
}

export const formatWhen = (startIso: string, endIso: string, zone: string) =>
  `${formatDay(startIso, zone)}, ${formatRange(startIso, endIso, zone)}`;

/** Local date string yyyy-mm-dd of an instant in a zone. */
export function dateInZone(iso: string | Date, zone: string): string {
  const p = partsOf(iso, zone, { year: 'numeric', month: '2-digit', day: '2-digit' });
  const get = (t: string) => p.find((x) => x.type === t)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}`;
}

export function timeInZone(iso: string, zone: string): string {
  const p = partsOf(iso, zone, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
  return `${p.find((x) => x.type === 'hour')?.value}:${p.find((x) => x.type === 'minute')?.value}`;
}

/** Converts a wall clock date and time in a zone to an ISO instant. */
export function zonedToIso(date: string, time: string, zone: string): string {
  const guess = new Date(`${date}T${time}:00Z`);
  const asZone = new Date(guess.toLocaleString('en-US', { timeZone: zone }));
  const asUtc = new Date(guess.toLocaleString('en-US', { timeZone: 'UTC' }));
  return new Date(guess.getTime() - (asZone.getTime() - asUtc.getTime())).toISOString();
}

export const nextQuarterHour = (): { date: string; start: string; end: string } => {
  const now = new Date(Date.now() + 15 * 60_000);
  const date = dateInZone(now, OFFICE_ZONE);
  let h = hoursInZone(now.toISOString(), OFFICE_ZONE);
  h = Math.ceil(h * 4) / 4;
  let start = Math.min(Math.max(h, 9), 17);
  let day = date;
  if (h >= 17.5) {
    const tomorrow = new Date(now.getTime() + 86_400_000);
    day = dateInZone(tomorrow, OFFICE_ZONE);
    start = 10;
  }
  const fmt = (x: number) =>
    `${String(Math.floor(x)).padStart(2, '0')}:${String(Math.round((x % 1) * 60)).padStart(2, '0')}`;
  return { date: day, start: fmt(start), end: fmt(start + 1) };
};
