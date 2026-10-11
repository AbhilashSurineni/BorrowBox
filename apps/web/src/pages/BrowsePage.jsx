import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ItemCondition } from '@/lib/constants';
import { ItemCard, ItemCardSkeleton } from '@/components/ItemCard';
import { PageContainer } from '@/components/Layout';
import { Button, EmptyState, ErrorState, Input, Modal, Pagination, Select } from '@/components/ui';
import { useCategories, useItems } from '@/features/items/hooks';
import { todayStr } from '@/lib/dates';
import { CONDITION_LABELS } from '@/lib/utils';

const FILTER_KEYS = ['category', 'condition', 'available', 'available_from', 'available_to', 'location', 'max_duration', 'min_rating'] ;

/** Screen 3: search, filters (sidebar / mobile drawer), card grid and pagination — all state lives in the URL. */
export function BrowsePage() {
  const [params, setParams] = useSearchParams();
  const [drawer, setDrawer] = useState(false);
  const [q, setQ] = useState(params.get('q') ?? '');

  const query = useMemo(
    () => ({
      q: params.get('q') ?? undefined,
      category: params.get('category') ?? undefined,
      condition: params.get('condition') ?? undefined,
      available: params.get('available') === 'true' || undefined,
      available_from: params.get('available_from') ?? undefined,
      available_to: params.get('available_to') ?? undefined,
      location: params.get('location') ?? undefined,
      max_duration: params.get('max_duration') ? Number(params.get('max_duration')) : undefined,
      min_rating: params.get('min_rating') ? Number(params.get('min_rating')) : undefined,
      sort: params.get('sort') ?? 'newest',
      page: Number(params.get('page') ?? 1),
      limit: 12,
    }),
    [params],
  );
  const { data, isLoading, isError, error, refetch, isFetching } = useItems(query);

  const update = (patch, opts = { resetPage: true }) => {
    const next = new URLSearchParams(params);
    for (const [k, v] of Object.entries(patch)) {
      if (v === undefined || v === '') next.delete(k);
      else next.set(k, v);
    }
    if (opts.resetPage !== false) next.delete('page');
    setParams(next, { replace: opts.replace });
  };

  // Debounced keyword search keeps the URL in sync while typing.
  useEffect(() => {
    if ((params.get('q') ?? '') === q) return;
    const t = setTimeout(() => update({ q: q.trim() || undefined }, { resetPage: true, replace: true }), 350);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q]);
  useEffect(() => {
    setQ(params.get('q') ?? '');
  }, [params]);

  const activeFilters = FILTER_KEYS.filter((k) => params.get(k)).length;
  const clearFilters = () => update(Object.fromEntries(FILTER_KEYS.map((k) => [k, undefined])));

  const filters = <Filters params={params} update={update} onClear={clearFilters} activeCount={activeFilters} />;

  return (
    <PageContainer wide>
      <h1 className="sr-only">Browse items</h1>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div role="search" className="flex-1">
          <label htmlFor="browse-q" className="sr-only">
            Search items
          </label>
          <input
            id="browse-q"
            type="search"
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search by name or description"
            className="min-h-[44px] w-full rounded-lg border border-gray-300 bg-white px-4 focus:border-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-700/30"
          />
        </div>
        <div className="flex gap-2">
          <Button variant="secondary" className="md:hidden" onClick={() => setDrawer(true)} aria-haspopup="dialog">
            Filters{activeFilters ? ` (${activeFilters})` : ''}
          </Button>
          <label htmlFor="browse-sort" className="sr-only">
            Sort by
          </label>
          <select
            id="browse-sort"
            value={query.sort}
            onChange={(e) => update({ sort: e.target.value === 'newest' ? undefined : e.target.value })}
            className="min-h-[44px] rounded-lg border border-gray-300 bg-white px-3 text-sm"
          >
            <option value="newest">Newest</option>
            <option value="rating">Top rated</option>
            <option value="soonest_available">Soonest available</option>
          </select>
        </div>
      </div>

      <div className="mt-6 grid gap-6 md:grid-cols-[240px_1fr]">
        <aside className="hidden md:block" aria-label="Filters">
          {filters}
        </aside>
        <section aria-live="polite" aria-busy={isFetching}>
          {data && (
            <p className="mb-3 text-sm text-gray-700">
              {data.meta.total} {data.meta.total === 1 ? 'item' : 'items'}
              {query.q && <> for “{query.q}”</>}
            </p>
          )}
          {isError ? (
            <ErrorState error={error} onRetry={() => refetch()} />
          ) : isLoading ? (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {Array.from({ length: 6 }, (_, i) => (
                <ItemCardSkeleton key={i} />
              ))}
            </div>
          ) : data && data.data.length === 0 ? (
            <EmptyState
              icon="🔍"
              title="No items match"
              message="Try a different keyword or loosen your filters."
              action={
                (activeFilters > 0 || query.q) && (
                  <Button variant="secondary" onClick={() => update({ ...Object.fromEntries(FILTER_KEYS.map((k) => [k, undefined])), q: undefined })}>
                    Clear search and filters
                  </Button>
                )
              }
            />
          ) : (
            <>
              <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {data?.data.map((item) => (
                  <li key={item.id}>
                    <ItemCard item={item} />
                  </li>
                ))}
              </ul>
              {data && <Pagination page={data.meta.page} totalPages={data.meta.totalPages} onChange={(p) => update({ page: String(p) }, { resetPage: false })} />}
            </>
          )}
        </section>
      </div>

      <Modal open={drawer} onClose={() => setDrawer(false)} title="Filters" variant="drawer" footer={<Button onClick={() => setDrawer(false)}>Show results</Button>}>
        {filters}
      </Modal>
    </PageContainer>
  );
}

function Filters({
  params,
  update,
  onClear,
  activeCount,
}

) {
  const { data: categories } = useCategories();
  const today = todayStr();
  const [location, setLocation] = useState(params.get('location') ?? '');
  useEffect(() => {
    setLocation(params.get('location') ?? '');
  }, [params]);

  return (
    <div className="space-y-5">
      <Select label="Category" value={params.get('category') ?? ''} onChange={(e) => update({ category: e.target.value })}>
        <option value="">All categories</option>
        {categories?.map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </Select>
      <Select label="Condition" value={params.get('condition') ?? ''} onChange={(e) => update({ condition: e.target.value })}>
        <option value="">Any condition</option>
        {ItemCondition.map((c) => (
          <option key={c} value={c}>
            {CONDITION_LABELS[c]}
          </option>
        ))}
      </Select>
      <label className="flex min-h-[44px] items-center gap-3 text-sm font-medium text-gray-800">
        <input
          type="checkbox"
          className="h-5 w-5 rounded border-gray-400 text-brand-700 focus:ring-brand-700"
          checked={params.get('available') === 'true'}
          onChange={(e) => update({ available: e.target.checked ? 'true' : undefined })}
        />
        Available today
      </label>
      <fieldset className="space-y-2">
        <legend className="text-sm font-medium text-gray-800">Free between</legend>
        <Input
          label="From"
          type="date"
          min={today}
          value={params.get('available_from') ?? ''}
          onChange={(e) => update({ available_from: e.target.value })}
        />
        <Input
          label="To"
          type="date"
          min={params.get('available_from') ?? today}
          value={params.get('available_to') ?? ''}
          onChange={(e) => update({ available_to: e.target.value })}
        />
      </fieldset>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          update({ location: location.trim() });
        }}
      >
        <Input label="Pickup location" placeholder="e.g. Library" value={location} onChange={(e) => setLocation(e.target.value)} onBlur={() => update({ location: location.trim() })} />
      </form>
      <Select label="Loan length" value={params.get('max_duration') ?? ''} onChange={(e) => update({ max_duration: e.target.value })}>
        <option value="">Any length</option>
        {[3, 7, 14, 21].map((d) => (
          <option key={d} value={d}>
            At least {d} days
          </option>
        ))}
      </Select>
      <Select label="Owner rating" value={params.get('min_rating') ?? ''} onChange={(e) => update({ min_rating: e.target.value })}>
        <option value="">Any rating</option>
        {[4, 3, 2].map((r) => (
          <option key={r} value={r}>
            {r}★ and up
          </option>
        ))}
      </Select>
      {activeCount > 0 && (
        <Button variant="ghost" className="w-full" onClick={onClear}>
          Clear {activeCount} filter{activeCount > 1 ? 's' : ''}
        </Button>
      )}
    </div>
  );
}
