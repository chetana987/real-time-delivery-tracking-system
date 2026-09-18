import { useEffect, useState } from 'react';
import { api } from '../lib/api';

function coord(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/**
 * Simple order-placement form for the customer dashboard.
 * Captures the delivery destination explicitly: the address is typed by the
 * customer, and latitude/longitude can be filled from browser geolocation as a
 * starting point (the customer is expected to confirm/edit them — the device's
 * current position is not automatically the delivery address).
 */
export default function PlaceOrderForm({ onPlaced }) {
  const [restaurants, setRestaurants] = useState([]);
  const [menu, setMenu] = useState([]);
  const [restaurantId, setRestaurantId] = useState('');
  const [menuItemId, setMenuItemId] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [address, setAddress] = useState('');
  const [lat, setLat] = useState('');
  const [lng, setLng] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [locationBusy, setLocationBusy] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [placed, setPlaced] = useState('');

  useEffect(() => {
    api
      .restaurants()
      .then((list) => {
        setRestaurants(list);
        if (list.length > 0) setRestaurantId(String(list[0].id));
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!restaurantId) {
      setMenu([]);
      return;
    }
    api
      .restaurantMenu(restaurantId)
      .then((items) => {
        setMenu(items);
        setMenuItemId(items.length > 0 ? String(items[0].id) : '');
      })
      .catch((err) => setError(err.message));
  }, [restaurantId]);

  function useCurrentLocation() {
    if (!('geolocation' in navigator)) {
      setError('Browser geolocation is not available — enter the coordinates manually.');
      return;
    }
    setLocationBusy(true);
    setError('');
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setLat(pos.coords.latitude.toFixed(6));
        setLng(pos.coords.longitude.toFixed(6));
        setLocationBusy(false);
      },
      () => {
        setError('Could not read your current location — enter the coordinates manually.');
        setLocationBusy(false);
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setFieldErrors({});
    setPlaced('');

    const deliveryLatitude = coord(lat);
    const deliveryLongitude = coord(lng);
    if (!restaurantId) return setError('Pick a restaurant.');
    if (!menuItemId) return setError('Pick a menu item.');
    if (!address.trim()) return setError('Enter a delivery address.');
    if (deliveryLatitude == null || deliveryLatitude < -90 || deliveryLatitude > 90) {
      return setError('Latitude must be a number between -90 and 90.');
    }
    if (deliveryLongitude == null || deliveryLongitude < -180 || deliveryLongitude > 180) {
      return setError('Longitude must be a number between -180 and 180.');
    }

    setSubmitting(true);
    try {
      await api.placeOrder({
        restaurantId: Number(restaurantId),
        deliveryAddress: address.trim(),
        deliveryLatitude,
        deliveryLongitude,
        items: [{ menuItemId: Number(menuItemId), quantity: Number(quantity) || 1 }],
      });
      setPlaced(`Order placed — you can track it above.`);
      setQuantity('1');
      setAddress('');
      setLat('');
      setLng('');
      onPlaced?.();
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors || {});
    } finally {
      setSubmitting(false);
    }
  }

  const input =
    'w-full rounded-xl border border-cream-200 bg-white px-3 py-2 text-sm text-charcoal-900 focus:border-terra-400 focus:outline-none';

  return (
    <div className="rounded-2xl border border-cream-200 bg-white p-5 shadow-sm">
      <h3 className="text-sm font-bold uppercase tracking-wider text-charcoal-600">Place a new order</h3>

      <form onSubmit={handleSubmit} className="mt-4 grid gap-3 sm:grid-cols-2">
        <label className="block text-sm font-medium text-charcoal-700">
          Restaurant
          <select
            className={input}
            value={restaurantId}
            onChange={(e) => setRestaurantId(e.target.value)}
            disabled={loading}
          >
            {restaurants.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm font-medium text-charcoal-700">
          Menu item
          <select className={input} value={menuItemId} onChange={(e) => setMenuItemId(e.target.value)}>
            {menu.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name} — ${Number(item.price).toFixed(2)}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm font-medium text-charcoal-700">
          Quantity
          <input
            type="number"
            min="1"
            step="1"
            className={input}
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </label>

        <label className="block text-sm font-medium text-charcoal-700">
          Delivery address
          <input
            type="text"
            className={`${input} ${fieldErrors.deliveryAddress ? 'border-red-300' : ''}`}
            placeholder="123 Main Street, Pune"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
          />
          {fieldErrors.deliveryAddress && (
            <span className="mt-1 block text-xs text-red-600">{fieldErrors.deliveryAddress}</span>
          )}
        </label>

        <label className="block text-sm font-medium text-charcoal-700">
          Delivery latitude
          <input
            type="number"
            step="any"
            className={`${input} ${fieldErrors.deliveryLatitude ? 'border-red-300' : ''}`}
            placeholder="18.5204"
            value={lat}
            onChange={(e) => setLat(e.target.value)}
          />
          {fieldErrors.deliveryLatitude && (
            <span className="mt-1 block text-xs text-red-600">{fieldErrors.deliveryLatitude}</span>
          )}
        </label>

        <label className="block text-sm font-medium text-charcoal-700">
          Delivery longitude
          <input
            type="number"
            step="any"
            className={`${input} ${fieldErrors.deliveryLongitude ? 'border-red-300' : ''}`}
            placeholder="73.8567"
            value={lng}
            onChange={(e) => setLng(e.target.value)}
          />
          {fieldErrors.deliveryLongitude && (
            <span className="mt-1 block text-xs text-red-600">{fieldErrors.deliveryLongitude}</span>
          )}
        </label>

        <div className="sm:col-span-2">
          <button
            type="button"
            onClick={useCurrentLocation}
            disabled={locationBusy}
            className="rounded-xl border border-cream-200 bg-cream-50 px-3 py-2 text-xs font-semibold text-charcoal-700 transition hover:bg-cream-100 disabled:cursor-not-allowed disabled:opacity-60"
          >
            {locationBusy ? 'Reading location…' : 'Use my current location as a starting point'}
          </button>
          <p className="mt-1 text-xs text-charcoal-500">
            Your device's position is only a starting point — confirm it matches the delivery address.
          </p>
        </div>

        {error && (
          <p
            role="alert"
            className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-sm font-medium text-red-700 sm:col-span-2"
          >
            {error}
          </p>
        )}
        {placed && (
          <p className="rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-800 sm:col-span-2">
            {placed}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting || loading}
          className="sm:col-span-2 rounded-xl bg-terra-500 px-4 py-2.5 text-sm font-bold text-white shadow-sm transition hover:bg-terra-600 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {submitting ? 'Placing order…' : 'Place order'}
        </button>
      </form>
    </div>
  );
}