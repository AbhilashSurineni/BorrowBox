import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthProvider';
import { qk } from '../keys';
import { notificationsApi } from './api';

export const useNotifications = (params) =>
  useQuery({
    queryKey: qk.notifications(params),
    queryFn: () => notificationsApi.list({ ...params, limit: 20 }),
    placeholderData: keepPreviousData,
  });

/** Unread count for the header bell; polls every minute while signed in. */
export function useUnreadCount() {
  const { status } = useAuth();
  return useQuery({
    queryKey: qk.unread,
    queryFn: () => notificationsApi.list({ unread: true, limit: 1 }),
    select: (d) => d.meta.unreadCount ?? 0,
    enabled: status === 'authenticated',
    refetchInterval: 60_000,
  });
}

export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: notificationsApi.markRead, onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }) });
}

export function useMarkAllRead() {
  const qc = useQueryClient();
  return useMutation({ mutationFn: notificationsApi.markAllRead, onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }) });
}
