import { Users } from '@phosphor-icons/react';
import { amenityLabel } from '../lib/types';
import type { RoomAvailability } from '../lib/types';
import { AvailabilityBar } from './AvailabilityBar';
import { Button, cx, Tag } from './ui';

interface Props {
  item: RoomAvailability;
  best: boolean;
  selected: boolean;
  pending: boolean;
  window: { start: string; end: string };
  zone: string;
  onSelect: () => void;
}

export function RoomCard({ item, best, selected, pending, window, zone, onSelect }: Props) {
  const { room, busy } = item;
  return (
    <article
      aria-label={room.name}
      className={cx(
        'bg-card rounded-2xl p-6 flex flex-col gap-4',
        selected || best ? 'border-2 border-primary' : 'border border-line',
      )}
    >
      <div className="flex justify-between items-start gap-3">
        <div>
          <h3 className="m-0 text-title font-semibold">{room.name}</h3>
          <div className="text-small text-muted">Floor {room.floor}</div>
        </div>
        {best && <Tag tone="tint">Best fit</Tag>}
      </div>
      <div className="flex items-center gap-2 text-small text-body">
        <Users size={18} aria-hidden="true" />
        {room.capacity} seats
      </div>
      <div className="flex flex-wrap gap-2 min-h-6">
        {room.amenities.map((a) => (
          <Tag key={a}>{amenityLabel(a)}</Tag>
        ))}
      </div>
      <AvailabilityBar
        busy={busy}
        openTime={room.openTime}
        closeTime={room.closeTime}
        selected={{ start: window.start, end: window.end }}
        zone={zone}
        label="Availability on this day"
      />
      <Button
        variant={selected ? 'primary' : 'secondary'}
        aria-pressed={selected}
        disabled={pending}
        onClick={onSelect}
        aria-label={selected ? `${room.name} selected` : `Select ${room.name}`}
      >
        {selected ? 'Selected' : pending ? 'Holding' : 'Select room'}
      </Button>
    </article>
  );
}
