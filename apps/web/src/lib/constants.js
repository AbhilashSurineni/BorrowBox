/**
 * API contract constants (enum strings and defaults) used by the UI. The Spring Boot API
 * (apps/api) is the source of truth; keep these in sync with its enums and Settings.DEFAULTS.
 */

export const ItemCondition = ['NEW', 'GOOD', 'FAIR', 'WORN'];

export const ContactPreference = ['EMAIL', 'PHONE', 'BOTH'];

export const LoanStatus = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'CANCELLED',
  'EXPIRED',
  'ACTIVE',
  'OVERDUE',
  'RETURNED',
  'COMPLETED',
];

export const ReportReason = ['SPAM', 'INAPPROPRIATE', 'FRAUD', 'DAMAGED_ITEM', 'NOT_RETURNED', 'HARASSMENT', 'OTHER'];

export const DEFAULT_SETTINGS = {
  max_loan_days_default: 14,
  request_expiry_hours: 48,
  max_active_loans: 3,
  max_pending_requests: 5,
  reminder_hour: 8,
  auto_complete_days: 7,
  auto_suspend_report_threshold: 3,
};
