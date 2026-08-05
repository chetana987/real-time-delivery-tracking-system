import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import L from 'leaflet';
import { api, getToken } from '../lib/api';
import { createStompClient } from '../lib/stomp';
import { demoDestination } from '../lib/simulatedRoute';
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
  const destinationMarkerRef = useRef(null);
  const seededRef = useRef(false);
  const subRef = useRef(null);

  const refreshOrder = useCallback(async () => {
    try {
      const data = await api.getOrder(orderId);
      setOrder(data);
      setLoadError('');
    } catch (err) {
      setLoadError(err.message);
    }
  }, [orderId]);

  useEffect(() => {
    refreshOrder();
    const poll = setInterval(refreshOrder, 5000);
    return () => clearInterval(poll);
  }, [refreshOrder]);

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

  // Destination pin: the demo simulation drives toward a fixed offset.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !order || order.restaurantLat == null || order.restaurantLng == null) return undefined;

    const dest = demoDestination(order.restaurantLat, order.restaurantLng);
    const icon = L.divIcon({
      className: 'marker-destination',
      html: '',
      iconSize: [18, 18],
      iconAnchor: [9, 9],
    });
    const marker = L.marker([dest.lat, dest.lng], { icon, interactive: false }).addTo(map);
    destinationMarkerRef.current = marker;

    const t = setTimeout(() => map.invalidateSize(), 100);
    return () => {
      clearTimeout(t);
      if (destinationMarkerRef.current) {
        map.removeLayer(destinationMarkerRef.current);
        destinationMarkerRef.current = null;
      }
    };
  }, [order, mapRef]);

  // STOMP subscription lifecycle: connect on mount, clean up on unmount.
  useEffect(() => {
    const token = getToken();
    const client = createStompClient(token);

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
      client.deactivate();
    };
  }, [orderId, moveTo, refreshOrder]);

  let distanceKm = null;
  if (order && live) {
    const dest = demoDestination(order.restaurantLat, order.restaurantLng);
    distanceKm = haversineKm({ lat: live.lat, lng: live.lng }, dest);
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
