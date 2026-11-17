import { render, screen } from '@testing-library/react';
import { AvailabilityBar } from './AvailabilityBar';

const Z = 'Asia/Kolkata';
// 09:00 to 18:00 IST. 10:00 IST is 04:30Z.
const at = (h: string) => `2026-11-18T${h}:00Z`;

describe('AvailabilityBar', () => {
  it('positions busy segments across the open hours', () => {
    render(
      <AvailabilityBar
        busy={[{ start: at('03:30'), end: at('04:30') }]}
        openTime="09:00:00"
        closeTime="18:00:00"
        zone={Z}
      />,
    );
    const seg = screen.getByTestId('busy-segment');
    expect(parseFloat(seg.style.left)).toBeCloseTo(0, 1);
    expect(parseFloat(seg.style.width)).toBeCloseTo(100 / 9, 1);
  });

  it('draws the search window outline', () => {
    render(
      <AvailabilityBar
        busy={[]}
        selected={{ start: at('04:30'), end: at('05:30') }}
        openTime="09:00"
        closeTime="18:00"
        zone={Z}
      />,
    );
    const w = screen.getByTestId('selected-window');
    expect(parseFloat(w.style.left)).toBeCloseTo(100 / 9, 1);
  });

  it('describes the day for screen readers', () => {
    const { rerender } = render(
      <AvailabilityBar busy={[]} openTime="09:00" closeTime="18:00" zone={Z} />,
    );
    expect(screen.getByRole('img')).toHaveAccessibleName(/Free all day/);
    rerender(
      <AvailabilityBar
        busy={[{ start: at('04:30'), end: at('05:30') }]}
        openTime="09:00"
        closeTime="18:00"
        zone={Z}
      />,
    );
    expect(screen.getByRole('img')).toHaveAccessibleName(/Busy 10:00 to 11:00 AM/);
  });
});
