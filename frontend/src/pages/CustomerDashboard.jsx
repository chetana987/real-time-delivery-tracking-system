import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import OrderCard from '../components/OrderCard';
import PlaceOrderForm from '../components/PlaceOrderForm';

const ACTIVE = ['PLACED', 'ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY'];

// History filters map 1:1 to the backend's single-value `status` query param.
const HISTORY_FILTERS = [
  { key: 'ALL', label: 'All' },
  { key: 'DELIVERED', label: 'Delivered' },
  { key: 'CANCELLED', label: 'Cancelled' },
];

const PAGE_SIZE = 10;

export default function CustomerDashboard() {
  const { user } = useAuth();
  const userId = user?.userId;

  // A small, bounded, most-recent-first window that powers the Active section.
  // Active orders are always recent (they have not reached a terminal state yet),
  // so the newest page captures them; the split is done client-side while the
  // backend scoping still guarantees only the caller's own orders come back.
  const [activeOrders, setActiveOrders] = useState([]);
  const [activeLoading, setActiveLoading] = useState(true);
  const [activeError, setActiveError] = useState('');

  // History is server-paginated and server-filtered.
  const [history, setHistory] = useState({
    content: [],
    page: 0,
    size: PAGE_SIZE,
    totalElements: 0,
    totalPages: 0,
  });
  const [historyFilter, setHistoryFilter] = useState('ALL');
  const [historyPage, setHistoryPage] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState('');

  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const loadActive = useCallback(async () => {
    try {
      const page = await api.myOrders({ size: 50, sortBy: 'createdAt', direction: 'desc' });
      const rows = (page?.content ?? []).filter((o) => ACTIVE.includes(o.status));
      rows.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      setActiveOrders(rows);
      setActiveError('');
    } catch (err) {
      setActiveError(err.message);
    } finally {
      setActiveLoading(false);
    }
  }, []);

  const loadHistory = useCallback(async () => {
    setHistoryLoading(true);
    try {
      const params = { page: historyPage, size: PAGE_SIZE, sortBy: 'createdAt', direction: 'desc' };
      if (historyFilter !== 'ALL') params.status = historyFilter;
      const data = await api.myOrders(params);
      setHistory({
        content: data?.content ?? [],
        page: data?.page ?? 0,
        size: data?.size ?? PAGE_SIZE,
        totalElements: data?.totalElements ?? 0,
        totalPages: data?.totalPages ?? 0,
      });
      setHistoryError('');
    } catch (err) {
      setHistoryError(err.message);
    } finally {
      setHistoryLoading(false);
    }
  }, [historyPage, historyFilter]);

  useEffect(() => {
    loadActive();
    const id = setInterval(loadActive, 10000);
    return () => clearInterval(id);
  }, [loadActive]);

  useEffect(() => {
    loadHistory();
  }, [loadHistory]);

  // Scenario: a different customer logs in (or the account changes) — wipe any
  // previously-loaded orders from state so the previous user's history can never
  // flash on screen.
  useEffect(() => {
    setActiveOrders([]);
    setHistory({ content: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 });
    setHistoryPage(0);
    setHistoryFilter('ALL');
  }, [userId]);

  function changeFilter(key) {
    setHistoryFilter(key);
    setHistoryPage(0);
  }

  async function cancelOrder(order) {
    if (!window.confirm(`Cancel order #${order.id}? This cannot be undone.`)) return;
    setBusy(true);
    setError('');
    try {
      await api.cancelOrder(order.id);
      await loadActive();
      await loadHistory();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-charcoal-900">Customer Dashboard</h1>
        <p className="mt-1 text-sm text-charcoal-600">
          Track deliveries in real time or review your order history.
        </p>
      </div>

      <PlaceOrderForm
        onPlaced={() => {
          loadActive();
          loadHistory();
        }}
      />

      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </div>
      )}

      {/* ─────────────────────────── Active orders ─────────────────────────── */}
      <section>
        <h2 className="mb-3 text-sm font-bold uppercase tracking-wider text-charcoal-600">
          Active Orders
        </h2>

        {activeError && (
          <div className="mb-3 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            Could not load active orders — retrying automatically. ({activeError})
          </div>
        )}

        {activeLoading && !activeError ? (
          <p className="text-sm text-charcoal-600">Loading your orders…</p>
        ) : activeOrders.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-cream-200 bg-white px-5 py-8 text-center text-sm text-charcoal-600">
            No active orders right now.
          </p>
        ) : (
          <div className="grid gap-4">
            {activeOrders.map((order) => (
              <OrderCard
                key={order.id}
                order={order}
                actions={
                  <>
                    <Link
                      to={`/track/${order.id}`}
                      className="inline-flex items-center justify-center rounded-xl bg-terra-500 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-terra-600"
                    >
                      Track order
                    </Link>
                    {order.status === 'PLACED' && (
                      <button
                        type="button"
                        disabled={busy}
                        onClick={() => cancelOrder(order)}
                        className="inline-flex items-center justify-center rounded-xl border border-red-200 bg-red-50 px-4 py-2.5 text-sm font-bold text-red-700 transition hover:bg-red-100 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        Cancel order
                      </button>
                    )}
                  </>
                }
              />
            ))}
          </div>
        )}
      </section>

      {/* ─────────────────────────── Order history ─────────────────────────── */}
      <section>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-bold uppercase tracking-wider text-charcoal-600">
            Order History
          </h2>

          <div className="flex items-center gap-1.5" role="tablist" aria-label="History filters">
            {HISTORY_FILTERS.map((f) => (
              <button
                key={f.key}
                type="button"
                role="tab"
                aria-selected={historyFilter === f.key}
                onClick={() => changeFilter(f.key)}
                className={`rounded-full px-3 py-1 text-xs font-semibold transition ${
                  historyFilter === f.key
                    ? 'bg-charcoal-900 text-white'
                    : 'bg-white text-charcoal-700 ring-1 ring-cream-200 hover:bg-cream-100'
                }`}
              >
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {historyError ? (
          <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
            Could not load your order history. ({historyError})
            <button
              type="button"
              onClick={loadHistory}
              className="ml-2 rounded-lg bg-white px-3 py-1 text-xs font-bold text-red-700 ring-1 ring-red-200 transition hover:bg-red-100"
            >
              Try again
            </button>
          </div>
        ) : historyLoading ? (
          <p className="text-sm text-charcoal-600">Loading order history…</p>
        ) : history.content.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-cream-200 bg-white px-5 py-8 text-center text-sm text-charcoal-600">
            {historyFilter === 'DELIVERED'
              ? 'No delivered orders yet.'
              : historyFilter === 'CANCELLED'
                ? 'No cancelled orders yet.'
                : 'No previous orders.'}
          </p>
        ) : (
          <div className="grid gap-4">
            {history.content.map((order) => (
              <OrderCard
                key={order.id}
                order={order}
                dim
                actions={
                  <Link
                    to={`/track/${order.id}`}
                    className="inline-flex items-center justify-center rounded-xl border border-cream-200 bg-white px-4 py-2 text-sm font-semibold text-terra-600 transition hover:bg-terra-50"
                  >
                    View details
                  </Link>
                }
              />
            ))}
          </div>
        )}

        {history.totalPages > 1 && (
          <div className="mt-4 flex items-center justify-between">
            <button
              type="button"
              disabled={historyLoading || history.page <= 0}
              onClick={() => setHistoryPage((p) => Math.max(0, p - 1))}
              className="rounded-xl border border-cream-200 bg-white px-3 py-1.5 text-sm font-semibold text-charcoal-700 transition hover:bg-cream-100 disabled:cursor-not-allowed disabled:opacity-50"
            >
              ← Previous
            </button>
            <span className="text-sm text-charcoal-600">
              Page {history.page + 1} of {history.totalPages}
            </span>
            <button
              type="button"
              disabled={historyLoading || history.page + 1 >= history.totalPages}
              onClick={() => setHistoryPage((p) => p + 1)}
              className="rounded-xl border border-cream-200 bg-white px-3 py-1.5 text-sm font-semibold text-charcoal-700 transition hover:bg-cream-100 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Next →
            </button>
          </div>
        )}
      </section>
    </div>
  );
}