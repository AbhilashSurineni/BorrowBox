import { pluralize } from '@/lib/utils';
import { Link } from 'react-router-dom';
import { DataTable, FilterBar } from '@/components/DataTable';
import { Badge, Button, PageHeader, Pagination, Select, useConfirm, useToast } from '@/components/ui';
import { useAdminMutation, useAdminReports } from '@/features/admin/hooks';
import { adminApi } from '@/features/admin/api';
import { formatDate } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';

import { REPORT_REASON_LABELS } from '@/lib/utils';
import { useUrlParams } from '@/lib/useUrlParams';

const STATUS_TONE = { OPEN: 'amber', UNDER_REVIEW: 'blue', RESOLVED: 'green', DISMISSED: 'grey' } ;

/** Screen 13 (reports): queue with review / uphold / dismiss. */
export default function AdminReportsPage() {
  const { get, set, page } = useUrlParams();
  const confirm = useConfirm();
  const toast = useToast();
  const status = get('status') ?? 'OPEN';
  const { data, isLoading, error, refetch } = useAdminReports({ status: status === 'ALL' ? undefined : status, targetType: get('targetType'), page, limit: 20 });
  const update = useAdminMutation((v) =>
    adminApi.updateReport(v.id, v.status, v.adminNote),
  );

  const decide = async (r, s) => {
    const titles = { UNDER_REVIEW: 'Mark as under review?', RESOLVED: 'Uphold this report?', DISMISSED: 'Dismiss this report?' };
    const res = await confirm({
      title: titles[s],
      message:
        s === 'RESOLVED' && r.accountableUser ? (
          <>
            Upheld reports count towards automatic suspension. <strong>{r.accountableUser.name}</strong> currently has {r.accountableUserUpheldReports} upheld report(s); 3 or more suspends the account.
          </>
        ) : undefined,
      confirmLabel: s === 'RESOLVED' ? 'Uphold' : s === 'DISMISSED' ? 'Dismiss' : 'Mark under review',
      danger: s === 'RESOLVED',
      input: { label: 'Admin note (optional)' },
    });
    if (!res.confirmed) return;
    try {
      const out = await update.mutateAsync({ id: r.id, status: s, adminNote: res.value || undefined });
      toast.success(out.autoSuspended ? 'Report upheld. The user was automatically suspended.' : 'Report updated');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader title="Reports" subtitle={data ? pluralize(data.meta.total, 'report') : undefined} />
      <FilterBar>
        <div className="hidden sm:block" />
        <Select label="Status" value={status} onChange={(e) => set({ status: e.target.value })}>
          <option value="ALL">All</option>
          <option value="OPEN">Open</option>
          <option value="UNDER_REVIEW">Under review</option>
          <option value="RESOLVED">Resolved</option>
          <option value="DISMISSED">Dismissed</option>
        </Select>
        <Select label="Target" value={get('targetType') ?? ''} onChange={(e) => set({ targetType: e.target.value })}>
          <option value="">Items and users</option>
          <option value="ITEM">Items</option>
          <option value="USER">Users</option>
        </Select>
      </FilterBar>
      <DataTable
        caption="Reports"
        rows={data?.data}
        loading={isLoading}
        error={error}
        onRetry={() => refetch()}
        rowKey={(r) => r.id}
        emptyTitle="The queue is empty 🎉"
        columns={[
          {
            header: 'Target',
            cell: (r) => (
              <>
                <Badge>{r.targetType.toLowerCase()}</Badge>{' '}
                <Link to={r.targetType === 'ITEM' ? `/items/${r.targetId}` : `/users/${r.targetId}`} className="font-medium text-gray-900 hover:underline">
                  {r.target?.name ?? 'Deleted'}
                </Link>
                {r.accountableUser && r.targetType === 'ITEM' && <p className="text-xs text-gray-600">Owner: {r.accountableUser.name}</p>}
                {r.accountableUser && <p className="text-xs text-gray-600">{r.accountableUserUpheldReports} upheld · {r.accountableUser.status.toLowerCase()}</p>}
              </>
            ),
          },
          {
            header: 'Report',
            className: 'max-w-xs',
            cell: (r) => (
              <>
                <p className="font-medium">{REPORT_REASON_LABELS[r.reason]}</p>
                <p className="text-xs text-gray-700">{r.description}</p>
                <p className="mt-1 text-xs text-gray-600">
                  by {r.reporter.name} · {formatDate(r.createdAt)}
                </p>
                {r.adminNote && <p className="mt-1 text-xs italic text-gray-700">Note: {r.adminNote}</p>}
              </>
            ),
          },
          { header: 'Status', cell: (r) => <Badge tone={STATUS_TONE[r.status]}>{r.status.replace('_', ' ').toLowerCase()}</Badge> },
          {
            header: 'Actions',
            cell: (r) =>
              (r.status === 'OPEN' || r.status === 'UNDER_REVIEW') && (
                <div className="flex flex-wrap gap-1">
                  {r.status === 'OPEN' && (
                    <Button size="sm" variant="secondary" onClick={() => decide(r, 'UNDER_REVIEW')}>
                      Review
                    </Button>
                  )}
                  <Button size="sm" variant="danger" onClick={() => decide(r, 'RESOLVED')}>
                    Uphold
                  </Button>
                  <Button size="sm" variant="secondary" onClick={() => decide(r, 'DISMISSED')}>
                    Dismiss
                  </Button>
                </div>
              ),
          },
        ]}
      />
      {data && <Pagination page={data.meta.page} totalPages={data.meta.totalPages} onChange={(p) => set({ page: String(p) })} />}
    </>
  );
}
