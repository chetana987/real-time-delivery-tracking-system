import http from 'k6/http';

// Returns a JWT by calling POST /api/auth/login, or null on failure.
export function login(base, email, password) {
  const res = http.post(`${base}/api/auth/login`, JSON.stringify({ email, password }), {
    headers: { 'Content-Type': 'application/json' },
  });
  if (res.status !== 200) {
    console.log(`login failed (${res.status}) for ${email}: ${res.body}`);
    return null;
  }
  return res.json().token;
}
