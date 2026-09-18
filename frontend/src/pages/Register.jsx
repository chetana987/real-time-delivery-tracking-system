import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import PasswordInput from '../components/PasswordInput';
import { useAuth } from '../lib/auth';

const ROLES = [
  { value: 'CUSTOMER', label: 'Customer', hint: 'I want to order food' },
  { value: 'DELIVERY_PARTNER', label: 'Delivery Partner', hint: 'I want to deliver orders' },
];

function homeFor(user) {
  return user?.role === 'CUSTOMER' ? '/customer' : '/partner';
}

export default function Register() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('CUSTOMER');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (user) navigate(homeFor(user), { replace: true });
  }, [user, navigate]);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      await register({ name: name.trim(), email: email.trim(), password, role });
      // redirect happens via the effect above once `user` is set
    } catch (err) {
      setError(err.message || 'Registration failed. Please try again.');
      setFieldErrors(err.fieldErrors || {});
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
            Create your account
          </h1>
          <p className="mt-1 text-sm text-charcoal-600">
            Sign up as a customer or a delivery partner.
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
            <span className="mb-1.5 block text-sm font-semibold text-charcoal-900">I am a…</span>
            <div className="grid grid-cols-2 gap-2">
              {ROLES.map((r) => (
                <button
                  key={r.value}
                  type="button"
                  onClick={() => setRole(r.value)}
                  className={`rounded-xl border-2 px-3 py-2.5 text-left transition ${
                    role === r.value
                      ? 'border-terra-500 bg-terra-50'
                      : 'border-cream-200 bg-white hover:border-terra-300'
                  }`}
                >
                  <span className="block text-sm font-semibold text-charcoal-900">{r.label}</span>
                  <span className="block text-[11px] leading-tight text-charcoal-600">
                    {r.hint}
                  </span>
                </button>
              ))}
            </div>
          </div>

          <div>
            <label htmlFor="name" className="mb-1.5 block text-sm font-semibold text-charcoal-900">
              Full name
            </label>
            <input
              id="name"
              type="text"
              autoComplete="name"
              className={inputClass}
              placeholder="Jane Doe"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            {fieldErrors.name && <p className="mt-1 text-xs text-red-600">{fieldErrors.name}</p>}
          </div>

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
            {fieldErrors.email && <p className="mt-1 text-xs text-red-600">{fieldErrors.email}</p>}
          </div>

          <div>
            <label htmlFor="password" className="mb-1.5 block text-sm font-semibold text-charcoal-900">
              Password
            </label>
            <PasswordInput
              id="password"
              autoComplete="new-password"
              className={inputClass}
              placeholder="At least 6 characters"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            {fieldErrors.password && (
              <p className="mt-1 text-xs text-red-600">{fieldErrors.password}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-xl bg-terra-500 px-4 py-3 text-sm font-bold text-white shadow-sm transition hover:bg-terra-600 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-charcoal-600">
          Already have an account?{' '}
          <Link to="/login" className="font-semibold text-terra-600 hover:text-terra-700">
            Log in
          </Link>
        </p>
      </div>
    </div>
  );
}
