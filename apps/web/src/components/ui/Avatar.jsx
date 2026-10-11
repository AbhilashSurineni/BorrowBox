import { cn, initials } from '@/lib/utils';
import PropTypes from 'prop-types';

const sizes = { sm: 'h-8 w-8 text-xs', md: 'h-10 w-10 text-sm', lg: 'h-16 w-16 text-lg', xl: 'h-24 w-24 text-2xl' };

export function Avatar({ name, url, size = 'md', className }) {
  if (url) return <img src={url} alt="" className={cn('shrink-0 rounded-full object-cover', sizes[size], className)} />;
  return (
    <span aria-hidden className={cn('inline-flex shrink-0 items-center justify-center rounded-full bg-brand-100 font-semibold text-brand-900', sizes[size], className)}>
      {initials(name)}
    </span>
  );
}

Avatar.propTypes = {
  name: PropTypes.string.isRequired,
  url: PropTypes.string,
  size: PropTypes.oneOf(Object.keys(sizes)),
  className: PropTypes.string,
};
