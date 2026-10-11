import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { qk } from '../keys';
import { usersApi } from './api';

export const useProfile = (id) => useQuery({ queryKey: qk.profile(id ?? ''), queryFn: () => usersApi.profile(id), enabled: !!id });

export const useUserReviews = (id, page) =>
  useQuery({
    queryKey: qk.userReviews(id, page),
    queryFn: () => usersApi.reviews(id, { page, limit: 10 }),
    placeholderData: keepPreviousData,
  });

export const useUpdateMe = () => useMutation({ mutationFn: usersApi.updateMe });

export const useDeleteMe = () => useMutation({ mutationFn: usersApi.deleteMe });

export const useCreateReport = () => useMutation({ mutationFn: usersApi.report });
