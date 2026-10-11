import { apiDelete, apiGet, apiPatch, apiPost } from '@/lib/api';

export const usersApi = {
  profile: (id) => apiGet(`/users/${id}`),
  reviews: (id, params) => apiGet(`/users/${id}/reviews`, { params }),
  /** `form` is FormData (profile fields + optional "avatar" file). */
  updateMe: (form) => apiPatch('/users/me', form),
  deleteMe: () => apiDelete('/users/me'),
  report: (body) => apiPost('/reports', body),
};
