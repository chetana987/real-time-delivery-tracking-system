import { useCallback, useEffect, useRef } from 'react';
import L from 'leaflet';

/**
 * A map marker that does NOT teleport. Instead of snapping to each incoming
 * coordinate, a requestAnimationFrame loop lerps the marker toward the latest
 * target every frame (easing at ~15% of the remaining distance per frame).
 *
 * - Each WebSocket location update only sets a new "target".
 * - The marker visibly travels between old and new positions.
 * - If updates pause, the marker converges exactly onto the last target.
 * - The rAF loop is cancelled and the marker removed on unmount.
 */
export function useSmoothMarker(mapRef) {
  const markerRef = useRef(null);
  const targetRef = useRef(null);
  const hasTargetRef = useRef(false);

  useEffect(() => {
    const map = mapRef.current;
    if (!map) return undefined;

    const icon = L.divIcon({
      className: 'delivery-marker',
      html: '<div class="marker-dot"></div>',
      iconSize: [22, 22],
      iconAnchor: [11, 11],
    });
    const marker = L.marker([0, 0], { icon }).addTo(map);
    markerRef.current = marker;

    let raf;
    const tick = () => {
      const m = markerRef.current;
      const target = targetRef.current;
      if (m && target) {
        const cur = m.getLatLng();
        const dx = target.lat - cur.lat;
        const dy = target.lng - cur.lng;
        if (Math.abs(dx) < 1e-7 && Math.abs(dy) < 1e-7) {
          m.setLatLng([target.lat, target.lng]);
        } else {
          m.setLatLng([cur.lat + dx * 0.15, cur.lng + dy * 0.15]);
        }
      }
      raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);

    return () => {
      cancelAnimationFrame(raf);
      if (markerRef.current) {
        map.removeLayer(markerRef.current);
        markerRef.current = null;
      }
    };
  }, [mapRef]);

  const moveTo = useCallback((latlng) => {
    targetRef.current = { lat: latlng.lat, lng: latlng.lng };
    if (!hasTargetRef.current && markerRef.current) {
      // First fix: snap to the initial position (there is no prior point to animate from).
      markerRef.current.setLatLng([latlng.lat, latlng.lng]);
    }
    hasTargetRef.current = true;
  }, []);

  return { markerRef, moveTo };
}
