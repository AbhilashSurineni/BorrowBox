import { useId } from 'react';
import { cn } from '@/lib/utils';
import PropTypes from 'prop-types';

function Star({ filled, className }) {
  return (
    <svg viewBox="0 0 20 20" className={cn('h-4 w-4', filled ? 'text-amber-500' : 'text-gray-300', className)} fill="currentColor" aria-hidden>
      <path d="M10 1.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L10 14.9l-5.2 2.7 1-5.8L1.5 7.7l5.9-.9z" />
    </svg>
  );
}

export function RatingStars({ value, count, className }) {
  if (count === 0) return <span className={cn('text-xs text-gray-600', className)}>No ratings yet</span>;
  return (
    <span className={cn('inline-flex items-center gap-1', className)}>
      <span className="sr-only">Rated {value.toFixed(1)} out of 5</span>
      <span className="flex" aria-hidden>
        {[1, 2, 3, 4, 5].map((n) => (
          <Star key={n} filled={value >= n - 0.25} />
        ))}
      </span>
      <span className="text-xs font-medium text-gray-700" aria-hidden>
        {value.toFixed(1)}
        {count !== undefined && <span className="text-gray-600"> ({count})</span>}
      </span>
    </span>
  );
}

/** Accessible 1–5 rating picker (radio group). */
export function RatingInput({ value, onChange, label = 'Rating' }) {
  const name = useId();
  return (
    <fieldset>
      <legend className="mb-1 text-sm font-medium text-gray-800">{label}</legend>
      <div className="flex gap-1">
        {[1, 2, 3, 4, 5].map((n) => (
          <label key={n} className="cursor-pointer rounded p-1 focus-within:outline focus-within:outline-2 focus-within:outline-brand-700">
            <input type="radio" name={name} value={n} checked={value === n} onChange={() => onChange(n)} className="sr-only" />
            <Star filled={value >= n} className="h-8 w-8" />
            <span className="sr-only">{n} star{n > 1 ? 's' : ''}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

RatingStars.propTypes = { value: PropTypes.number.isRequired, count: PropTypes.number, className: PropTypes.string };
RatingInput.propTypes = { value: PropTypes.number.isRequired, onChange: PropTypes.func.isRequired, label: PropTypes.string };
