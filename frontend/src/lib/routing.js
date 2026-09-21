export const ROUTE_BY_ROLE = {
  ADMIN: '/admin',
  DELIVERY_PARTNER: '/partner',
  CUSTOMER: '/customer',
};

const ROLE_LABELS = {
  ADMIN: 'Administrator',
  DELIVERY_PARTNER: 'Delivery Partner',
  CUSTOMER: 'Customer',
};

export function homePathFor(user) {
  return user ? ROUTE_BY_ROLE[user.role] ?? null : null;
}

export function roleLabel(role) {
  return ROLE_LABELS[role] ?? 'User';
}