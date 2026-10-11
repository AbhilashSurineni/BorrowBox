import { pluralize } from '@/lib/utils';
import { Link } from 'react-router-dom';
import { LoanStatus } from '@/lib/constants';
import { DataTable, FilterBar } from '@/components/DataTable';
import { Button, LoanStatusChip, PageHeader, Pagination, Select, useConfirm, useToast } from '@/components/ui';
import { loanStatusLabel } from '@/components/ui/StatusChip';
import { useAdminLoans, useAdminMutation } from '@/features/admin/hooks';
import { adminApi } from '@/features/admin/api';
import { formatRange } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';

import { useUrlParams } from '@/lib/useUrlParams';

/** Screen 13 (loans): monitoring with the overdue list and one-click reminder (FR-31). */
export default function AdminLoansPage() {
  const { get, set, page } = useUrlParams();
  const status = get('status') ?? 'OVERDUE';
  const confirm = useConfirm();
  const toast = useToast();
  const { data, isLoading, error, refetch } = useAdminLoans({ status: status === 'ALL' ? undefined : status, page, limit: 20 });
  const remind = useAdminMutation((v) => adminApi.remindLoan(v.id, v.message));

  const sendReminder = async (l) => {
    const r = await confirm({
      title: `Remind ${l.borrower.name}?`,
      message: `An in-app notification and email about “${l.item.name}” will be sent now.`,
      confirmLabel: 'Send reminder',
      input: { label: 'Extra message (optional)' },
    });
    if (!r.confirmed) return;
    try {
      await remind.mutateAsync({ id: l.id, message: r.value || undefined });
      toast.success('Reminder sent');
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader title="Loans" subtitle={data ? pluralize(data.meta.total, `${status === 'ALL' ? '' : loanStatusLabel(status ).toLowerCase() + ' '}loan`) : undefined} />
      <FilterBar>
        <Select label="Status" value={status} onChange={(e) => set({ status: e.target.value })}>
          <option value="ALL">All statuses</option>
          {LoanStatus.map((s) => (
            <option key={s} value={s}>
              {loanStatusLabel(s)}
            </option>
          ))}
        </Select>
      </FilterBar>
      <DataTable
        caption="Loans"
        rows={data?.data}
        loading={isLoading}
        error={error}
        onRetry={() => refetch()}
        rowKey={(l) => l.id}
        emptyTitle={status === 'OVERDUE' ? 'No overdue loans 🎉' : 'No loans found'}
        columns={[
          {
            header: 'Item',
            cell: (l) => (
              <Link to={`/items/${l.item.id}`} className="font-medium text-gray-900 hover:underline">
                {l.item.name}
              </Link>
            ),
          },
          {
            header: 'Borrower',
            cell: (l) => (
              <>
                {l.borrower.name}
                <p className="text-xs text-gray-600">{l.borrowerContact.email}</p>
                {l.borrowerContact.phone && <p className="text-xs text-gray-600">{l.borrowerContact.phone}</p>}
              </>
            ),
          },
          {
            header: 'Owner',
            cell: (l) => (
              <>
                {l.owner.name}
                <p className="text-xs text-gray-600">{l.ownerContact.email}</p>
              </>
            ),
          },
          {
            header: 'Dates',
            cell: (l) => (
              <>
                {formatRange(l.startDate, l.endDate)}
                {l.daysOverdue > 0 && <p className="text-xs font-semibold text-red-800">⚠️ {l.daysOverdue} days overdue</p>}
              </>
            ),
          },
          { header: 'Status', cell: (l) => <LoanStatusChip status={l.status} /> },
          {
            header: 'Actions',
            cell: (l) =>
              ['APPROVED', 'ACTIVE', 'OVERDUE'].includes(l.status) && (
                <Button size="sm" variant={l.status === 'OVERDUE' ? 'primary' : 'secondary'} onClick={() => sendReminder(l)}>
                  Send reminder
                </Button>
              ),
          },
        ]}
      />
      {data && <Pagination page={data.meta.page} totalPages={data.meta.totalPages} onChange={(p) => set({ page: String(p) })} />}
    </>
  );
}
