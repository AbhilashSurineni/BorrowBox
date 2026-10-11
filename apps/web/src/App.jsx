import { lazy, Suspense } from 'react';
import { createBrowserRouter, Navigate, RouterProvider } from 'react-router-dom';
import { Layout } from '@/components/Layout';
import { PageSpinner } from '@/components/ui';
import { GuestOnly, RequireAdmin, RequireAuth } from '@/features/auth/guards';
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage, VerifyEmailPage } from '@/pages/AuthPages';
import { BrowsePage } from '@/pages/BrowsePage';
import { ItemDetailPage } from '@/pages/ItemDetailPage';
import { ItemFormPage } from '@/pages/ItemFormPage';
import { LandingPage } from '@/pages/LandingPage';
import { LoanDetailPage } from '@/pages/LoanDetailPage';
import { MyItemsPage } from '@/pages/MyItemsPage';
import { MyLoansPage } from '@/pages/MyLoansPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { NotificationsPage } from '@/pages/NotificationsPage';
import { MyProfilePage, UserProfilePage } from '@/pages/ProfilePages';

// Admin screens (and Recharts) are code-split away from the member bundle.
const AdminLayout = lazy(() => import('@/pages/admin/AdminLayout'));
const AdminDashboardPage = lazy(() => import('@/pages/admin/AdminDashboardPage'));
const AdminUsersPage = lazy(() => import('@/pages/admin/AdminUsersPage'));
const AdminItemsPage = lazy(() => import('@/pages/admin/AdminItemsPage'));
const AdminLoansPage = lazy(() => import('@/pages/admin/AdminLoansPage'));
const AdminReportsPage = lazy(() => import('@/pages/admin/AdminReportsPage'));
const AdminSettingsPage = lazy(() => import('@/pages/admin/AdminSettingsPage'));

const lazyEl = (C) => (
  <Suspense fallback={<PageSpinner />}>
    <C />
  </Suspense>
);

const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: '/', element: <LandingPage /> },
      { path: '/browse', element: <BrowsePage /> },
      { path: '/items/:id', element: <ItemDetailPage /> },
      { path: '/users/:id', element: <UserProfilePage /> },
      { path: '/verify-email', element: <VerifyEmailPage /> },
      { path: '/reset-password', element: <ResetPasswordPage /> },
      {
        element: <GuestOnly />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/register', element: <RegisterPage /> },
          { path: '/forgot-password', element: <ForgotPasswordPage /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          { path: '/items/new', element: <ItemFormPage /> },
          { path: '/items/:id/edit', element: <ItemFormPage /> },
          { path: '/my/items', element: <MyItemsPage /> },
          { path: '/my/loans', element: <MyLoansPage /> },
          { path: '/loans/:id', element: <LoanDetailPage /> },
          { path: '/notifications', element: <NotificationsPage /> },
          { path: '/profile', element: <MyProfilePage /> },
        ],
      },
      {
        element: <RequireAdmin />,
        children: [
          {
            path: '/admin',
            element: lazyEl(AdminLayout),
            children: [
              { index: true, element: lazyEl(AdminDashboardPage) },
              { path: 'users', element: lazyEl(AdminUsersPage) },
              { path: 'items', element: lazyEl(AdminItemsPage) },
              { path: 'loans', element: lazyEl(AdminLoansPage) },
              { path: 'reports', element: lazyEl(AdminReportsPage) },
              { path: 'settings', element: lazyEl(AdminSettingsPage) },
            ],
          },
        ],
      },
      { path: '/home', element: <Navigate to="/browse" replace /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);

export function App() {
  return <RouterProvider router={router} />;
}
