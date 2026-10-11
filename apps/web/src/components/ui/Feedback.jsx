import { cn } from '@/lib/utils';
import { errorMessage } from '@/lib/errorMessages';
import { Button } from './Button';
import PropTypes from 'prop-types';

export function Skeleton({ className }) {
  return <div className={cn('animate-pulse rounded-md bg-gray-200', className)} aria-hidden />;
}

export function SkeletonList({ rows = 3, className }) {
  return (
    <div className={cn('space-y-3', className)} role="status" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex gap-3 rounded-xl border border-gray-200 bg-white p-4">
          <Skeleton className="h-16 w-16 shrink-0" />
          <div className="flex-1 space-y-2">
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-3 w-3/4" />
            <Skeleton className="h-3 w-1/3" />
          </div>
        </div>
      ))}
    </div>
  );
}

export function EmptyState({ title, message, action, icon = '📦' }) {
  return (
    <div className="rounded-xl border border-dashed border-gray-300 bg-white px-6 py-12 text-center">
      <div className="text-4xl" aria-hidden>
        {icon}
      </div>
      <h3 className="mt-3 text-base font-semibold text-gray-900">{title}</h3>
      {message && <p className="mx-auto mt-1 max-w-md text-sm text-gray-600">{message}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  );
}

export function ErrorState({ error, onRetry }) {
  return (
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-6 py-8 text-center">
      <p className="font-medium text-red-900">{errorMessage(error)}</p>
      {onRetry && (
        <Button variant="secondary" className="mt-4" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  );
}

export function Alert({ tone = 'info', children, className }) {
  const tones = {
    info: 'border-blue-200 bg-blue-50 text-blue-900',
    warning: 'border-amber-300 bg-amber-50 text-amber-900',
    error: 'border-red-200 bg-red-50 text-red-900',
    success: 'border-green-200 bg-green-50 text-green-900',
  };
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={cn('rounded-lg border px-4 py-3 text-sm', tones[tone], className)}>
      {children}
    </div>
  );
}

EmptyState.propTypes = { title: PropTypes.string.isRequired, message: PropTypes.node, action: PropTypes.node, icon: PropTypes.string };
ErrorState.propTypes = { error: PropTypes.any, onRetry: PropTypes.func };
Alert.propTypes = { tone: PropTypes.oneOf(['info', 'warning', 'error', 'success']), children: PropTypes.node, className: PropTypes.string };
