import { cn } from '@/lib/utils';
import PropTypes from 'prop-types';
import { LoanStatus } from '@/lib/constants';

/**
 * Section 12 chip colours: Pending amber, Approved blue, Active green, Overdue red,
 * Returned/Completed grey. Each pairing is ≥ 7:1 contrast (WCAG AA+), and the chip
 * always carries a text label so status never relies on colour alone.
 */
const tone = {
  amber: 'bg-amber-100 text-amber-900 ring-amber-300',
  blue: 'bg-blue-100 text-blue-900 ring-blue-300',
  green: 'bg-green-100 text-green-900 ring-green-300',
  red: 'bg-red-100 text-red-900 ring-red-300',
  grey: 'bg-gray-100 text-gray-800 ring-gray-300',
  slate: 'bg-white text-gray-700 ring-gray-300',
};

const LOAN = {
  PENDING: { label: 'Pending', tone: 'amber' },
  APPROVED: { label: 'Approved', tone: 'blue' },
  ACTIVE: { label: 'Active', tone: 'green' },
  OVERDUE: { label: 'Overdue', tone: 'red' },
  RETURNED: { label: 'Returned', tone: 'grey' },
  COMPLETED: { label: 'Completed', tone: 'grey' },
  REJECTED: { label: 'Rejected', tone: 'slate' },
  CANCELLED: { label: 'Cancelled', tone: 'slate' },
  EXPIRED: { label: 'Expired', tone: 'slate' },
};

const ITEM = {
  AVAILABLE: { label: 'Available', tone: 'green' },
  RESERVED: { label: 'Reserved', tone: 'blue' },
  ON_LOAN: { label: 'On loan', tone: 'amber' },
  PAUSED: { label: 'Paused', tone: 'grey' },
  REMOVED: { label: 'Removed', tone: 'red' },
};

function Chip({ label, t, className }) {
  return (
    <span className={cn('inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset', tone[t], className)}>
      <span className="h-1.5 w-1.5 rounded-full bg-current" aria-hidden />
      {label}
    </span>
  );
}

export function LoanStatusChip({ status, className }) {
  return <Chip label={LOAN[status].label} t={LOAN[status].tone} className={className} />;
}

export function ItemStatusChip({ status, className }) {
  return <Chip label={ITEM[status].label} t={ITEM[status].tone} className={className} />;
}

export function Badge({ children, tone: t = 'grey', className }) {
  return <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ring-1 ring-inset', tone[t], className)}>{children}</span>;
}

export const loanStatusLabel = (s) => LOAN[s].label;

LoanStatusChip.propTypes = { status: PropTypes.oneOf(LoanStatus).isRequired, className: PropTypes.string };
ItemStatusChip.propTypes = {
  status: PropTypes.oneOf(['AVAILABLE', 'RESERVED', 'ON_LOAN', 'PAUSED', 'REMOVED']).isRequired,
  className: PropTypes.string,
};
Badge.propTypes = { children: PropTypes.node, tone: PropTypes.oneOf(Object.keys(tone)), className: PropTypes.string };
