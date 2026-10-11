import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { PageSpinner } from '@/components/ui';
import { useAuth } from './AuthProvider';

/** Only for logged-out visitors (login, register). */
export function GuestOnly() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <PageSpinner />;
  if (status === 'authenticated') {
    const from = (location.state )?.from;
    return <Navigate to={from ?? '/browse'} replace />;
  }
  return <Outlet />;
}

/** Any signed-in member. */
export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <PageSpinner />;
  if (status === 'guest') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <Outlet />;
}

/** ADMIN role only. */
export function RequireAdmin() {
  const { status, isAdmin } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <PageSpinner />;
  if (status === 'guest') return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (!isAdmin) return <Navigate to="/browse" replace />;
  return <Outlet />;
}
