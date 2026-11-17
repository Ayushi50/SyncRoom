import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Skeleton } from './ui';

export function RequireAuth({ admin = false }: { admin?: boolean }) {
  const { user, loading } = useAuth();
  const loc = useLocation();
  if (loading) {
    return (
      <div className="p-10 flex flex-col gap-4 max-w-xl" aria-busy="true" aria-label="Loading">
        <Skeleton className="h-10 w-64" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }
  if (!user) {
    const next = encodeURIComponent(loc.pathname + loc.search);
    return <Navigate to={`/login?next=${next}`} replace />;
  }
  if (admin && user.role !== 'ADMIN') return <Navigate to="/rooms/find" replace />;
  return <Outlet />;
}
