import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import OrderCard from '../components/OrderCard';

const ACTIVE = ['PLACED', 'ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY'];

export default function CustomerDashboard() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const data = await api.myOrders();
      setOrders(data);
      setError('');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const id = setInterval(load, 10000);
    return () => clearInterval(id);
  }, [load]);

  const active = orders.filter((o) => ACTIVE.includes(o.status));
  const past = orders.filter((o) => !ACTIVE.includes(o.status));

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-charcoal-900">My orders</h1>
        <p className="mt-1 text-sm text-charcoal-600">
          Track deliveries in real time or review past orders.
        </p>
      </div>

      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </div>
      )}

      <section>
        <h2 className="mb-3 text-sm font-bold uppercase tracking-wider text-charcoal-600">
          Active
        </h2>
        {loading ? (
          <p className="text-sm text-charcoal-600">Loading your orders…</p>
        ) : active.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-cream-200 bg-white px-5 py-8 text-center text-sm text-charcoal-600">
            No active orders right now.
          </p>
        ) : (
          <div className="grid gap-4">
            {active.map((order) => (
              <OrderCard
                key={order.id}
                order={order}
                actions={
                  <Link
                    to={`/track/${order.id}`}
                    className="inline-flex items-center justify-center rounded-xl bg-terra-500 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-terra-600"
                  >
                    Track order
                  </Link>
                }
              />
            ))}
          </div>
        )}
      </section>

      <section>
        <h2 className="mb-3 text-sm font-bold uppercase tracking-wider text-charcoal-600">Past</h2>
        {past.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-cream-200 bg-white px-5 py-8 text-center text-sm text-charcoal-600">
            No past orders yet.
          </p>
        ) : (
          <div className="grid gap-4">
            {past.map((order) => (
              <OrderCard key={order.id} order={order} dim />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
