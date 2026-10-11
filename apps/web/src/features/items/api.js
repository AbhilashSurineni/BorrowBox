import { apiDelete, apiGet, apiPatch, apiPost } from '@/lib/api';

export const itemsApi = {
  categories: () => apiGet('/categories'),
  list: (params, signal) => apiGet('/items', { params, signal }),
  get: (id) => apiGet(`/items/${id}`),
  availability: (id) => apiGet(`/items/${id}/availability`),
  mine: (params) => apiGet('/users/me/items', { params }),
  /** `form` is FormData (fields + "images" files). */
  create: (form) => apiPost('/items', form),
  update: (id, form) => apiPatch(`/items/${id}`, form),
  remove: (id) => apiDelete(`/items/${id}`),
  togglePause: (id) => apiPost(`/items/${id}/pause`),
};
