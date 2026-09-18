import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import L from 'leaflet';
import { api, getToken } from '../lib/api';
import { createStompClient } from '../lib/stomp';
import { haversineKm, formatKm, formatTime } from '../lib/geo';
import { useLeafletMap } from '../hooks/useLeafletMap';
import { useSmoothMarker } from '../hooks/useSmoothMarker';
import StatusProgress from '../components/StatusProgress';

const DEFAULT_CENTER = [28.6139, 77.209];

export default function TrackOrder() {
  const { orderId } = useParams();
  const [order, setOrder] = useState(null);
  const [live, setLive] = useState(null);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState('');
  const [loadError, setLoadError] = useState('');

  const { containerRef, mapRef } = useLeafletMap({ center: DEFAULT_CENTER, zoom: 13 });
  const { moveTo } = useSmoothMarker(mapRef);
  const seededRef = useRef(false);
  const subRef = useRef(null);
  const clientRef = useRef(null);

  const refreshOrder = useCallback(async () => {
    try {
      const data = await api.getOrder(orderId);
      setOrder(data);
      setLoadError('');
    } catch (err) {
      setLoadError(err.message);
    }
  }, [orderId]);

  const isTerminal = !!order && (order.status === 'CANCELLED' || order.status === 'DELIVERED');

  // Live tracking only applies once we know the order is still in progress.
  // Terminal orders (CANCELLED / DELIVERED) never create a WebSocket
  // subscription, even briefly, and never poll.
  const liveEligible = !!order && !isTerminal;

  // The delivery destination always comes from the order. A record created
  // before destinations existed may have no coordinates — we never invent one.
  const hasDestination =
    !!order && order.deliveryLatitude != null && order.deliveryLongitude != null;
  const destination = hasDestination
    ? { lat: order.deliveryLatitude, lng: order.deliveryLongitude }
    : null;

  // Poll while the status is unknown or in progress; stop once terminal.
  const shouldPoll = !(order && isTerminal);

  useEffect(() => {
    refreshOrder();
    if (!shouldPoll) return undefined;
    const poll = setInterval(refreshOrder, 5000);
    return () => clearInterval(poll);
  }, [refreshOrder, shouldPoll]);

  // Terminal orders (CANCELLED / DELIVERED) stop live tracking: no STOMP
  // subscription and no WebSocket connection.
  useEffect(() => {
    if (!isTerminal) return undefined;
    setConnected(false);
    setLive(null);
    try {
      subRef.current?.unsubscribe();
    } catch {
      /* noop */
    }
    subRef.current = null;
    try {
      clientRef.current?.deactivate();
    } catch {
      /* noop */
    }
    clientRef.current = null;
  }, [isTerminal]);

  async function handleCancel() {
    if (!window.confirm('Cancel this order? This cannot be undone.')) return;
    try {
      await api.cancelOrder(orderId);
      await refreshOrder();
    } catch (err) {
      setError(err.message);
    }
  }

  // Seed the map once: center at the latest known location (or the restaurant).
  useEffect(() => {
    if (!order || seededRef.current) return;
    seededRef.current = true;

    if (order.restaurantLat != null && order.restaurantLng != null) {
      mapRef.current?.setView([order.restaurantLat, order.restaurantLng], 14);
    }

    api
      .getLatestLocation(orderId)
      .then((u) => {
        setLive(u);
        moveTo({ lat: u.lat, lng: u.lng });
        mapRef.current?.setView([u.lat, u.lng], 15);
      })
      .catch(() => {
        // 404 = no location yet; the marker just sits at the restaurant.
      });
  }, [order, orderId, mapRef, moveTo]);

  // Restaurant marker + destination pin from the ORDER's real destination.
  // If the order has no destination coordinates (legacy record), only the
  // restaurant is shown and no false destination is drawn.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !order) return undefined;

    const markers = [];

    if (order.restaurantLat != null && order.restaurantLng != null) {
      markers.push(
        L.marker([order.restaurantLat, order.restaurantLng], {
          icon: L.divIcon({
            className: 'marker-restaurant',
            html: '<div class="marker-restaurant-dot"></div>',
            iconSize: [20, 20],
            iconAnchor: [10, 10],
          }),
          interactive: false,
        }).addTo(map),
      );
    }

    if (hasDestination) {
      markers.push(
        L.marker([destination.lat, destination.lng], {
          icon: L.divIcon({
            className: 'marker-destination',
            html: '',
            iconSize: [18, 18],
            iconAnchor: [9, 9],
          }),
          interactive: false,
        }).addTo(map),
      );
    }

    const t = setTimeout(() => map.invalidateSize(), 100);
    return () => {
      clearTimeout(t);
      markers.forEach((m) => map.removeLayer(m));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [order, hasDestination]);

  // STOMP subscription lifecycle: only for in-progress orders, connect on mount,
  // clean up on unmount or when the order reaches a terminal state.
  useEffect(() => {
    if (!liveEligible) return undefined;
    const token = getToken();
    const client = createStompClient(token);
    clientRef.current = client;

    client.onConnect = () => {
      setConnected(true);
      subRef.current = client.subscribe(`/topic/order/${orderId}/location`, (frame) => {
        try {
          const update = JSON.parse(frame.body);
          setLive(update);
          moveTo({ lat: update.lat, lng: update.lng });
          refreshOrder();
        } catch {
          /* ignore malformed frames */
        }
      });
    };
    client.onWebSocketClose = () => setConnected(false);
    client.onStompError = (frame) => {
      setConnected(false);
      setError(frame.headers?.message || 'Could not subscribe to this order.');
    };
    client.onWebSocketError = () => {};

    client.activate();

    return () => {
      try {
        subRef.current?.unsubscribe();
      } catch {
        /* noop */
      }
      clientRef.current = null;
      client.deactivate();
    };
  }, [orderId, moveTo, refreshOrder, liveEligible]);

  let distanceKm = null;
  if (order && live && destination) {
    distanceKm = haversineKm(live, destination);
  }

  if (loadError) {
    return (
      <div className="rounded-2xl border border-red-200 bg-red-50 px-5 py-8 text-center">
        <p className="font-medium text-red-700">{loadError}</p>
        <Link to="/" className="mt-3 inline-block text-sm font-semibold text-terra-600 hover:text-terra-700">
          Back to dashboard
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <Link to="/" className="text-sm font-semibold text-terra-600 hover:text-terra-700">
            ← Back
          </Link>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-charcoal-900">
            Order #{orderId}
          </h1>
          <p className="text-sm text-charcoal-600">
            {order?.restaurantName || 'Restaurant'} · {order?.deliveryAddress || '—'}
          </p>
        </div>
        <div className="flex items-center gap-2">
          {order?.status === 'PLACED' && (
            <button
              type="button"
              onClick={handleCancel}
              className="rounded-xl border border-red-200 bg-red-50 px-3 py-1.5 text-sm font-bold text-red-700 transition hover:bg-red-100"
            >
              Cancel order
            </button>
          )}
          <span
            className={`flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold ${
              connected ? 'bg-emerald-100 text-emerald-800' : 'bg-stone-100 text-stone-600'
            }`}
          >
            <span
              className={`h-2 w-2 rounded-full ${connected ? 'bg-emerald-500 animate-pulse' : 'bg-stone-400'}`}
            />
            {connected ? 'Live' : 'Offline'}
          </span>
        </div>
      </div>

      {error && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900">
          {error}
        </div>
      )}

      {order && !hasDestination && (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900">
          Delivery destination unavailable — this order has no stored destination.
        </div>
      )}

      {order && (
        <div className="rounded-2xl border border-cream-200 bg-white p-5 shadow-sm">
          <StatusProgress status={order.status} />
        </div>
      )}

      <div className="overflow-hidden rounded-2xl border border-cream-200 bg-white shadow-sm">
        <div ref={containerRef} className="relative z-0 h-[55vh] min-h-[320px] w-full" />
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-2xl border border-cream-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-bold uppercase tracking-wider text-charcoal-600">
            Live position
          </p>
          <p className="mt-1 text-sm font-semibold text-charcoal-900">
            {live ? `${live.lat.toFixed(5)}, ${live.lng.toFixed(5)}` : 'Waiting for updates…'}
          </p>
          <p className="mt-0.5 text-xs text-charcoal-600">
            {live ? `Updated ${formatTime(live.timestamp)}` : 'Partner will appear on the map soon'}
          </p>
        </div>

        <div className="rounded-2xl border border-cream-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-bold uppercase tracking-wider text-charcoal-600">
            Estimated distance remaining
          </p>
          <p className="mt-1 text-2xl font-extrabold text-terra-600">
            {formatKm(distanceKm)}
          </p>
          <p className="mt-0.5 text-xs text-charcoal-600">Straight-line estimate to destination</p>
        </div>

        <div className="rounded-2xl border border-cream-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-bold uppercase tracking-wider text-charcoal-600">Total</p>
          <p className="mt-1 text-2xl font-extrabold text-charcoal-900">
            {order ? `$${Number(order.totalAmount).toFixed(2)}` : '—'}
          </p>
          <p className="mt-0.5 text-xs text-charcoal-600">
            Order placed {order ? formatTime(order.createdAt) : ''}
          </p>
        </div>
      </div>
    </div>
  );
}
