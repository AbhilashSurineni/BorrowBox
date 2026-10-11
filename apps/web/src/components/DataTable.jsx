import { EmptyState, ErrorState, Skeleton } from './ui';

/** Admin data table: horizontally scrollable on small screens, with loading/empty/error states. */
export function DataTable({
  columns,
  rows,
  rowKey,
  loading,
  error,
  onRetry,
  emptyTitle = 'Nothing here',
  caption,
}

) {
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (loading)
    return (
      <div className="space-y-2" role="status" aria-label="Loading">
        {Array.from({ length: 5 }, (_, i) => (
          <Skeleton key={i} className="h-12 w-full" />
        ))}
      </div>
    );
  if (!rows?.length) return <EmptyState icon="🗂️" title={emptyTitle} message="Try changing the filters." />;
  return (
    <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white">
      <table className="min-w-[720px] w-full text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-gray-50 text-xs uppercase tracking-wide text-gray-700">
          <tr>
            {columns.map((c) => (
              <th key={c.header} scope="col" className={`px-4 py-3 font-semibold ${c.className ?? ''}`}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {rows.map((r) => (
            <tr key={rowKey(r)} className="align-top hover:bg-gray-50">
              {columns.map((c) => (
                <td key={c.header} className={`px-4 py-3 text-gray-800 ${c.className ?? ''}`}>
                  {c.cell(r)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Filter bar row used above admin tables. */
export function FilterBar({ children }) {
  return <div className="mb-4 grid gap-3 sm:grid-cols-[1fr_auto_auto] sm:items-end">{children}</div>;
}
