import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BookingPanel, parseInvitees } from './BookingPanel';
import type { Hold, Room } from '../lib/types';

const room: Room = {
  id: 'r1',
  name: 'Godavari',
  floor: 3,
  capacity: 3,
  amenities: ['VIDEO_CALL'],
  openTime: '09:00:00',
  closeTime: '18:00:00',
  checkInRequired: true,
  active: true,
};
const hold = (): Hold => ({
  id: 'h1',
  roomId: 'r1',
  startsAt: '2026-11-18T04:30:00Z',
  endsAt: '2026-11-18T05:30:00Z',
  expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
});

function setup(h: Hold = hold()) {
  const props = { onBooked: vi.fn(), onExpired: vi.fn(), onPickAlternative: vi.fn() };
  render(
    <QueryClientProvider client={new QueryClient()}>
      <BookingPanel room={room} hold={h} zone="Asia/Kolkata" {...props} />
    </QueryClientProvider>,
  );
  return props;
}

afterEach(() => vi.restoreAllMocks());

describe('parseInvitees', () => {
  it('splits on commas and spaces, lowercases and de duplicates', () => {
    expect(parseInvitees('A@x.com, b@x.com  a@x.com;c@x.com')).toEqual({
      emails: ['a@x.com', 'b@x.com', 'c@x.com'],
      invalid: [],
    });
  });
  it('reports what is not an email', () => {
    expect(parseInvitees('ok@x.com, nope').invalid).toEqual(['nope']);
  });
});

describe('BookingPanel', () => {
  it('shows the room, the time in the user zone and the hold countdown', () => {
    setup();
    expect(screen.getByText('Godavari')).toBeInTheDocument();
    expect(screen.getByText(/Wed 18 Nov, 10:00 to 11:00 AM/)).toBeInTheDocument();
    expect(screen.getByRole('timer')).toHaveTextContent(/Held for you for [45]:\d\d/);
  });

  it('requires a title', async () => {
    setup();
    await userEvent.click(screen.getByRole('button', { name: 'Confirm booking' }));
    expect(await screen.findByText('Give the meeting a title')).toBeInTheDocument();
  });

  it('flags invalid invitee addresses without calling the API', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    setup();
    await userEvent.type(screen.getByLabelText('Meeting title'), 'Sync');
    await userEvent.type(screen.getByLabelText(/Invite people/), 'not-an-email');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm booking' }));
    expect(
      await screen.findByText(/do not look like email addresses: not-an-email/),
    ).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('blocks more people than the room seats', async () => {
    setup();
    await userEvent.type(screen.getByLabelText('Meeting title'), 'Sync');
    await userEvent.type(screen.getByLabelText(/Invite people/), 'a@x.com b@x.com c@x.com');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm booking' }));
    expect(await screen.findByText(/seats 3, including you/)).toBeInTheDocument();
  });

  it('posts the booking with the hold and reports success', async () => {
    const booking = [{ id: 'b1' }];
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(booking), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    const props = setup();
    await userEvent.type(screen.getByLabelText('Meeting title'), 'Sync');
    await userEvent.type(screen.getByLabelText(/Invite people/), 'a@x.com');
    await userEvent.selectOptions(screen.getByLabelText('Repeat'), 'WEEKLY');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm booking' }));
    await waitFor(() => expect(props.onBooked).toHaveBeenCalled());
    const body = JSON.parse((fetchSpy.mock.calls[0][1] as RequestInit).body as string);
    expect(body).toMatchObject({
      roomId: 'r1',
      holdId: 'h1',
      title: 'Sync',
      attendees: [{ email: 'a@x.com' }],
      repeat: { pattern: 'WEEKLY', count: 4 },
    });
  });

  it('shows what failed and offers nearby alternatives on a conflict', async () => {
    const alt = {
      room: { ...room, id: 'r2', name: 'Ganga' },
      startsAt: '2026-11-18T06:30:00Z',
      endsAt: '2026-11-18T07:30:00Z',
    };
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          title: 'Time slot taken',
          detail: 'This room is already booked for that time.',
          alternatives: [alt],
        }),
        { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
      ),
    );
    const props = setup();
    await userEvent.type(screen.getByLabelText('Meeting title'), 'Sync');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm booking' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Time slot taken');
    await userEvent.click(
      screen.getByRole('button', { name: /Ganga, Wed 18 Nov, 12:00 to 1:00 PM/ }),
    );
    expect(props.onPickAlternative).toHaveBeenCalledWith(alt);
  });

  it('disables confirming and tells the parent when the hold has expired', () => {
    const props = setup({ ...hold(), expiresAt: new Date(Date.now() - 1000).toISOString() });
    expect(screen.getByRole('button', { name: 'Confirm booking' })).toBeDisabled();
    expect(props.onExpired).toHaveBeenCalled();
  });
});
