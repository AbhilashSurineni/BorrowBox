import { useNavigate, useSearchParams } from 'react-router-dom';
import { PageContainer } from '@/components/Layout';
import { Button, EmptyState, ErrorState, PageHeader, Pagination, SkeletonList, Tabs, useToast } from '@/components/ui';
import { useMarkAllRead, useMarkRead, useNotifications } from '@/features/notifications/hooks';
import { timeAgo } from '@/lib/dates';
import { errorMessage } from '@/lib/errorMessages';

import { cn } from '@/lib/utils';

const TYPE_ICON = {
  LOAN_REQUESTED: '📥',
  LOAN_APPROVED: '✅',
  LOAN_REJECTED: '🚫',
  LOAN_CANCELLED: '↩️',
  LOAN_EXPIRED: '⌛',
  LOAN_HANDOVER: '🤝',
  LOAN_RETURN: '📦',
  LOAN_EXTENSION: '📅',
  LOAN_STARTING: '🔔',
  DUE_REMINDER: '⏰',
  OVERDUE_REMINDER: '⚠️',
  REVIEW_RECEIVED: '⭐',
  ACCOUNT: '🛡️',
  ANNOUNCEMENT: '📣',
};

/** Screen 10: notification centre with unread highlighting. */
export function NotificationsPage() {
  const [params, setParams] = useSearchParams();
  const unread = params.get('filter') === 'unread';
  const page = Number(params.get('page') ?? 1);
  const { data, isLoading, isError, error, refetch } = useNotifications({ unread: unread || undefined, page });
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();
  const navigate = useNavigate();
  const toast = useToast();
  const unreadCount = data?.meta.unreadCount ?? 0;

  const open = (n) => {
    if (!n.isRead) markRead.mutate(n.id);
    if (n.link) navigate(n.link);
  };

  return (
    <PageContainer>
      <PageHeader
        title="Notifications"
        subtitle={unreadCount ? `${unreadCount} unread` : 'You’re all caught up'}
        actions={
          <Button
            variant="secondary"
            size="sm"
            disabled={!unreadCount}
            loading={markAll.isPending}
            onClick={() => markAll.mutateAsync().then(() => toast.success('All marked as read'), (e) => toast.error(errorMessage(e)))}
          >
            Mark all as read
          </Button>
        }
      />
      <Tabs
        label="Filter notifications"
        value={unread ? 'unread' : 'all'}
        onChange={(v) => setParams(v === 'unread' ? { filter: 'unread' } : {})}
        tabs={[
          { value: 'all', label: 'All' },
          { value: 'unread', label: 'Unread', badge: unreadCount },
        ]}
      />
      <div className="mt-4">
        {isError ? (
          <ErrorState error={error} onRetry={() => refetch()} />
        ) : isLoading ? (
          <SkeletonList rows={4} />
        ) : !data?.data.length ? (
          <EmptyState icon="🔔" title={unread ? 'No unread notifications' : 'No notifications yet'} message="Requests, approvals, reminders and reviews will appear here." />
        ) : (
          <>
            <ul className="divide-y divide-gray-100 overflow-hidden rounded-xl border border-gray-200 bg-white">
              {data.data.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    onClick={() => open(n)}
                    className={cn(
                      'flex w-full gap-3 px-4 py-3 text-left hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand-700',
                      !n.isRead && 'bg-brand-50',
                    )}
                  >
                    <span className="text-xl" aria-hidden>
                      {TYPE_ICON[n.type] ?? '🔔'}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className={cn('block text-sm text-gray-900', !n.isRead && 'font-semibold')}>
                        {!n.isRead && <span className="sr-only">Unread: </span>}
                        {n.title}
                      </span>
                      <span className="mt-0.5 block whitespace-pre-line text-sm text-gray-700">{n.body}</span>
                      <span className="mt-1 block text-xs text-gray-600">{timeAgo(n.createdAt)}</span>
                    </span>
                    {!n.isRead && <span className="mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full bg-brand-700" aria-hidden />}
                  </button>
                </li>
              ))}
            </ul>
            <Pagination page={data.meta.page} totalPages={data.meta.totalPages} onChange={(p) => setParams({ ...(unread ? { filter: 'unread' } : {}), page: String(p) })} />
          </>
        )}
      </div>
    </PageContainer>
  );
}
