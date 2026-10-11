import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { ItemImage } from '@/components/ItemCard';
import { PageContainer } from '@/components/Layout';
import { Badge, Button, ButtonLink, EmptyState, ErrorState, ItemStatusChip, LoanStatusChip, PageHeader, Pagination, SkeletonList, Tabs, useToast } from '@/components/ui';
import { useMyItems, useTogglePause } from '@/features/items/hooks';
import { formatRange } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';

/** Screen 7: tabs Listed / Paused with request badges. */
export function MyItemsPage() {
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') === 'paused' ? 'paused' : 'listed') ;
  const page = Number(params.get('page') ?? 1);
  const { data, isLoading, isError, error, refetch } = useMyItems(tab, page);
  const listedCount = useMyItems('listed', 1).data;
  const pending = listedCount?.data.reduce((n, i) => n + i.pendingRequestCount, 0) ?? 0;

  return (
    <PageContainer>
      <PageHeader title="My items" subtitle="Everything you lend, with upcoming requests." actions={<ButtonLink to="/items/new">+ List an item</ButtonLink>} />
      <Tabs
        label="Item status"
        value={tab}
        onChange={(t) => setParams({ tab: t })}
        tabs={[
          { value: 'listed', label: 'Listed', badge: pending },
          { value: 'paused', label: 'Paused' },
        ]}
      />
      <div role="tabpanel" className="mt-4">
        {isError ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : isLoading ? (
          <SkeletonList />
        ) : !data?.data.length ? (
          <EmptyState
            title={tab === 'listed' ? 'You haven’t listed anything yet' : 'No paused items'}
            message={tab === 'listed' ? 'Lend something you rarely use and help a classmate out.' : 'Pause an item when it’s temporarily unavailable.'}
            action={tab === 'listed' && <ButtonLink to="/items/new">List your first item</ButtonLink>}
          />
        ) : (
          <>
            <ul className="space-y-3">
              {data.data.map((item) => (
                <li key={item.id}>
                  <MyItemRow item={item} />
                </li>
              ))}
            </ul>
            <Pagination page={data.meta.page} totalPages={data.meta.totalPages} onChange={(p) => setParams({ tab, page: String(p) })} />
          </>
        )}
      </div>
    </PageContainer>
  );
}

function MyItemRow({ item }) {
  const toast = useToast();
  const pause = useTogglePause();
  const [open, setOpen] = useState(item.pendingRequestCount > 0);
  return (
    <article className="rounded-xl border border-gray-200 bg-white p-4">
      <div className="flex gap-3">
        <ItemImage url={item.images[0]?.url ?? null} name={item.name} className="h-16 w-16 shrink-0 rounded-lg text-2xl" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <Link to={`/items/${item.id}`} className="font-semibold text-gray-900 hover:underline">
              {item.name}
            </Link>
            <ItemStatusChip status={item.status} />
            {item.pendingRequestCount > 0 && <Badge tone="amber">{item.pendingRequestCount} new request{item.pendingRequestCount > 1 ? 's' : ''}</Badge>}
          </div>
          <p className="text-xs text-gray-600">
            {item.category.name} · up to {item.maxLoanDays} days · {item.pickupLocation}
          </p>
          {item.isRemoved && <p className="mt-1 text-xs text-red-800">Removed by a moderator{item.removedReason ? `: ${item.removedReason}` : ''}</p>}
        </div>
      </div>
      <div className="mt-3 flex flex-wrap items-center gap-2">
        <ButtonLink to={`/items/${item.id}/edit`} size="sm" variant="secondary">
          Edit
        </ButtonLink>
        {!item.isRemoved && (
          <Button
            size="sm"
            variant="secondary"
            loading={pause.isPending}
            onClick={async () => {
              try {
                await pause.mutateAsync(item.id);
                toast.success(item.isPaused ? 'Listing resumed' : 'Listing paused');
              } catch (err) {
                toast.error(errorMessage(err));
              }
            }}
          >
            {item.isPaused ? 'Resume' : 'Pause'}
          </Button>
        )}
        {item.upcoming.length > 0 && (
          <Button size="sm" variant="ghost" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
            {open ? 'Hide' : 'Show'} {item.upcoming.length} upcoming
          </Button>
        )}
      </div>
      {open && item.upcoming.length > 0 && (
        <ul className="mt-3 divide-y divide-gray-100 rounded-lg border border-gray-100">
          {item.upcoming.map((u) => (
            <li key={u.loanId}>
              <Link to={`/loans/${u.loanId}`} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2 text-sm hover:bg-gray-50">
                <span className="text-gray-900">
                  {u.borrower.name} · {formatRange(u.startDate, u.endDate)}
                </span>
                <LoanStatusChip status={u.status} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </article>
  );
}
