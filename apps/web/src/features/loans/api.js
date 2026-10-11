import { apiGet, apiPost } from '@/lib/api';

export const loansApi = {
  list: (params) => apiGet('/loans', { params }),
  get: (id) => apiGet(`/loans/${id}`),
  request: (itemId, body) => apiPost(`/items/${itemId}/loans`, body),
  approve: (id) => apiPost(`/loans/${id}/approve`),
  reject: (id, reason) => apiPost(`/loans/${id}/reject`, { reason }),
  cancel: (id, reason) => apiPost(`/loans/${id}/cancel`, reason ? { reason } : {}),
  handover: (id) => apiPost(`/loans/${id}/handover`),
  markReturned: (id, conditionNote) => apiPost(`/loans/${id}/return`, conditionNote ? { conditionNote } : {}),
  requestExtension: (id, until, message) => apiPost(`/loans/${id}/extension`, { until, ...(message ? { message } : {}) }),
  decideExtension: (id, approve) => apiPost(`/loans/${id}/extension/decision`, { approve }),
  review: (id, body) => apiPost(`/loans/${id}/reviews`, body),
};
