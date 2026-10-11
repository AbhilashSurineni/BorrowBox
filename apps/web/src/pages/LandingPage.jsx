import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ButtonLink, Skeleton } from '@/components/ui';
import { useAuth } from '@/features/auth/AuthProvider';
import { useCategories } from '@/features/items/hooks';
import { pluralize } from '@/lib/utils';

const ICONS = { book: '📚', calculator: '🧮', flask: '🧪', ball: '🏸', cpu: '💻', pencil: '✏️', wrench: '🔧', box: '📦' };
export const categoryIcon = (icon) => (icon && ICONS[icon]) || '📦';

/** Screen 1: value proposition, search bar, featured categories, sign-up call to action. */
export function LandingPage() {
  const navigate = useNavigate();
  const { status } = useAuth();
  const { data: categories, isLoading } = useCategories();
  const [q, setQ] = useState('');

  const search = (e) => {
    e.preventDefault();
    navigate(`/browse${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ''}`);
  };

  return (
    <>
      <section className="bg-gradient-to-b from-brand-50 to-gray-50">
        <div className="mx-auto max-w-4xl px-4 py-14 text-center sm:py-20">
          <h1 className="text-3xl font-extrabold tracking-tight text-gray-900 sm:text-5xl">Borrow it. Lend it. Skip the store.</h1>
          <p className="mx-auto mt-4 max-w-2xl text-base text-gray-700 sm:text-lg">
            BorrowBox connects campus members who need a calculator, lab coat or racket for a few days with people who have one sitting unused.
          </p>
          <form onSubmit={search} role="search" className="mx-auto mt-8 flex max-w-xl gap-2">
            <label htmlFor="landing-search" className="sr-only">
              Search items
            </label>
            <input
              id="landing-search"
              type="search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Try “graphing calculator”"
              className="min-h-[48px] flex-1 rounded-lg border border-gray-300 bg-white px-4 text-base focus:border-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-700/30"
            />
            <button type="submit" className="min-h-[48px] rounded-lg bg-brand-700 px-5 font-semibold text-white hover:bg-brand-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700">
              Search
            </button>
          </form>
          {status !== 'authenticated' && (
            <div className="mt-6 flex justify-center gap-3">
              <ButtonLink to="/register" size="lg">
                Join with your campus email
              </ButtonLink>
              <ButtonLink to="/browse" size="lg" variant="secondary">
                Browse items
              </ButtonLink>
            </div>
          )}
        </div>
      </section>

      <section className="mx-auto max-w-5xl px-4 py-12" aria-labelledby="cats">
        <h2 id="cats" className="text-xl font-bold text-gray-900">
          Popular categories
        </h2>
        <ul className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
          {isLoading
            ? Array.from({ length: 8 }, (_, i) => <Skeleton key={i} className="h-24" />)
            : categories?.map((c) => (
                <li key={c.id}>
                  <Link
                    to={`/browse?category=${c.id}`}
                    className="flex h-full flex-col items-center justify-center gap-1 rounded-xl border border-gray-200 bg-white p-4 text-center hover:border-brand-700 hover:shadow-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700"
                  >
                    <span className="text-3xl" aria-hidden>
                      {categoryIcon(c.icon)}
                    </span>
                    <span className="font-medium text-gray-900">{c.name}</span>
                    <span className="text-xs text-gray-600">{pluralize(c.itemCount, 'item')}</span>
                  </Link>
                </li>
              ))}
        </ul>
      </section>

      <section className="border-t border-gray-200 bg-white">
        <div className="mx-auto grid max-w-5xl gap-6 px-4 py-12 sm:grid-cols-3">
          {[
            ['🔎', 'Find it', 'Search what classmates are lending and check the availability calendar.'],
            ['🤝', 'Request & meet', 'Pick your dates. Once the owner approves, you’ll see their contact details.'],
            ['⭐', 'Return & rate', 'Hand it back on time and rate each other to build trust.'],
          ].map(([icon, title, text]) => (
            <div key={title}>
              <div className="text-3xl" aria-hidden>
                {icon}
              </div>
              <h3 className="mt-2 font-semibold text-gray-900">{title}</h3>
              <p className="mt-1 text-sm text-gray-700">{text}</p>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}
