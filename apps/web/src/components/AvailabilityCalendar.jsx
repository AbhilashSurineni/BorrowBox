import { useMemo, useState } from 'react';
import { addDays, inclusiveDays, parseDate, toDateStr, todayStr } from '@/lib/dates';

import { cn } from '@/lib/utils';
import PropTypes from 'prop-types';

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

function monthStart(s) {
  return `${s.slice(0, 7)}-01`;
}

/** FR-21 availability calendar; also the date-range picker in the request modal. */
export function AvailabilityCalendar({ blocked, value, onChange, onInvalid, maxDays, className }) {
  const today = todayStr();
  const [month, setMonth] = useState(() => monthStart(value?.start ?? today));
  const selectable = !!onChange;

  const blockedOn = (d) => blocked.find((b) => d >= b.startDate && d <= b.endDate);
  const rangeHitsBlocked = (a, b) => blocked.some((r) => a <= r.endDate && r.startDate <= b);

  const days = useMemo(() => {
    const first = parseDate(month);
    const offset = (first.getDay() + 6) % 7; // Monday-first grid
    const gridStart = addDays(month, -offset);
    return Array.from({ length: 42 }, (_, i) => addDays(gridStart, i));
  }, [month]);

  const monthLabel = parseDate(month).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });
  const shiftMonth = (n) => {
    const d = parseDate(month);
    d.setMonth(d.getMonth() + n);
    setMonth(toDateStr(d));
  };

  const select = (d) => {
    if (!onChange) return;
    const { start, end } = value ?? {};
    if (!start || end || d < start) return onChange({ start: d, end: undefined });
    if (rangeHitsBlocked(start, d)) return onInvalid?.('Your dates can’t include days when the item is already booked.');
    if (maxDays && inclusiveDays(start, d) > maxDays) return onInvalid?.(`The owner allows at most ${maxDays} days.`);
    onChange({ start, end: d });
  };

  return (
    <div className={cn('select-none', className)}>
      <div className="mb-2 flex items-center justify-between">
        <button type="button" onClick={() => shiftMonth(-1)} disabled={month <= monthStart(today)} className="rounded-lg p-2 text-gray-700 hover:bg-gray-100 disabled:opacity-40" aria-label="Previous month">
          ‹
        </button>
        <p className="text-sm font-semibold text-gray-900" aria-live="polite">
          {monthLabel}
        </p>
        <button type="button" onClick={() => shiftMonth(1)} className="rounded-lg p-2 text-gray-700 hover:bg-gray-100" aria-label="Next month">
          ›
        </button>
      </div>
      <div role="grid" aria-label={`Availability for ${monthLabel}`} className="grid grid-cols-7 gap-1 text-center text-sm">
        {WEEKDAYS.map((w) => (
          <div key={w} role="columnheader" className="py-1 text-xs font-medium text-gray-600">
            {w}
          </div>
        ))}
        {days.map((d) => {
          const inMonth = d.slice(0, 7) === month.slice(0, 7);
          const past = d < today;
          const block = blockedOn(d);
          const isStart = value?.start === d;
          const isEnd = value?.end === d;
          const inRange = !!value?.start && !!value?.end && d > value.start && d < value.end;
          const label = `${parseDate(d).toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}${
            block ? `, ${block.status === 'ON_LOAN' ? 'on loan' : 'reserved'}` : past ? ', past' : ', available'
          }${isStart ? ', start date' : ''}${isEnd ? ', end date' : ''}`;
          const cls = cn(
            'flex h-10 items-center justify-center rounded-md',
            !inMonth && 'opacity-40',
            past && 'text-gray-400',
            block && !past && 'bg-red-50 text-red-800 line-through decoration-red-700',
            !block && !past && 'text-gray-900',
            inRange && 'bg-brand-100 text-brand-900',
            (isStart || isEnd) && 'bg-brand-700 font-semibold text-white no-underline',
            d === today && !isStart && !isEnd && 'ring-1 ring-inset ring-brand-700',
          );
          return selectable ? (
            <button key={d} type="button" role="gridcell" aria-label={label} aria-selected={isStart || isEnd || inRange} disabled={past || !!block} onClick={() => select(d)} className={cn(cls, !past && !block && 'hover:bg-brand-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700')}>
              {Number(d.slice(8))}
            </button>
          ) : (
            <div key={d} role="gridcell" aria-label={label} className={cls}>
              {Number(d.slice(8))}
            </div>
          );
        })}
      </div>
      <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-gray-700" aria-label="Legend">
        <li className="flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-sm border border-gray-300 bg-white" aria-hidden /> Available
        </li>
        <li className="flex items-center gap-1.5">
          <span className="h-3 w-3 rounded-sm bg-red-100" aria-hidden /> Booked
        </li>
        {selectable && (
          <li className="flex items-center gap-1.5">
            <span className="h-3 w-3 rounded-sm bg-brand-700" aria-hidden /> Your dates
          </li>
        )}
      </ul>
    </div>
  );
}

const rangeShape = PropTypes.shape({ start: PropTypes.string, end: PropTypes.string });

AvailabilityCalendar.propTypes = {
  blocked: PropTypes.arrayOf(
    PropTypes.shape({ startDate: PropTypes.string.isRequired, endDate: PropTypes.string.isRequired, status: PropTypes.string }),
  ).isRequired,
  value: rangeShape,
  onChange: PropTypes.func,
  onInvalid: PropTypes.func,
  maxDays: PropTypes.number,
  className: PropTypes.string,
};
