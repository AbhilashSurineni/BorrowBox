import { apiGet, apiPost } from '@/lib/api';

export const authApi = {
  login: (email, password) => apiPost('/auth/login', { email, password }),
  logout: () => apiPost('/auth/logout'),
  register: (body) => apiPost('/auth/register', body),
  verifyEmail: (token) => apiGet('/auth/verify-email', { params: { token } }),
  resendVerification: (email) => apiPost('/auth/resend-verification', { email }),
  forgotPassword: (email) => apiPost('/auth/forgot-password', { email }),
  resetPassword: (token, password) => apiPost('/auth/reset-password', { token, password }),
};
