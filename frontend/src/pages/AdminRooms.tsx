import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { DoorOpen, Plus } from '@phosphor-icons/react';
import { api, ApiError } from '../lib/api';
import { AMENITIES, amenityLabel } from '../lib/types';
import type { Amenity, Room } from '../lib/types';
import {
  Button,
  Chip,
  EmptyState,
  Field,
  InlineError,
  Input,
  PageHeader,
  Skeleton,
  Tag,
} from '../components/ui';

const schema = z
  .object({
    name: z.string().trim().min(1, 'Enter a room name').max(120),
    floor: z.coerce.number().int().min(0, 'Floor cannot be negative').max(200),
    capacity: z.coerce.number().int().min(1, 'At least 1 seat').max(500),
    openTime: z.string().min(1, 'Set an open time'),
    closeTime: z.string().min(1, 'Set a close time'),
    checkInRequired: z.boolean(),
  })
  .refine((v) => v.closeTime > v.openTime, {
    path: ['closeTime'],
    message: 'Close time must be after open time',
  });
type Shape = z.input<typeof schema>;

function RoomForm({ room, onDone }: { room: Room | null; onDone: () => void }) {
  const qc = useQueryClient();
  const [amenities, setAmenities] = useState<Amenity[]>(room?.amenities ?? []);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Shape>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: room?.name ?? '',
      floor: room?.floor ?? 1,
      capacity: room?.capacity ?? 4,
      openTime: room?.openTime.slice(0, 5) ?? '09:00',
      closeTime: room?.closeTime.slice(0, 5) ?? '18:00',
      checkInRequired: room?.checkInRequired ?? false,
    },
  });
  const save = useMutation({
    mutationFn: (v: z.output<typeof schema>) =>
      api<Room>(room ? `/rooms/${room.id}` : '/rooms', {
        method: room ? 'PUT' : 'POST',
        body: { ...v, amenities },
      }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin-rooms'] });
      onDone();
    },
  });
  return (
    <form
      noValidate
      aria-label={room ? `Edit ${room.name}` : 'Add a room'}
      onSubmit={handleSubmit((v) => save.mutate(schema.parse(v)))}
      className="bg-card border border-line rounded-2xl p-6 flex flex-col gap-4"
    >
      <h2 className="m-0 text-title font-semibold">{room ? `Edit ${room.name}` : 'Add a room'}</h2>
      <div className="flex flex-wrap gap-4">
        <Field label="Name" error={errors.name?.message} className="flex-[2_1_200px]">
          <Input aria-invalid={!!errors.name} {...register('name')} />
        </Field>
        <Field label="Floor" error={errors.floor?.message} className="flex-[1_1_100px]">
          <Input type="number" aria-invalid={!!errors.floor} {...register('floor')} />
        </Field>
        <Field label="Seats" error={errors.capacity?.message} className="flex-[1_1_100px]">
          <Input type="number" aria-invalid={!!errors.capacity} {...register('capacity')} />
        </Field>
        <Field label="Opens" error={errors.openTime?.message} className="flex-[1_1_120px]">
          <Input type="time" aria-invalid={!!errors.openTime} {...register('openTime')} />
        </Field>
        <Field label="Closes" error={errors.closeTime?.message} className="flex-[1_1_120px]">
          <Input type="time" aria-invalid={!!errors.closeTime} {...register('closeTime')} />
        </Field>
      </div>
      <div role="group" aria-label="Equipment" className="flex flex-wrap items-center gap-3">
        <span className="text-small font-semibold text-body mr-1">Equipment</span>
        {AMENITIES.map((a) => (
          <Chip
            key={a.value}
            pressed={amenities.includes(a.value)}
            onClick={() =>
              setAmenities((c) =>
                c.includes(a.value) ? c.filter((x) => x !== a.value) : [...c, a.value],
              )
            }
          >
            {a.label}
          </Chip>
        ))}
      </div>
      <label className="flex items-center gap-3 min-h-11 text-body">
        <input type="checkbox" className="size-5 accent-primary" {...register('checkInRequired')} />
        Release the room if nobody checks in within 10 minutes
      </label>
      {save.isError && (
        <InlineError title="Could not save the room">
          {(save.error as ApiError).message}
        </InlineError>
      )}
      <div className="flex flex-wrap gap-3">
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Saving' : 'Save room'}
        </Button>
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

export function AdminRooms() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<Room | 'new' | null>(null);
  const q = useQuery({
    queryKey: ['admin-rooms'],
    queryFn: () => api<Room[]>('/rooms?includeInactive=true'),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api<void>(`/rooms/${id}`, { method: 'DELETE' }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['admin-rooms'] }),
  });

  return (
    <>
      <PageHeader
        title="Manage rooms"
        subtitle="Add rooms, change seats and equipment, or take a room out of service."
        action={
          <Button onClick={() => setEditing('new')}>
            <Plus size={20} aria-hidden="true" /> Add a room
          </Button>
        }
      />
      {editing && (
        <RoomForm
          key={editing === 'new' ? 'new' : editing.id}
          room={editing === 'new' ? null : editing}
          onDone={() => setEditing(null)}
        />
      )}
      {remove.isError && (
        <InlineError title="Could not remove the room">
          {(remove.error as ApiError).message}
        </InlineError>
      )}
      {q.isPending && (
        <div aria-busy="true" aria-label="Loading rooms" className="flex flex-col gap-3">
          {[0, 1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      )}
      {q.isError && (
        <InlineError title="Could not load rooms">{(q.error as ApiError).message}</InlineError>
      )}
      {q.data && q.data.length === 0 && (
        <EmptyState
          icon={<DoorOpen size={24} aria-hidden="true" />}
          title="No rooms yet"
          action={<Button onClick={() => setEditing('new')}>Add a room</Button>}
        >
          Employees cannot book anything until there is at least one room.
        </EmptyState>
      )}
      {q.data && q.data.length > 0 && (
        <ul className="m-0 p-0 list-none flex flex-col gap-3">
          {q.data.map((r) => (
            <li
              key={r.id}
              className="bg-card border border-line rounded-2xl p-4 md:p-6 flex flex-wrap items-center justify-between gap-4"
            >
              <div className="flex flex-col gap-2 min-w-0">
                <div className="flex flex-wrap items-center gap-3">
                  <span className="text-title font-semibold">{r.name}</span>
                  {!r.active && <Tag tone="busy">Out of service</Tag>}
                </div>
                <div className="text-small text-muted">
                  Floor {r.floor}, {r.capacity} seats, open {r.openTime.slice(0, 5)} to{' '}
                  {r.closeTime.slice(0, 5)}
                  {r.checkInRequired ? ', check in required' : ''}
                </div>
                <div className="flex flex-wrap gap-2">
                  {r.amenities.map((a) => (
                    <Tag key={a}>{amenityLabel(a)}</Tag>
                  ))}
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  className="min-h-11"
                  onClick={() => setEditing(r)}
                  aria-label={`Edit ${r.name}`}
                >
                  Edit
                </Button>
                {r.active && (
                  <Button
                    variant="ghost"
                    className="min-h-11"
                    onClick={() => remove.mutate(r.id)}
                    disabled={remove.isPending}
                    aria-label={`Take ${r.name} out of service`}
                  >
                    Take out of service
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
