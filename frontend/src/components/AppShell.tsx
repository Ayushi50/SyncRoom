import { Link, NavLink, Outlet } from 'react-router-dom';
import { SignOut } from '@phosphor-icons/react';
import { useAuth } from '../auth/AuthContext';
import { cx } from './ui';

const navClass = ({ isActive }: { isActive: boolean }) =>
  cx(
    'flex items-center min-h-11 px-4 rounded-full text-small no-underline transition-colors duration-300 ease-soft',
    isActive ? 'bg-tint text-primary font-semibold' : 'text-body hover:bg-sand',
  );

export function AppShell() {
  const { user, logout } = useAuth();
  return (
    <div className="flex flex-col min-h-screen">
      <header className="flex flex-wrap items-center justify-between gap-4 px-4 py-4 md:px-10 bg-card border-b border-line">
        <Link
          to="/"
          className="text-title font-semibold text-primary no-underline min-h-11 flex items-center"
        >
          Rooms
        </Link>
        <nav aria-label="Main" className="flex flex-wrap items-center gap-2">
          <NavLink to="/rooms/find" className={navClass}>
            Find a room
          </NavLink>
          <NavLink to="/bookings" end className={navClass}>
            My bookings
          </NavLink>
          {user?.role === 'ADMIN' && (
            <>
              <NavLink to="/admin/rooms" className={navClass}>
                Manage rooms
              </NavLink>
              <NavLink to="/admin/bookings" className={navClass}>
                All bookings
              </NavLink>
            </>
          )}
          {user && (
            <button
              type="button"
              onClick={() => void logout()}
              className="flex items-center gap-2 min-h-11 px-4 rounded-full text-small text-body hover:bg-sand transition-colors"
            >
              <SignOut size={18} aria-hidden="true" />
              Sign out
            </button>
          )}
        </nav>
      </header>
      <main className="flex-1 w-full max-w-[1280px] mx-auto box-border px-4 py-8 md:px-10 md:py-10 flex flex-col gap-8">
        <Outlet />
      </main>
    </div>
  );
}
