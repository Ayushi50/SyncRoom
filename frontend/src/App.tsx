import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { RequireAuth } from './components/RequireAuth';
import { Landing } from './pages/Landing';
import { Login, Register } from './pages/AuthPages';
import { Finder } from './pages/Finder';
import { MyBookings } from './pages/MyBookings';
import { BookingDetail } from './pages/BookingDetail';
import { AdminRooms } from './pages/AdminRooms';
import { AdminBookings } from './pages/AdminBookings';
import { NotFound } from './pages/NotFound';

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<RequireAuth />}>
        <Route element={<AppShell />}>
          <Route path="/rooms/find" element={<Finder />} />
          <Route path="/bookings" element={<MyBookings />} />
          <Route path="/bookings/:id" element={<BookingDetail />} />
        </Route>
      </Route>
      <Route element={<RequireAuth admin />}>
        <Route element={<AppShell />}>
          <Route path="/admin/rooms" element={<AdminRooms />} />
          <Route path="/admin/bookings" element={<AdminBookings />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
