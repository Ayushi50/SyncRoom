export type Amenity = 'PROJECTOR' | 'VIDEO_CALL' | 'WHITEBOARD' | 'ACCESSIBLE';
export type Role = 'EMPLOYEE' | 'ADMIN';
export type BookingStatus = 'CONFIRMED' | 'CANCELLED' | 'RELEASED';
export type SeriesPattern = 'DAILY' | 'WEEKDAYS' | 'WEEKLY';
export type Scope = 'THIS' | 'FOLLOWING';

export const AMENITIES: { value: Amenity; label: string }[] = [
  { value: 'VIDEO_CALL', label: 'Video call' },
  { value: 'PROJECTOR', label: 'Projector' },
  { value: 'WHITEBOARD', label: 'Whiteboard' },
  { value: 'ACCESSIBLE', label: 'Accessible' },
];

export const amenityLabel = (a: Amenity) => AMENITIES.find((x) => x.value === a)?.label ?? a;

export interface User {
  id: string;
  name: string;
  email: string;
  role: Role;
  timeZone: string;
}

export interface AuthResponse {
  accessToken: string;
  expiresInSeconds: number;
  user: User;
}

export interface Room {
  id: string;
  name: string;
  floor: number;
  capacity: number;
  amenities: Amenity[];
  openTime: string;
  closeTime: string;
  checkInRequired: boolean;
  active: boolean;
}

export interface Segment {
  start: string;
  end: string;
}

export interface RoomAvailability {
  room: Room;
  busy: Segment[];
}

export interface Suggestion {
  room: Room;
  startsAt: string;
  endsAt: string;
}

export interface SearchResult {
  rooms: RoomAvailability[];
  nearby: Suggestion[];
}

export interface Hold {
  id: string;
  roomId: string;
  startsAt: string;
  endsAt: string;
  expiresAt: string;
}

export interface Person {
  name: string | null;
  email: string;
}

export interface Booking {
  id: string;
  roomId: string;
  roomName: string;
  floor: number;
  title: string;
  startsAt: string;
  endsAt: string;
  status: BookingStatus;
  organizer: Person;
  attendees: Person[];
  seriesId: string | null;
  repeatPattern: SeriesPattern | null;
  repeatCount: number | null;
  version: number;
  checkedInAt: string | null;
  checkInRequired: boolean;
}

export interface Utilization {
  roomId: string;
  roomName: string;
  bookedHours: number;
  openHours: number;
  percent: number;
}

export interface PageView<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
