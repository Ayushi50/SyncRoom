import type { BookingStatus } from '../lib/types';
import { Tag } from './ui';

export function StatusTag({ status }: { status: BookingStatus }) {
  if (status === 'CONFIRMED') return <Tag tone="tint">Confirmed</Tag>;
  if (status === 'CANCELLED') return <Tag tone="danger">Cancelled</Tag>;
  return <Tag tone="busy">Released</Tag>;
}
