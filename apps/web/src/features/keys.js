/** Central React Query keys so mutations can invalidate precisely. */
export const qk = {
  categories: ['categories'] ,
  items: (params) => ['items', 'list', params ?? {}] ,
  item: (id) => ['items', 'detail', id] ,
  availability: (id) => ['items', 'availability', id] ,
  myItems: (params) => ['my-items', params ?? {}] ,
  loans: (params) => ['loans', 'list', params ?? {}] ,
  loan: (id) => ['loans', 'detail', id] ,
  notifications: (params) => ['notifications', params ?? {}] ,
  unread: ['notifications', 'unread'] ,
  profile: (id) => ['users', id] ,
  userReviews: (id, page) => ['users', id, 'reviews', page] ,
  admin: (...parts) => ['admin', ...parts] ,
};
