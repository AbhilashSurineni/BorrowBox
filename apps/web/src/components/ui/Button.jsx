import { forwardRef } from 'react';
import { Link } from 'react-router-dom';
import { cn } from '@/lib/utils';
import { Spinner } from './Spinner';
import PropTypes from 'prop-types';

const base =
  'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700 disabled:cursor-not-allowed disabled:opacity-60';
const variants = {
  primary: 'bg-brand-700 text-white hover:bg-brand-800',
  secondary: 'border border-gray-300 bg-white text-gray-900 hover:bg-gray-50',
  danger: 'bg-red-700 text-white hover:bg-red-800',
  success: 'bg-green-700 text-white hover:bg-green-800',
  ghost: 'text-gray-700 hover:bg-gray-100',
};
const sizes = {
  sm: 'min-h-[36px] px-3 text-sm',
  md: 'min-h-[44px] px-4 text-sm',
  lg: 'min-h-[48px] px-5 text-base',
};

export const buttonClass = (variant = 'primary', size = 'md', className) =>
  cn(base, variants[variant], sizes[size], className);

export const Button = forwardRef(function Button(
  { variant = 'primary', size = 'md', loading, icon, className, children, disabled, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={buttonClass(variant, size, className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading ? <Spinner className="h-4 w-4" /> : icon}
      {children}
    </button>
  );
});

export function ButtonLink({
  variant = 'primary',
  size = 'md',
  className,
  ...rest
}) {
  return <Link className={buttonClass(variant, size, className)} {...rest} />;
}

const variantProp = PropTypes.oneOf(['primary', 'secondary', 'danger', 'ghost', 'success']);
const sizeProp = PropTypes.oneOf(['sm', 'md', 'lg']);

Button.propTypes = {
  variant: variantProp,
  size: sizeProp,
  loading: PropTypes.bool,
  icon: PropTypes.node,
  className: PropTypes.string,
  children: PropTypes.node,
};

ButtonLink.propTypes = {
  variant: variantProp,
  size: sizeProp,
  className: PropTypes.string,
};
