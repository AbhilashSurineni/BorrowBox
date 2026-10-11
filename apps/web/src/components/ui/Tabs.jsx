import { useRef } from 'react';
import { cn } from '@/lib/utils';
import PropTypes from 'prop-types';

/** WAI-ARIA tabs (arrow keys move between tabs). Content is rendered by the caller. */
export function Tabs({ tabs, value, onChange, label }) {
  const refs = useRef([]);
  const onKey = (e, i) => {
    const dir = e.key === 'ArrowRight' ? 1 : e.key === 'ArrowLeft' ? -1 : 0;
    if (!dir) return;
    e.preventDefault();
    const next = (i + dir + tabs.length) % tabs.length;
    refs.current[next]?.focus();
    onChange(tabs[next].value);
  };
  return (
    <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto border-b border-gray-200">
      {tabs.map((t, i) => {
        const selected = t.value === value;
        return (
          <button
            key={t.value}
            ref={(el) => (refs.current[i] = el)}
            role="tab"
            type="button"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(t.value)}
            onKeyDown={(e) => onKey(e, i)}
            className={cn(
              '-mb-px flex min-h-[44px] items-center gap-2 whitespace-nowrap border-b-2 px-4 text-sm font-medium focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700',
              selected ? 'border-brand-700 text-brand-800' : 'border-transparent text-gray-600 hover:text-gray-900',
            )}
          >
            {t.label}
            {!!t.badge && (
              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-900">
                {t.badge}
                <span className="sr-only"> new</span>
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

Tabs.propTypes = {
  tabs: PropTypes.arrayOf(
    PropTypes.shape({ value: PropTypes.string.isRequired, label: PropTypes.node.isRequired, badge: PropTypes.number }),
  ).isRequired,
  value: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  label: PropTypes.string.isRequired,
};
