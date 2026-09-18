const TOKEN_KEY = 'delivery_token';
const USER_KEY = 'delivery_user';

// Registered by the auth layer so a 401 (expired/invalid JWT) can clear the
// stale session and route the user to login. Kept as a plain module callback
// so api.js does not need to import React or a router.
let unauthorizedHandler = null;

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

export function clearUnauthorizedHandler() {
  unauthorizedHandler = null;
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function persistAuth(data) {
  localStorage.setItem(TOKEN_KEY, data.token);
  localStorage.setItem(
    USER_KEY,
    JSON.stringify({
      userId: data.userId,
      name: data.name,
      email: data.email,
      role: data.role,
    }),
  );
}

export function getStoredUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY));
  } catch {
    return null;
  }
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

async function request(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;

  let res;
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch {
    const error = new Error('Could not reach the server. Check your connection and try again.');
    error.status = 0;
    throw error;
  }

  if (!res.ok) {
    let data = null;
    try {
      data = await res.json();
    } catch {
      /* non-JSON error body */
    }
    const error = new Error(data?.message || data?.error || `Request failed (${res.status})`);
    error.status = res.status;
    error.fieldErrors = data?.fieldErrors || null;
    if (res.status === 401 && token) {
      // Expired/invalid JWT: drop the stale session once and let the auth layer
      // route to login. After clearing, subsequent polls send no token, so the
      // same failure is not broadcast repeatedly.
      clearAuth();
      unauthorizedHandler?.();
    }
    throw error;
  }

  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  login: (body) => request('/api/auth/login', { method: 'POST', body }),
  register: (body) => request('/api/auth/register', { method: 'POST', body }),

  restaurants: () => request('/api/restaurants?size=100&sortBy=name').then((p) => p?.content ?? []),
  restaurantMenu: (id) => request(`/api/restaurants/${id}/menu?size=100&sortBy=name`).then((p) => p?.content ?? []),
  placeOrder: (body) => request('/api/orders', { method: 'POST', body }),

  // Paginated, filterable order list for the signed-in user (customers see
  // their own orders; the backend scopes every query to the caller).
  myOrders: (params = {}) => {
    const qs = new URLSearchParams();
    if (params.page != null) qs.set('page', params.page);
    if (params.size != null) qs.set('size', params.size);
    if (params.sortBy) qs.set('sortBy', params.sortBy);
    if (params.direction) qs.set('direction', params.direction);
    if (params.status) qs.set('status', params.status);
    const q = qs.toString();
    return request(`/api/orders${q ? `?${q}` : ''}`);
  },
  availableOrders: () => request('/api/orders/available').then((p) => p?.content ?? []),
  getOrder: (id) => request(`/api/orders/${id}`),
  acceptOrder: (id) => request(`/api/orders/${id}/accept`, { method: 'PATCH' }),
  updateOrderStatus: (id, status) => request(`/api/orders/${id}/status`, { method: 'PATCH', body: { status } }),
  cancelOrder: (id) => request(`/api/orders/${id}/cancel`, { method: 'PATCH' }),
  availability: () => request('/api/partners/availability'),

  getLatestLocation: (id) => request(`/api/orders/${id}/location`),
  getLocationHistory: (id) => request(`/api/orders/${id}/history`),
};
