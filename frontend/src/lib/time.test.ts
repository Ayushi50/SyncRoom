import { dateInZone, formatRange, hoursInZone, timeInZone, zonedToIso } from './time';

describe('time helpers', () => {
  it('reads decimal hours in a zone', () => {
    expect(hoursInZone('2026-11-18T04:30:00Z', 'Asia/Kolkata')).toBe(10);
    expect(hoursInZone('2026-11-18T05:00:00Z', 'Asia/Kolkata')).toBe(10.5);
  });
  it('round trips a wall clock time through a zone', () => {
    const iso = zonedToIso('2026-11-18', '10:00', 'Asia/Kolkata');
    expect(iso).toBe('2026-11-18T04:30:00.000Z');
    expect(dateInZone(iso, 'Asia/Kolkata')).toBe('2026-11-18');
    expect(timeInZone(iso, 'Asia/Kolkata')).toBe('10:00');
  });
  it('formats a range with one meridiem when both ends share it', () => {
    expect(formatRange('2026-11-18T04:30:00Z', '2026-11-18T05:30:00Z', 'Asia/Kolkata')).toBe(
      '10:00 to 11:00 AM',
    );
    expect(formatRange('2026-11-18T06:00:00Z', '2026-11-18T07:00:00Z', 'Asia/Kolkata')).toBe(
      '11:30 AM to 12:30 PM',
    );
  });
});
