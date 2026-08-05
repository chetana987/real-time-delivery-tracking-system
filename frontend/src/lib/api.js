const TOKEN_KEY = 'delivery_token';
const USER_KEY = 'delivery_user';

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

  const res = await fetch(path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

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
    throw error;
  }

  if (res.status === 204) return null;
  return res.json();
}

export const api = {
  login: (body) => request('/api/auth/login', { method: 'POST', body }),
  register: (body) => request('/api/auth/register', { method: 'POST', body }),

  myOrders: () => request('/api/orders'),
  availableOrders: () => request('/api/orders/available'),
  getOrder: (id) => request(`/api/orders/${id}`),
  acceptOrder: (id) => request(`/api/orders/${id}/accept`, { method: 'PATCH' }),
  updateOrderStatus: (id, status) => request(`/api/orders/${id}/status`, { method: 'PATCH', body: { status } }),

  getLatestLocation: (id) => request(`/api/orders/${id}/location`),
  getLocationHistory: (id) => request(`/api/orders/${id}/history`),
};
