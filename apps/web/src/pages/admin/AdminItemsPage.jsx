import { pluralize } from '@/lib/utils';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { DataTable, FilterBar } from '@/components/DataTable';
import { ItemImage } from '@/components/ItemCard';
import { Badge, Button, Input, PageHeader, Pagination, Select, useConfirm, useToast } from '@/components/ui';
import { useAdminItems, useAdminMutation } from '@/features/admin/hooks';
import { useCategories } from '@/features/items/hooks';
import { adminApi } from '@/features/admin/api';
import { formatDate } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';
import { useUrlParams } from '@/lib/useUrlParams';
import { useDebounced } from './AdminUsersPage';

/** Screen 13 (items): all items including removed; take-down action. */
export default function AdminItemsPage() {
  const { get, set, page } = useUrlParams();
  const confirm = useConfirm();
  const toast = useToast();
  const { data: categories } = useCategories();
  const [q, setQ] = useState(get('q') ?? '');
  const dq = useDebounced(q);
  useEffect(() => {
    if (dq !== (get('q') ?? '')) set({ q: dq || undefined });
  }, [dq, get, set]);

  const removed = get('removed');
  const { data, isLoading, error, refetch } = useAdminItems({ q: get('q'), category: get('category'), removed, page, limit: 20 });
  const takeDown = useAdminMutation((v) => adminApi.removeItem(v.id, v.reason));

  return (
    <>
      <PageHeader title="Items" subtitle={data ? pluralize(data.meta.total, 'item') : undefined} />
      <FilterBar>
        <Input label="Search" type="search" placeholder="Name or description" value={q} onChange={(e) => setQ(e.target.value)} />
        <Select label="Category" value={get('category') ?? ''} onChange={(e) => set({ category: e.target.value })}>
          <option value="">All</option>
          {categories?.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </Select>
        <Select label="Visibility" value={removed ?? ''} onChange={(e) => set({ removed: e.target.value })}>
          <option value="">All</option>
          <option value="false">Live</option>
          <option value="true">Removed</option>
        </Select>
      </FilterBar>
      <DataTable
        caption="Items"
        rows={data?.data}
        loading={isLoading}
        error={error}
        onRetry={() => refetch()}
        rowKey={(i) => i.id}
        emptyTitle="No items found"
        columns={[
          {
            header: 'Item',
            cell: (i) => (
              <div className="flex items-center gap-3">
                <ItemImage url={i.coverImageUrl} name={i.name} className="h-10 w-10 shrink-0 rounded-md text-lg" />
                <div>
                  <Link to={`/items/${i.id}`} className="font-medium text-gray-900 hover:underline">
                    {i.name}
                  </Link>
                  <p className="text-xs text-gray-600">{i.category.name}</p>
                </div>
              </div>
            ),
          },
          {
            header: 'Owner',
            cell: (i) => (
              <>
                <Link to={`/users/${i.owner.id}`} className="hover:underline">
                  {i.owner.name}
                </Link>
                <p className="text-xs text-gray-600">{i.owner.email}</p>
              </>
            ),
          },
          {
            header: 'State',
            cell: (i) =>
              i.isRemoved ? (
                <>
                  <Badge tone="red">removed</Badge>
                  {i.removedReason && <p className="mt-1 max-w-[200px] text-xs text-gray-600">{i.removedReason}</p>}
                </>
              ) : i.isPaused ? (
                <Badge>paused</Badge>
              ) : (
                <Badge tone="green">live</Badge>
              ),
          },
          { header: 'Loans', cell: (i) => i.loanCount },
          { header: 'Listed', cell: (i) => formatDate(i.createdAt) },
          {
            header: 'Actions',
            cell: (i) =>
              !i.isRemoved && (
                <Button
                  size="sm"
                  variant="danger"
                  onClick={async () => {
                    const r = await confirm({
                      title: `Remove “${i.name}”?`,
                      message: 'The item is hidden, pending requests are declined and upcoming approved loans are cancelled. Active loans continue.',
                      danger: true,
                      confirmLabel: 'Remove item',
                      input: { label: 'Reason (shared with the owner)', required: true },
                    });
                    if (!r.confirmed) return;
                    try {
                      await takeDown.mutateAsync({ id: i.id, reason: r.value });
                      toast.success('Item removed');
                    } catch (err) {
                      toast.error(errorMessage(err));
                    }
                  }}
                >
                  Remove
                </Button>
              ),
          },
        ]}
      />
      {data && <Pagination page={data.meta.page} totalPages={data.meta.totalPages} onChange={(p) => set({ page: String(p) })} />}
    </>
  );
}
