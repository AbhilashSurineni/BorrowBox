import { NavLink, Outlet } from 'react-router-dom';
import { cn } from '@/lib/utils';

const links = [
  ['/admin', 'Dashboard'],
  ['/admin/users', 'Users'],
  ['/admin/items', 'Items'],
  ['/admin/loans', 'Loans'],
  ['/admin/reports', 'Reports'],
  ['/admin/settings', 'Settings'],
] ;

/** Admin section shell (screens 12–14). */
export default function AdminLayout() {
  return (
    <div className="mx-auto max-w-6xl px-4 py-6">
      <nav aria-label="Admin" className="-mx-4 mb-6 overflow-x-auto border-b border-gray-200 px-4">
        <ul className="flex gap-1">
          {links.map(([to, label]) => (
            <li key={to}>
              <NavLink
                to={to}
                end={to === '/admin'}
                className={({ isActive }) =>
                  cn(
                    '-mb-px flex min-h-[44px] items-center whitespace-nowrap border-b-2 px-3 text-sm font-medium',
                    isActive ? 'border-brand-700 text-brand-800' : 'border-transparent text-gray-600 hover:text-gray-900',
                  )
                }
              >
                {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <Outlet />
    </div>
  );
}
