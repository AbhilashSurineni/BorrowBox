import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { qk } from '../keys';
import { itemsApi } from './api';

export const useCategories = () => useQuery({ queryKey: qk.categories, queryFn: itemsApi.categories, staleTime: 10 * 60_000 });

export const useItems = (params) =>
  useQuery({
    queryKey: qk.items(params),
    queryFn: ({ signal }) => itemsApi.list(params, signal),
    placeholderData: keepPreviousData,
  });

export const useItem = (id) => useQuery({ queryKey: qk.item(id), queryFn: () => itemsApi.get(id) });

export const useAvailability = (id, enabled = true) =>
  useQuery({ queryKey: qk.availability(id), queryFn: () => itemsApi.availability(id), enabled });

export const useMyItems = (status, page) =>
  useQuery({
    queryKey: qk.myItems({ status, page }),
    queryFn: () => itemsApi.mine({ status, page, limit: 20 }),
    placeholderData: keepPreviousData,
  });

function useInvalidateItems() {
  const qc = useQueryClient();
  return () => Promise.all([qc.invalidateQueries({ queryKey: ['items'] }), qc.invalidateQueries({ queryKey: ['my-items'] })]);
}

export function useSaveItem(id) {
  const invalidate = useInvalidateItems();
  return useMutation({
    mutationFn: (form) => (id ? itemsApi.update(id, form) : itemsApi.create(form)),
    onSuccess: invalidate,
  });
}

export function useDeleteItem() {
  const invalidate = useInvalidateItems();
  return useMutation({ mutationFn: itemsApi.remove, onSuccess: invalidate });
}

export function useTogglePause() {
  const invalidate = useInvalidateItems();
  return useMutation({ mutationFn: itemsApi.togglePause, onSuccess: invalidate });
}
