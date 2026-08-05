import { useEffect, useRef } from 'react';
import L from 'leaflet';

/**
 * Initializes a Leaflet map once and exposes the container/map refs.
 * The map is torn down (and its container id cleared) on unmount so it can be
 * re-created cleanly if the component ever remounts.
 */
export function useLeafletMap({ center = [28.6139, 77.209], zoom = 13 } = {}) {
  const containerRef = useRef(null);
  const mapRef = useRef(null);

  useEffect(() => {
    if (!containerRef.current) return undefined;

    const map = L.map(containerRef.current, { center, zoom });
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
    }).addTo(map);

    mapRef.current = map;
    const t = setTimeout(() => map.invalidateSize(), 150);

    return () => {
      clearTimeout(t);
      map.remove();
      if (containerRef.current) containerRef.current._leaflet_id = undefined;
      mapRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return { containerRef, mapRef };
}
