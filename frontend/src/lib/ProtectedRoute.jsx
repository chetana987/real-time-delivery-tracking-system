import { Navigate } from 'react-router-dom';
import { useAuth } from './auth';
import { homePathFor } from './routing';

export default function ProtectedRoute({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

export function RoleRoute({ allow, children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (!allow.includes(user.role)) {
    return <Navigate to={homePathFor(user) ?? '/login'} replace />;
  }
  return children;
}