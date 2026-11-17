import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { MagnifyingGlass } from '@phosphor-icons/react';
import { AMENITIES } from '../lib/types';
import type { Amenity } from '../lib/types';
import { Button, Chip, Field, Input } from './ui';

export interface SearchValues {
  date: string;
  start: string;
  end: string;
  people: number;
  amenities: Amenity[];
}

const schema = z
  .object({
    date: z.string().min(1, 'Pick a date'),
    start: z.string().min(1, 'Pick a start time'),
    end: z.string().min(1, 'Pick an end time'),
    people: z.coerce
      .number({ message: 'Enter a number' })
      .int('Enter a whole number')
      .min(1, 'At least 1 person')
      .max(100, 'At most 100 people'),
  })
  .refine((v) => !v.start || !v.end || v.end > v.start, {
    path: ['end'],
    message: 'End must be after the start',
  });

type FormShape = z.input<typeof schema>;

interface Props {
  initial: SearchValues;
  zoneLabel: string;
  onSearch: (v: SearchValues) => void;
  busy?: boolean;
}

export function SearchForm({ initial, zoneLabel, onSearch, busy }: Props) {
  const [amenities, setAmenities] = useState<Amenity[]>(initial.amenities);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormShape>({
    resolver: zodResolver(schema),
    defaultValues: {
      date: initial.date,
      start: initial.start,
      end: initial.end,
      people: initial.people,
    },
  });
  const toggle = (a: Amenity) =>
    setAmenities((cur) => (cur.includes(a) ? cur.filter((x) => x !== a) : [...cur, a]));

  return (
    <form
      noValidate
      aria-label="Search rooms"
      className="bg-card border border-line rounded-2xl p-6 flex flex-col gap-6"
      onSubmit={handleSubmit((v) =>
        onSearch({ date: v.date, start: v.start, end: v.end, people: Number(v.people), amenities }),
      )}
    >
      <div className="flex flex-wrap gap-4 items-start">
        <Field label="Date" error={errors.date?.message} className="flex-[1.4_1_180px]">
          <Input type="date" aria-invalid={!!errors.date} {...register('date')} />
        </Field>
        <Field label="From" error={errors.start?.message} className="flex-[1_1_120px]">
          <Input type="time" step={900} aria-invalid={!!errors.start} {...register('start')} />
        </Field>
        <Field label="To" error={errors.end?.message} className="flex-[1_1_120px]">
          <Input type="time" step={900} aria-invalid={!!errors.end} {...register('end')} />
        </Field>
        <Field label="People" error={errors.people?.message} className="flex-[1_1_100px]">
          <Input
            type="number"
            min={1}
            max={100}
            inputMode="numeric"
            aria-invalid={!!errors.people}
            {...register('people')}
          />
        </Field>
        <div className="flex flex-col gap-2 self-stretch justify-end">
          <span className="text-small leading-5 invisible max-md:hidden" aria-hidden="true">
            Go
          </span>
          <Button type="submit" disabled={busy} className="max-md:w-full">
            <MagnifyingGlass size={20} aria-hidden="true" />
            Search
          </Button>
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-3" role="group" aria-label="Equipment needed">
        <span className="text-small font-semibold text-body mr-1">Needs</span>
        {AMENITIES.map((a) => (
          <Chip key={a.value} pressed={amenities.includes(a.value)} onClick={() => toggle(a.value)}>
            {a.label}
          </Chip>
        ))}
        <span className="text-caption text-muted ml-auto">Times are in {zoneLabel}</span>
      </div>
    </form>
  );
}
