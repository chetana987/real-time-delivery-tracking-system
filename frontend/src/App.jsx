import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './lib/auth';
import ProtectedRoute, { RoleRoute } from './lib/ProtectedRoute';
import { homePathFor } from './lib/routing';
import Navbar from './components/Navbar';
import Login from './pages/Login';
import Register from './pages/Register';
import CustomerDashboard from './pages/CustomerDashboard';
import DeliveryDashboard from './pages/DeliveryDashboard';
import AdminDashboard from './pages/AdminDashboard';
import TrackOrder from './pages/TrackOrder';

function HomeRedirect() {
  const { user } = useAuth();
  const dest = homePathFor(user) ?? '/login';
  return <Navigate to={dest} replace />;
}

function Shell({ children }) {
  return (
    <div className="min-h-screen bg-cream-50 text-charcoal-900">
      <Navbar />
      <main className="mx-auto w-full max-w-5xl px-4 py-6 sm:px-6 sm:py-8">{children}</main>
    </div>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/" element={<HomeRedirect />} />
          <Route
            path="/customer"
            element={
              <RoleRoute allow={['CUSTOMER']}>
                <Shell>
                  <CustomerDashboard />
                </Shell>
              </RoleRoute>
            }
          />
          <Route
            path="/partner"
            element={
              <RoleRoute allow={['DELIVERY_PARTNER']}>
                <Shell>
                  <DeliveryDashboard />
                </Shell>
              </RoleRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <RoleRoute allow={['ADMIN']}>
                <Shell>
                  <AdminDashboard />
                </Shell>
              </RoleRoute>
            }
          />
          <Route
            path="/track/:orderId"
            element={
              <ProtectedRoute>
                <Shell>
                  <TrackOrder />
                </Shell>
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
