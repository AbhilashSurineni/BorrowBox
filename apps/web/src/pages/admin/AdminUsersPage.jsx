import { pluralize } from '@/lib/utils';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { DataTable, FilterBar } from '@/components/DataTable';
import { Avatar, Badge, Button, Input, PageHeader, Pagination, Select, useConfirm, useToast } from '@/components/ui';
import { useAdminMutation, useAdminUsers } from '@/features/admin/hooks';
import { useAuth } from '@/features/auth/AuthProvider';
import { adminApi } from '@/features/admin/api';
import { formatDate } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';

import { useUrlParams } from '@/lib/useUrlParams';

const STATUS_TONE = { ACTIVE: 'green', SUSPENDED: 'amber', BANNED: 'red' } ;
const ACTION_COPY = {
  WARN: { label: 'Warn', title: 'Send a warning' },
  SUSPEND: { label: 'Suspend', title: 'Suspend this account', danger: true },
  BAN: { label: 'Ban', title: 'Ban this account', danger: true },
  REINSTATE: { label: 'Reinstate', title: 'Reinstate this account' },
};

export function useDebounced(value, ms = 350) {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

/** Screen 13 (users): search, filters, warn / suspend / ban / reinstate. */
export default function AdminUsersPage() {
  const { get, set, page } = useUrlParams();
  const { user: me } = useAuth();
  const confirm = useConfirm();
  const toast = useToast();
  const [q, setQ] = useState(get('q') ?? '');
  const dq = useDebounced(q);
  useEffect(() => {
    if (dq !== (get('q') ?? '')) set({ q: dq || undefined });
  }, [dq, get, set]);

  const { data, isLoading, error, refetch } = useAdminUsers({ q: get('q'), status: get('status'), role: get('role'), page, limit: 20 });
  const act = useAdminMutation((v) =>
    adminApi.setUserStatus(v.id, v.action, v.reason),
  );

  const run = async (u, action) => {
    const copy = ACTION_COPY[action];
    const r = await confirm({
      title: copy.title,
      message: (
        <>
          <strong>{u.name}</strong> ({u.email}) will be notified.{' '}
          {action === 'SUSPEND' || action === 'BAN' ? 'They’ll be signed out immediately and can’t log in.' : ''}
        </>
      ),
      danger: copy.danger,
      confirmLabel: copy.label,
      input: { label: 'Reason (shared with the user and kept in the audit log)', required: true },
    });
    if (!r.confirmed) return;
    try {
      await act.mutateAsync({ id: u.id, action, reason: r.value });
      toast.success(`${copy.label}: ${u.name}`);
    } catch (err) {
      toast.error(errorMessage(err));
    }
  };

  return (
    <>
      <PageHeader title="Users" subtitle={data ? pluralize(data.meta.total, 'account') : undefined} />
      <FilterBar>
        <Input label="Search" placeholder="Name, email or department" value={q} onChange={(e) => setQ(e.target.value)} type="search" />
        <Select label="Status" value={get('status') ?? ''} onChange={(e) => set({ status: e.target.value })}>
          <option value="">All</option>
          <option value="ACTIVE">Active</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="BANNED">Banned</option>
        </Select>
        <Select label="Role" value={get('role') ?? ''} onChange={(e) => set({ role: e.target.value })}>
          <option value="">All</option>
          <option value="MEMBER">Member</option>
          <option value="ADMIN">Admin</option>
        </Select>
      </FilterBar>
      <DataTable
        caption="Users"
        rows={data?.data}
        loading={isLoading}
        error={error}
        onRetry={() => refetch()}
        rowKey={(u) => u.id}
        emptyTitle="No users found"
        columns={[
          {
            header: 'User',
            cell: (u) => (
              <div className="flex items-center gap-3">
                <Avatar name={u.name} url={u.avatarUrl} size="sm" />
                <div>
                  <Link to={`/users/${u.id}`} className="font-medium text-gray-900 hover:underline">
                    {u.name}
                  </Link>
                  <p className="text-xs text-gray-600">{u.email}</p>
                </div>
              </div>
            ),
          },
          { header: 'Department', cell: (u) => u.department },
          {
            header: 'Status',
            cell: (u) => (
              <div className="flex flex-wrap gap-1">
                <Badge tone={STATUS_TONE[u.status]}>{u.status.toLowerCase()}</Badge>
                {u.role === 'ADMIN' && <Badge tone="blue">admin</Badge>}
                {!u.emailVerified && <Badge>unverified</Badge>}
              </div>
            ),
          },
          {
            header: 'Activity',
            cell: (u) => (
              <span className="text-xs text-gray-700">
                {u.itemCount} items · {u.loanCount} loans
                <br />
                {u.reportCount} reports · {u.warningCount} warnings
              </span>
            ),
          },
          { header: 'Joined', cell: (u) => formatDate(u.createdAt) },
          {
            header: 'Actions',
            cell: (u) =>
              u.role === 'ADMIN' || u.id === me?.id ? (
                <span className="text-xs text-gray-600">—</span>
              ) : (
                <div className="flex flex-wrap gap-1">
                  <Button size="sm" variant="secondary" onClick={() => run(u, 'WARN')}>
                    Warn
                  </Button>
                  {u.status === 'ACTIVE' ? (
                    <>
                      <Button size="sm" variant="secondary" onClick={() => run(u, 'SUSPEND')}>
                        Suspend
                      </Button>
                      <Button size="sm" variant="danger" onClick={() => run(u, 'BAN')}>
                        Ban
                      </Button>
                    </>
                  ) : (
                    <Button size="sm" variant="success" onClick={() => run(u, 'REINSTATE')}>
                      Reinstate
                    </Button>
                  )}
                </div>
              ),
          },
        ]}
      />
      {data && <Pagination page={data.meta.page} totalPages={data.meta.totalPages} onChange={(p) => set({ page: String(p) })} />}
    </>
  );
}
