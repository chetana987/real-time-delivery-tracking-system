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

export default function DeliveryDashboard() {
  const [available, setAvailable] = useState([]);
  const [mine, setMine] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const [simOrderId, setSimOrderId] = useState(null);
  const [simTick, setSimTick] = useState(0);

  const stompRef = useRef(null);
  const timerRef = useRef(null);
  const simRef = useRef({ orderId: null, route: [], step: 0 });

  const load = useCallback(async () => {
    try {
      const [avail, my] = await Promise.all([api.availableOrders(), api.myOrders()]);
      setAvailable(avail);
      setMine(my);
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

  useEffect(
    () => () => {
      if (timerRef.current) clearInterval(timerRef.current);
      try {
        stompRef.current?.deactivate();
      } catch {
        /* noop */
      }
    },
    [],
  );

  async function accept(orderId) {
    setBusy(true);
    try {
      await api.acceptOrder(orderId);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function advance(orderId, status) {
    setBusy(true);
    try {
      await api.updateOrderStatus(orderId, status);
      await load();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
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
    try {
      stompRef.current?.deactivate();
    } catch {
      /* noop */
    }
    simRef.current = {
      orderId: order.id,
      route: buildDemoRoute(order.restaurantLat, order.restaurantLng),
      step: 0,
    };

    const client = createStompClient(getToken());
    client.onConnect = () => {
      sendStep();
      timerRef.current = setInterval(sendStep, 2000);
      setSimTick((n) => n + 1);
    };
    client.onWebSocketError = () => {};
    client.onStompError = () => stopSim();
    stompRef.current = client;
    client.activate();
    setSimOrderId(order.id);
  }

  function stopSim() {
    if (timerRef.current) clearInterval(timerRef.current);
    timerRef.current = null;
    try {
      stompRef.current?.deactivate();
    } catch {
      /* noop */
    }
    stompRef.current = null;
    setSimOrderId(null);
  }

  const active = mine.filter((o) => IN_PROGRESS.includes(o.status));
  const delivered = mine.filter((o) => o.status === 'DELIVERED');

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-charcoal-900">Partner dashboard</h1>
        <p className="mt-1 text-sm text-charcoal-600">
          Accept nearby orders, update their status, and simulate your ride.
        </p>
      </div>

      {error && (
        <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
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
                    disabled={busy}
                    onClick={() => accept(order.id)}
                    className="rounded-xl bg-terra-500 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-terra-600 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    Accept order
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
                          disabled={busy}
                          onClick={() => advance(order.id, next.status)}
                          className="rounded-xl bg-charcoal-800 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-charcoal-700 disabled:cursor-not-allowed disabled:opacity-60"
                        >
                          Mark {next.label}
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
        Simulated movement: the page plays back a demo route from the restaurant and sends a
        location update every 2 seconds over WebSocket — no phone GPS needed.
      </p>
    </div>
  );
}
