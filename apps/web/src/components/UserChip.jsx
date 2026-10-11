import { Link } from 'react-router-dom';

import { Avatar, RatingStars } from './ui';

export function UserChip({ user, label }) {
  return (
    <Link to={`/users/${user.id}`} className="flex items-center gap-3 rounded-lg p-1 hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand-700">
      <Avatar name={user.name} url={user.avatarUrl} />
      <span className="min-w-0">
        {label && <span className="block text-xs text-gray-600">{label}</span>}
        <span className="block truncate font-medium text-gray-900">{user.name}</span>
        <span className="block text-xs text-gray-600">{user.department}</span>
        <RatingStars value={user.ratingAvg} count={user.ratingCount} />
      </span>
    </Link>
  );
}
