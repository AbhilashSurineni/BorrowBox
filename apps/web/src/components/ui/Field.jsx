import { forwardRef, useId } from 'react';
import { cn } from '@/lib/utils';

const control =
  'block w-full rounded-lg border bg-white px-3 py-2.5 text-base text-gray-900 placeholder:text-gray-500 focus:border-brand-700 focus:outline-none focus:ring-2 focus:ring-brand-700/30 disabled:bg-gray-100 sm:text-sm';

function FieldWrap({ label, error, hint, required, className, children }) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errId = error ? `${id}-err` : undefined;
  const describedBy = [hintId, errId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1 block text-sm font-medium text-gray-800">
        {label}
        {required && <span className="text-red-700" aria-hidden> *</span>}
      </label>
      {children({ id, describedBy, invalid: !!error })}
      {hint && !error && (
        <p id={hintId} className="mt-1 text-xs text-gray-600">
          {hint}
        </p>
      )}
      {error && (
        <p id={errId} className="mt-1 text-sm text-red-700" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

export const Input = forwardRef(function Input(
  { label, error, hint, wrapperClassName, className, required, ...rest },
  ref,
) {
  return (
    <FieldWrap label={label} error={error} hint={hint} required={required} className={wrapperClassName}>
      {({ id, describedBy, invalid }) => (
        <input
          ref={ref}
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          required={required}
          className={cn(control, invalid ? 'border-red-600' : 'border-gray-300', className)}
          {...rest}
        />
      )}
    </FieldWrap>
  );
});

export const Textarea = forwardRef(function Textarea(
  { label, error, hint, wrapperClassName, className, required, rows = 4, ...rest },
  ref,
) {
  return (
    <FieldWrap label={label} error={error} hint={hint} required={required} className={wrapperClassName}>
      {({ id, describedBy, invalid }) => (
        <textarea
          ref={ref}
          id={id}
          rows={rows}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          required={required}
          className={cn(control, invalid ? 'border-red-600' : 'border-gray-300', className)}
          {...rest}
        />
      )}
    </FieldWrap>
  );
});

export const Select = forwardRef(function Select(
  { label, error, hint, wrapperClassName, className, required, children, ...rest },
  ref,
) {
  return (
    <FieldWrap label={label} error={error} hint={hint} required={required} className={wrapperClassName}>
      {({ id, describedBy, invalid }) => (
        <select
          ref={ref}
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          required={required}
          className={cn(control, 'pr-8', invalid ? 'border-red-600' : 'border-gray-300', className)}
          {...rest}
        >
          {children}
        </select>
      )}
    </FieldWrap>
  );
});
