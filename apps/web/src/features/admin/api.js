import { apiDelete, apiGet, apiPatch, apiPost } from '@/lib/api';

export const adminApi = {
  stats: () => apiGet('/admin/stats'),
  /** Paged lists: users | items | loans | reports | announcements. */
  list: (resource, params) => apiGet(`/admin/${resource}`, { params }),
  setUserStatus: (id, action, reason) => apiPatch(`/admin/users/${id}/status`, { action, reason }),
  removeItem: (id, reason) => apiPatch(`/admin/items/${id}/remove`, { reason }),
  remindLoan: (id, message) => apiPost(`/admin/loans/${id}/remind`, message ? { message } : {}),
  updateReport: (id, status, adminNote) => apiPatch(`/admin/reports/${id}`, { status, adminNote }),
  categories: () => apiGet('/admin/categories'),
  createCategory: (body) => apiPost('/admin/categories', body),
  updateCategory: (id, body) => apiPatch(`/admin/categories/${id}`, body),
  deleteCategory: (id) => apiDelete(`/admin/categories/${id}`),
  settings: () => apiGet('/admin/settings'),
  updateSettings: (patch) => apiPatch('/admin/settings', patch),
  announce: (body) => apiPost('/admin/announcements', body),
};
