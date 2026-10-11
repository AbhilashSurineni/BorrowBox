import { apiGet, apiPatch, apiPost } from '@/lib/api';

export const notificationsApi = {
  list: (params) => apiGet('/notifications', { params }),
  markRead: (id) => apiPatch(`/notifications/${id}/read`),
  markAllRead: () => apiPost('/notifications/read-all'),
};
