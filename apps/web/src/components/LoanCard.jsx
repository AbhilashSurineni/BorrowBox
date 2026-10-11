import { Link } from 'react-router-dom';

import { formatRange } from '@/lib/dates';
import { ItemImage } from './ItemCard';
import { LoanActions } from './LoanActions';
import { LoanStatusChip } from './ui';
import PropTypes from 'prop-types';

export function LoanCard({ loan }) {
  const isOwner = loan.viewerRole === 'OWNER';
  const other = isOwner ? loan.borrower : loan.owner;
  return (
    <article className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex gap-3">
        <ItemImage url={loan.item.coverImageUrl} name={loan.item.name} className="h-16 w-16 shrink-0 rounded-lg text-2xl" />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-start justify-between gap-2">
            <h3 className="font-semibold text-gray-900">
              <Link to={`/loans/${loan.id}`} className="hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700">
                {loan.item.name}
              </Link>
            </h3>
            <LoanStatusChip status={loan.status} />
          </div>
          <p className="mt-0.5 text-sm text-gray-700">{formatRange(loan.startDate, loan.endDate)}</p>
          <p className="text-xs text-gray-600">
            {isOwner ? 'Borrower' : 'Owner'}: {other.name}
          </p>
          {loan.extension.status === 'REQUESTED' && <p className="mt-1 text-xs font-medium text-blue-900">Extension requested</p>}
        </div>
      </div>
      <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-gray-100 pt-3">
        <LoanActions loan={loan} size="sm" />
        <Link to={`/loans/${loan.id}`} className="ml-auto text-sm font-medium text-brand-700 hover:underline">
          Details →
        </Link>
      </div>
    </article>
  );
}

LoanCard.propTypes = { loan: PropTypes.object.isRequired };
