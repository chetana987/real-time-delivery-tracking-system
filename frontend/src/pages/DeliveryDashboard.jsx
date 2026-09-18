import { useCallback, useEffect, useRef, useState } from 'react';
import { api, getToken } from '../lib/api';
import { createStompClient } from '../lib/stomp';
import { buildDemoRoute } from '../lib/simulatedRoute';
import OrderCard from '../components/OrderCard';

const IN_PROGRESS = ['ACCEPTED', 'PICKED_UP', 'OUT_FOR_DELIVERY'];

const NEXT_STATUS = {
  ACCEPTED: { status: 'PICKED_UP', label: 'Picked up' },
  PICKED_UP: { status: 'OUT_FOR_DELIVERY', label: 'Out for delivery' },
  OUT_FOR_DELIVERY: { status: 'DELIVERED', label: 'Delivered' },
};

const STATUS_STYLES = {
  AVAILABLE: 'bg-emerald-100 text-emerald-900',
  BUSY: 'bg-amber-100 text-amber-900',
  OFFLINE: 'bg-stone-100 text-stone-600',
};

export default function DeliveryDashboard() {
  const [available, setAvailable] = useState([]);
  const [mine, setMine] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  // Tracks the specific order currently being accepted/advanced, so only that
  // order's buttons are disabled while its request is in flight.
  const [busyId, setBusyId] = useState(null);
  const [availability, setAvailability] = useState(null);

  const [simOrderId, setSimOrderId] = useState(null);
  const [simTick, setSimTick] = useState(0);

  const stompRef = useRef(null);
  const timerRef = useRef(null);
  const simRef = useRef({ orderId: null, route: [], step: 0 });

  const load = useCallback(async () => {
    try {
      const [avail, my] = await Promise.all([api.availableOrders(), api.myOrders()]);
      setAvailable(avail);
      setMine(my?.content ?? []);
      setError('');
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadAvailability = useCallback(async () => {
    try {
      const res = await api.availability();
      setAvailability(res?.availability ?? null);
    } catch {
      /* availability is best-effort; keep the previous value */
    }
  }, []);

  useEffect(() => {
    load();
    loadAvailability();
    const id = setInterval(() => {
      load();
      loadAvailability();
    }, 10000);
    return () => clearInterval(id);
  }, [load, loadAvailability]);

  useEffect(() => {
    let active = true;
    const client = createStompClient(getToken());
    client.onConnect = () => {
      if (!active) return;
      loadAvailability();
      if (simRef.current.orderId != null) {
        if (timerRef.current) clearInterval(timerRef.current);
        timerRef.current = null;
        sendStep();
        timerRef.current = setInterval(sendStep, 2000);
        setSimTick((n) => n + 1);
      }
    };
    client.onWebSocketError = () => {};
    client.onStompError = () => stopSim();
    stompRef.current = client;
    client.activate();
    return () => {
      active = false;
      if (timerRef.current) clearInterval(timerRef.current);
      timerRef.current = null;
      try {
        client.deactivate();
      } catch {
        /* noop */
      }
      if (stompRef.current === client) stompRef.current = null;
    };
  }, [load, loadAvailability]);

  async function accept(orderId) {
    setBusyId(orderId);
    try {
      await api.acceptOrder(orderId);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
    }
  }

  async function advance(orderId, status) {
    setBusyId(orderId);
    try {
      await api.updateOrderStatus(orderId, status);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusyId(null);
    }
  }

  function sendStep() {
    const sim = simRef.current;
    if (sim.step >= sim.route.length) {
      stopSim();
      return;
    }
    const p = sim.route[sim.step];
    if (stompRef.current?.connected) {
      stompRef.current.publish({
        destination: '/app/location-update',
        body: JSON.stringify({ orderId: sim.orderId, lat: p.lat, lng: p.lng }),
      });
    }
    sim.step += 1;
    setSimTick(sim.step);
    if (sim.step >= sim.route.length) stopSim();
  }

  function startSim(order) {
    if (order.restaurantLat == null || order.restaurantLng == null) return;
    if (order.deliveryLatitude == null || order.deliveryLongitude == null) {
      setError('This order has no delivery destination, so simulated movement is unavailable.');
      return;
    }
    // Never leave a ticking step-interval behind when switching to a new order.
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = null;
    simRef.current = {
      orderId: order.id,
      route: buildDemoRoute(
        order.restaurantLat,
        order.restaurantLng,
        order.deliveryLatitude,
        order.deliveryLongitude,
      ),
      step: 0,
    };

    if (stompRef.current?.connected) {
      sendStep();
      timerRef.current = setInterval(sendStep, 2000);
    } else {
      stompRef.current?.activate();
    }
    setSimOrderId(order.id);
  }

  function stopSim() {
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = null;
    simRef.current = { orderId: null, route: [], step: 0 };
    setSimOrderId(null);
  }

  const active = mine.filter((o) => IN_PROGRESS.includes(o.status));
  const delivered = mine.filter((o) => o.status === 'DELIVERED');

  const statusLabel = availability ?? 'Checking…';
  const statusStyle = availability
    ? STATUS_STYLES[availability] ?? STATUS_STYLES.OFFLINE
    : 'bg-stone-100 text-stone-600';

  return (
    <div className="space-y-8">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-charcoal-900">Partner dashboard</h1>
          <p className="mt-1 text-sm text-charcoal-600">
            Accept nearby orders, update their status, and simulate your ride.
          </p>
        </div>
        <span
          className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold uppercase tracking-wide ${statusStyle}`}
        >
          <span className="h-2 w-2 rounded-full bg-current" />
          {statusLabel}
        </span>
      </div>

      {error && (
        <div
          role="alert"
          className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700"
        >
          {error}
        </div>
      )}

      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-bold uppercase tracking-wider text-charcoal-600">
            Available orders
          </h2>
          <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-bold text-amber-900">
            {available.length} waiting
          </span>
        </div>
        {loading ? (
          <p className="text-sm text-charcoal-600">Loading orders…</p>
        ) : available.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-cream-200 bg-white px-5 py-8 text-center text-sm text-charcoal-600">
            No orders waiting right now. Check back in a moment.
          </p>
        ) : (
          <div className="grid gap-4">
            {available.map((order) => (
              <OrderCard
                key={order.id}
                order={order}
                actions={
                  <button
                    type="button"
                    disabled={busyId === order.id}
                    onClick={() => accept(order.id)}
                    className="rounded-xl bg-terra-500 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-terra-600 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {busyId === order.id ? 'Accepting…' : 'Accept order'}
                  </button>
                }
              />
            ))}
          </div>
        )}
      </section>

      <section>
        <h2 className="mb-3 text-sm font-bold uppercase tracking-wider text-charcoal-600">
          My deliveries
        </h2>
        {active.length === 0 ? (
          <p className="rounded-2xl border border-dashed border-cream-200 bg-white px-5 py-8 text-center text-sm text-charcoal-600">
            You are not working on any deliveries yet.
          </p>
        ) : (
          <div className="grid gap-4">
            {active.map((order) => {
              const next = NEXT_STATUS[order.status];
              const simulating = simOrderId === order.id;
              return (
                <OrderCard
                  key={order.id}
                  order={order}
                  actions={
                    <>
                      {order.status !== 'PLACED' && next && (
                        <button
                          type="button"
                          disabled={busyId === order.id}
                          onClick={() => advance(order.id, next.status)}
                          className="rounded-xl bg-charcoal-800 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-charcoal-700 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          {busyId === order.id ? 'Updating…' : `Mark ${next.label}`}
                        </button>
                      )}
                      {IN_PROGRESS.includes(order.status) &&
                        (simulating ? (
                          <button
                            type="button"
                            onClick={stopSim}
                            className="rounded-xl border border-red-200 bg-red-50 px-4 py-2.5 text-sm font-bold text-red-700 transition hover:bg-red-100"
                          >
                            Stop simulation · step {simTick}
                          </button>
                        ) : (
                          <button
                            type="button"
                            onClick={() => startSim(order)}
                            className="rounded-xl border-2 border-terra-300 bg-terra-50 px-4 py-2.5 text-sm font-bold text-terra-700 transition hover:border-terra-400 hover:bg-terra-100"
                          >
                            Start simulated movement
                          </button>
                        ))}
                    </>
                  }
                />
              );
            })}
          </div>
        )}
      </section>

      {delivered.length > 0 && (
        <section>
          <h2 className="mb-3 text-sm font-bold uppercase tracking-wider text-charcoal-600">
            Recently delivered
          </h2>
          <div className="grid gap-4">
            {delivered.map((order) => (
              <OrderCard key={order.id} order={order} dim />
            ))}
          </div>
        </section>
      )}

      <p className="rounded-xl border border-cream-200 bg-white px-4 py-3 text-xs text-charcoal-600">
        While this dashboard is open your browser keeps a WebSocket connection alive, so the status
        pill above shows <span className="font-semibold">Available</span> when you are online with no
        active delivery and <span className="font-semibold">Busy</span> once you accept an order.
        Simulated movement: the page plays back a demo route from the restaurant toward the order's
        actual delivery destination and sends a location update every 2 seconds over WebSocket — no
        phone GPS needed. The simulation never changes the order's stored destination.
      </p>
    </div>
  );
}
