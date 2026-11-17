import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SearchForm } from './SearchForm';

const initial = { date: '2026-11-18', start: '10:00', end: '11:00', people: 4, amenities: [] };

describe('SearchForm', () => {
  it('submits the values with the chosen equipment', async () => {
    const onSearch = vi.fn();
    render(<SearchForm initial={initial} zoneLabel="IST" onSearch={onSearch} />);
    await userEvent.click(screen.getByRole('button', { name: 'Video call' }));
    await userEvent.clear(screen.getByLabelText('People'));
    await userEvent.type(screen.getByLabelText('People'), '6');
    await userEvent.click(screen.getByRole('button', { name: /Search/ }));
    expect(onSearch).toHaveBeenCalledWith({
      date: '2026-11-18',
      start: '10:00',
      end: '11:00',
      people: 6,
      amenities: ['VIDEO_CALL'],
    });
  });

  it('marks chips as pressed and lets them be toggled off', async () => {
    render(<SearchForm initial={initial} zoneLabel="IST" onSearch={vi.fn()} />);
    const chip = screen.getByRole('button', { name: 'Projector' });
    expect(chip).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(chip);
    expect(chip).toHaveAttribute('aria-pressed', 'true');
    await userEvent.click(chip);
    expect(chip).toHaveAttribute('aria-pressed', 'false');
  });

  it('refuses an end time before the start and says why', async () => {
    const onSearch = vi.fn();
    render(
      <SearchForm initial={{ ...initial, end: '09:00' }} zoneLabel="IST" onSearch={onSearch} />,
    );
    await userEvent.click(screen.getByRole('button', { name: /Search/ }));
    expect(await screen.findByText('End must be after the start')).toBeInTheDocument();
    expect(onSearch).not.toHaveBeenCalled();
  });

  it('rejects zero people', async () => {
    const onSearch = vi.fn();
    render(<SearchForm initial={{ ...initial, people: 0 }} zoneLabel="IST" onSearch={onSearch} />);
    await userEvent.click(screen.getByRole('button', { name: /Search/ }));
    expect(await screen.findByText('At least 1 person')).toBeInTheDocument();
    expect(onSearch).not.toHaveBeenCalled();
  });

  it('states which time zone the times are in', () => {
    render(<SearchForm initial={initial} zoneLabel="IST" onSearch={vi.fn()} />);
    expect(screen.getByText('Times are in IST')).toBeInTheDocument();
  });
});
