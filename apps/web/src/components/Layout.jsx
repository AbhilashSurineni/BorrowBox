import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthProvider';
import { useUnreadCount } from '@/features/notifications/hooks';
import { authApi } from '@/features/auth/api';
import { errorMessage } from '@/lib/errorMessages';
import { cn } from '@/lib/utils';
import { Avatar, ButtonLink, ErrorBoundary, useToast } from './ui';

function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2 font-bold text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700">
      <img src="/favicon.svg" alt="" className="h-8 w-8" />
      <span className="text-lg tracking-tight">BorrowBox</span>
    </Link>
  );
}

export function NotificationBell() {
  const { data: unread = 0 } = useUnreadCount();
  return (
    <NavLink
      to="/notifications"
      className={({ isActive }) =>
        cn('relative rounded-lg p-2 hover:bg-gray-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700', isActive ? 'text-brand-800' : 'text-gray-700')
      }
      aria-label={unread ? `Notifications, ${unread} unread` : 'Notifications'}
    >
      <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={1.8} aria-hidden>
        <path d="M15 17h5l-1.4-1.4A2 2 0 0 1 18 14.2V11a6 6 0 1 0-12 0v3.2a2 2 0 0 1-.6 1.4L4 17h5m6 0a3 3 0 1 1-6 0m6 0H9" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {unread > 0 && (
        <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-[20px] items-center justify-center rounded-full bg-red-700 px-1 text-xs font-bold text-white" aria-hidden>
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </NavLink>
  );
}

function UserMenu() {
  const { user, isAdmin, logout } = useAuth();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const navigate = useNavigate();
  useEffect(() => {
    if (!open) return;
    const close = (e) => {
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !ref.current?.contains(e.target )) setOpen(false);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', close);
    };
  }, [open]);
  if (!user) return null;
  const item = 'block w-full px-4 py-2.5 text-left text-sm text-gray-800 hover:bg-gray-50';
  return (
    <div className="relative" ref={ref}>
      <button type="button" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)} className="flex items-center gap-2 rounded-full p-1 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700">
        <Avatar name={user.name} url={user.avatarUrl} size="sm" />
        <span className="sr-only">Account menu</span>
      </button>
      {open && (
        <div role="menu" className="absolute right-0 z-40 mt-2 w-56 overflow-hidden rounded-xl border border-gray-200 bg-white py-1 shadow-lg" onClick={() => setOpen(false)}>
          <p className="truncate border-b border-gray-100 px-4 py-2 text-xs text-gray-600">{user.email}</p>
          <Link role="menuitem" to="/profile" className={item}>
            Profile
          </Link>
          <Link role="menuitem" to="/my/items" className={item}>
            My items
          </Link>
          <Link role="menuitem" to="/items/new" className={item}>
            List an item
          </Link>
          {isAdmin && (
            <Link role="menuitem" to="/admin" className={item}>
              Admin dashboard
            </Link>
          )}
          <button
            role="menuitem"
            type="button"
            className={cn(item, 'border-t border-gray-100')}
            onClick={async () => {
              await logout();
              navigate('/');
            }}
          >
            Log out
          </button>
        </div>
      )}
    </div>
  );
}

function VerifyBanner() {
  const { user } = useAuth();
  const toast = useToast();
  const [sent, setSent] = useState(false);
  if (!user || user.emailVerified) return null;
  return (
    <div className="border-b border-amber-300 bg-amber-50 px-4 py-2 text-center text-sm text-amber-900" role="status">
      Verify your email ({user.email}) to borrow and lend items.{' '}
      <button
        type="button"
        disabled={sent}
        className="font-semibold underline disabled:no-underline"
        onClick={async () => {
          try {
            await authApi.resendVerification(user.email);
            setSent(true);
            toast.success('Verification email sent.');
          } catch (err) {
            toast.error(errorMessage(err));
          }
        }}
      >
        {sent ? 'Link sent' : 'Resend link'}
      </button>
    </div>
  );
}

const desktopLink = ({ isActive }) =>
  cn('rounded-lg px-3 py-2 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700', isActive ? 'bg-brand-50 text-brand-800' : 'text-gray-700 hover:bg-gray-100');

function MobileNav() {
  const { data: unread = 0 } = useUnreadCount();
  const links = [
    { to: '/browse', label: 'Browse', icon: 'M21 21l-4.3-4.3M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14z' },
    { to: '/my/loans', label: 'Loans', icon: 'M4 7h16M4 12h16M4 17h10' },
    { to: '/my/items', label: 'My items', icon: 'M20 7l-8-4-8 4m16 0v10l-8 4m8-14-8 4m0 10L4 17V7m8 14V11' },
    { to: '/notifications', label: 'Alerts', icon: 'M15 17h5l-1.4-1.4A2 2 0 0 1 18 14.2V11a6 6 0 1 0-12 0v3.2a2 2 0 0 1-.6 1.4L4 17h5m6 0a3 3 0 1 1-6 0', badge: unread },
    { to: '/profile', label: 'Profile', icon: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0zM12 14a7 7 0 0 0-7 7h14a7 7 0 0 0-7-7z' },
  ];
  return (
    <nav aria-label="Primary" className="fixed inset-x-0 bottom-0 z-30 border-t border-gray-200 bg-white pb-[env(safe-area-inset-bottom)] md:hidden">
      <ul className="grid grid-cols-5">
        {links.map((l) => (
          <li key={l.to}>
            <NavLink to={l.to} className={({ isActive }) => cn('relative flex min-h-[56px] flex-col items-center justify-center gap-0.5 text-xs font-medium', isActive ? 'text-brand-800' : 'text-gray-600')}>
              <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                <path d={l.icon} />
              </svg>
              {l.label}
              {!!l.badge && (
                <span className="absolute right-[22%] top-1.5 h-2.5 w-2.5 rounded-full bg-red-700" aria-label={`${l.badge} unread`} />
              )}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export function Layout() {
  const { status, isAdmin } = useAuth();
  const location = useLocation();
  const authed = status === 'authenticated';
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [location.pathname]);

  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[70] focus:rounded-lg focus:bg-white focus:px-4 focus:py-2 focus:shadow">
        Skip to content
      </a>
      <header className="sticky top-0 z-30 border-b border-gray-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4">
          <Logo />
          {authed ? (
            <>
              <nav aria-label="Main" className="hidden items-center gap-1 md:flex">
                <NavLink to="/browse" className={desktopLink}>
                  Browse
                </NavLink>
                <NavLink to="/my/loans" className={desktopLink}>
                  My loans
                </NavLink>
                <NavLink to="/my/items" className={desktopLink}>
                  My items
                </NavLink>
                {isAdmin && (
                  <NavLink to="/admin" className={desktopLink}>
                    Admin
                  </NavLink>
                )}
              </nav>
              <div className="flex items-center gap-1">
                <ButtonLink to="/items/new" size="sm" className="hidden sm:inline-flex">
                  + List item
                </ButtonLink>
                <NotificationBell />
                <UserMenu />
              </div>
            </>
          ) : status === 'guest' ? (
            <div className="flex items-center gap-2">
              <NavLink to="/browse" className={cn(desktopLink({ isActive: false }), 'hidden sm:block')}>
                Browse
              </NavLink>
              <ButtonLink to="/login" variant="ghost" size="sm">
                Log in
              </ButtonLink>
              <ButtonLink to="/register" size="sm">
                Sign up
              </ButtonLink>
            </div>
          ) : null}
        </div>
        <VerifyBanner />
      </header>
      <main id="main" className={cn('flex-1', authed && 'pb-20 md:pb-0')}>
        <ErrorBoundary resetKey={location.pathname}>
          <Outlet />
        </ErrorBoundary>
      </main>
      <footer className="hidden border-t border-gray-200 bg-white py-6 text-center text-xs text-gray-600 md:block">
        BorrowBox · Share more, buy less · Campus members only
      </footer>
      {authed && <MobileNav />}
    </div>
  );
}

export function PageContainer({ children, wide }) {
  return <div className={cn('mx-auto px-4 py-6 sm:py-8', wide ? 'max-w-6xl' : 'max-w-4xl')}>{children}</div>;
}

