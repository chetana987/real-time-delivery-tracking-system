import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../lib/auth';
import { homePathFor, roleLabel } from '../lib/routing';

function Logo() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="h-6 w-6" aria-hidden="true">
      <path
        d="M12 2c1.6 2.7 3.2 4.3 3.2 6.9a3.7 3.7 0 0 1-7.4 0C7.8 7.1 8.2 6.2 8.9 5.4 6.9 7.6 6 9.7 6 11.9A6 6 0 0 0 18 11.9c0-3.4-3-6.1-6-9.9Z"
        fill="currentColor"
      />
    </svg>
  );
}

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const homePath = user ? (homePathFor(user) ?? '/login') : '/login';

  function handleLogout() {
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <header className="sticky top-0 z-50 border-b border-cream-200 bg-cream-50/90 backdrop-blur">
      <div className="mx-auto flex h-16 w-full max-w-5xl items-center justify-between px-4 sm:px-6">
        <Link to={homePath} className="flex items-center gap-2.5">
          <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-terra-500 text-white">
            <Logo />
          </span>
          <span className="text-xl font-bold tracking-tight">
            Delivery Tracking System <span className="text-terra-500">by Chetana</span>
          </span>
        </Link>

        {user && (
          <div className="flex items-center gap-3">
            <div className="hidden text-right sm:block">
              <p className="text-sm font-semibold leading-tight">{user.name}</p>
              <p className="text-xs leading-tight text-charcoal-600">{roleLabel(user.role)}</p>
            </div>
            <span className="hidden h-8 w-px bg-cream-200 sm:block" />
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-lg border border-cream-200 bg-white px-3 py-1.5 text-sm font-medium text-charcoal-700 transition hover:border-terra-300 hover:text-terra-700"
            >
              Log out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
