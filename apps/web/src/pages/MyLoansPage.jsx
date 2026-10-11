import { useSearchParams } from 'react-router-dom';

import { LoanCard } from '@/components/LoanCard';
import { PageContainer } from '@/components/Layout';
import { ButtonLink, EmptyState, ErrorState, PageHeader, Pagination, SkeletonList, Tabs } from '@/components/ui';
import { useLoans } from '@/features/loans/hooks';

const GROUPS = {
  open: { label: 'In progress', statuses: ['PENDING', 'APPROVED', 'ACTIVE', 'OVERDUE'] },
  past: { label: 'Past', statuses: ['RETURNED', 'COMPLETED', 'REJECTED', 'CANCELLED', 'EXPIRED'] },
  all: { label: 'All' },
};

/** Screen 8: tabs Borrowing / Lending with status chips and action buttons. */
export function MyLoansPage() {
  const [params, setParams] = useSearchParams();
  const role = params.get('role') === 'owner' ? 'owner' : 'borrower';
  const group = GROUPS[params.get('show') ?? 'open'] ? (params.get('show') ?? 'open') : 'open';
  const page = Number(params.get('page') ?? 1);
  const { data, isLoading, isError, error, refetch } = useLoans({ role, status: GROUPS[group].statuses, page, limit: 10 });
  const pendingLending = useLoans({ role: 'owner', status: ['PENDING'], limit: 1 }).data?.meta.total ?? 0;

  const set = (patch) => setParams({ role, show: group, ...patch });

  return (
    <PageContainer>
      <PageHeader title="My loans" subtitle="Requests and loans where you’re the borrower or the lender." />
      <Tabs
        label="Loan role"
        value={role}
        onChange={(r) => setParams({ role: r, show: group })}
        tabs={[
          { value: 'borrower', label: 'Borrowing' },
          { value: 'owner', label: 'Lending', badge: pendingLending },
        ]}
      />
      <div className="mt-4 flex gap-2" role="group" aria-label="Filter loans">
        {Object.entries(GROUPS).map(([key, g]) => (
          <button
            key={key}
            type="button"
            aria-pressed={group === key}
            onClick={() => set({ show: key })}
            className={`min-h-[36px] rounded-full px-3 text-sm font-medium ring-1 ring-inset ${group === key ? 'bg-brand-700 text-white ring-brand-700' : 'bg-white text-gray-700 ring-gray-300 hover:bg-gray-50'}`}
          >
            {g.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" className="mt-4">
        {isError ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : isLoading ? (
          <SkeletonList />
        ) : !data?.data.length ? (
          <EmptyState
            icon={role === 'borrower' ? '🎒' : '🤝'}
            title={role === 'borrower' ? 'Nothing borrowed here' : 'No lending activity here'}
            message={role === 'borrower' ? 'Find something you need and send a request.' : 'When someone requests one of your items, it shows up here.'}
            action={role === 'borrower' ? <ButtonLink to="/browse">Browse items</ButtonLink> : <ButtonLink to="/items/new">List an item</ButtonLink>}
          />
        ) : (
          <>
            <ul className="space-y-3">
              {data.data.map((loan) => (
                <li key={loan.id}>
                  <LoanCard loan={loan} />
                </li>
              ))}
            </ul>
            <Pagination page={data.meta.page} totalPages={data.meta.totalPages} onChange={(p) => set({ page: String(p) })} />
          </>
        )}
      </div>
    </PageContainer>
  );
}
