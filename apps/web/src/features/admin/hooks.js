import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { qk } from '../keys';
import { adminApi } from './api';

export const useAdminStats = () => useQuery({ queryKey: qk.admin('stats'), queryFn: adminApi.stats });

const list = (resource) =>
  function useList(params) {
    return useQuery({
      queryKey: qk.admin(resource, params),
      queryFn: () => adminApi.list(resource, params),
      placeholderData: keepPreviousData,
    });
  };

export const useAdminUsers = list('users');
export const useAdminItems = list('items');
export const useAdminLoans = list('loans');
export const useAdminReports = list('reports');
export const useAnnouncements = list('announcements');

export const useAdminCategories = () => useQuery({ queryKey: qk.admin('categories'), queryFn: adminApi.categories });

export const useSettings = () => useQuery({ queryKey: qk.admin('settings'), queryFn: adminApi.settings });

/** Generic admin write; refreshes all admin data (and public categories/items) on success. */
export function useAdminMutation(fn) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['admin'] });
      void qc.invalidateQueries({ queryKey: ['categories'] });
      void qc.invalidateQueries({ queryKey: ['items'] });
    },
  });
}
