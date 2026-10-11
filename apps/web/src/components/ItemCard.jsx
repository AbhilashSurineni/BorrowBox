import { Link } from 'react-router-dom';

import { formatDate, todayStr } from '@/lib/dates';
import { CONDITION_LABELS } from '@/lib/utils';
import { ItemStatusChip, RatingStars } from './ui';
import PropTypes from 'prop-types';

export function ItemImage({ url, name, className = '' }) {
  if (url) return <img src={url} alt={name} loading="lazy" className={`object-cover ${className}`} />;
  return (
    <div className={`flex items-center justify-center bg-gradient-to-br from-brand-50 to-gray-100 text-4xl ${className}`} aria-hidden>
      📦
    </div>
  );
}

export function ItemCard({ item }) {
  const next = item.nextAvailableDate && item.nextAvailableDate > todayStr() ? item.nextAvailableDate : null;
  return (
    <article className="group relative flex flex-col overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm transition-shadow focus-within:ring-2 focus-within:ring-brand-700 hover:shadow-md">
      <ItemImage url={item.coverImageUrl} name={item.name} className="aspect-[4/3] w-full" />
      <div className="flex flex-1 flex-col gap-2 p-4">
        <div className="flex items-start justify-between gap-2">
          <h3 className="line-clamp-2 font-semibold text-gray-900">
            <Link to={`/items/${item.id}`} className="after:absolute after:inset-0 focus:outline-none">
              {item.name}
            </Link>
          </h3>
          <ItemStatusChip status={item.status} className="shrink-0" />
        </div>
        <p className="text-xs text-gray-600">
          {item.category.name} · {CONDITION_LABELS[item.condition]} · up to {item.maxLoanDays} days
        </p>
        <p className="line-clamp-1 text-sm text-gray-700">📍 {item.pickupLocation}</p>
        <div className="mt-auto flex items-center justify-between gap-2 pt-2 text-xs text-gray-700">
          <span className="truncate">{item.owner.name}</span>
          <RatingStars value={item.owner.ratingAvg} count={item.owner.ratingCount} />
        </div>
        {next && <p className="text-xs font-medium text-blue-900">Free from {formatDate(next, { day: 'numeric', month: 'short' })}</p>}
      </div>
    </article>
  );
}

export function ItemCardSkeleton() {
  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white" aria-hidden>
      <div className="aspect-[4/3] animate-pulse bg-gray-200" />
      <div className="space-y-2 p-4">
        <div className="h-4 w-3/4 animate-pulse rounded bg-gray-200" />
        <div className="h-3 w-1/2 animate-pulse rounded bg-gray-200" />
        <div className="h-3 w-2/3 animate-pulse rounded bg-gray-200" />
      </div>
    </div>
  );
}

ItemCard.propTypes = {
  item: PropTypes.shape({
    id: PropTypes.string.isRequired,
    name: PropTypes.string.isRequired,
    status: PropTypes.string.isRequired,
    condition: PropTypes.string.isRequired,
    maxLoanDays: PropTypes.number.isRequired,
    pickupLocation: PropTypes.string.isRequired,
    coverImageUrl: PropTypes.string,
    nextAvailableDate: PropTypes.string,
    category: PropTypes.shape({ name: PropTypes.string.isRequired }).isRequired,
    owner: PropTypes.shape({ name: PropTypes.string.isRequired, ratingAvg: PropTypes.number, ratingCount: PropTypes.number }).isRequired,
  }).isRequired,
};
