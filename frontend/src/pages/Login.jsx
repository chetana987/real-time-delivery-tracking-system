import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import PasswordInput from '../components/PasswordInput';
import { useAuth } from '../lib/auth';

function homeFor(user) {
  return user?.role === 'CUSTOMER' ? '/customer' : '/partner';
}

export default function Login() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (user) navigate(homeFor(user), { replace: true });
  }, [user, navigate]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    if (!email.trim() || !password) {
      setError('Please enter your email and password.');
      return;
    }
    setSubmitting(true);
    try {
      await login(email.trim(), password);
      // redirect happens via the effect above once `user` is set
    } catch (err) {
      setError(err.message || 'Login failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  }

  const inputClass =
    'w-full rounded-xl border border-cream-200 bg-white px-4 py-3 text-sm text-charcoal-900 placeholder-charcoal-600/50 outline-none transition focus:border-terra-400 focus:ring-2 focus:ring-terra-100';

  return (
    <div className="flex min-h-screen items-center justify-center bg-cream-50 px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-terra-500 text-3xl font-extrabold text-white">
            C
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-charcoal-900">
            Welcome back
          </h1>
          <p className="mt-1 text-sm text-charcoal-600">
            Log in to Delivery Tracking System by Chetana to track or deliver orders.
          </p>
        </div>

        <form
          onSubmit={handleSubmit}
          className="space-y-5 rounded-2xl border border-cream-200 bg-white p-6 shadow-sm sm:p-8"
        >
          {error && (
            <div
              role="alert"
              className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700"
            >
              {error}
            </div>
          )}

          <div>
            <label htmlFor="email" className="mb-1.5 block text-sm font-semibold text-charcoal-900">
              Email
            </label>
            <input
              id="email"
              type="email"
              autoComplete="email"
              className={inputClass}
              placeholder="you@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div>
            <label htmlFor="password" className="mb-1.5 block text-sm font-semibold text-charcoal-900">
              Password
            </label>
            <PasswordInput
              id="password"
              autoComplete="current-password"
              className={inputClass}
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-xl bg-terra-500 px-4 py-3 text-sm font-bold text-white shadow-sm transition hover:bg-terra-600 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting ? 'Logging in…' : 'Log in'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-charcoal-600">
          New here?{' '}
          <Link to="/register" className="font-semibold text-terra-600 hover:text-terra-700">
            Create an account
          </Link>
        </p>
      </div>
    </div>
  );
}
