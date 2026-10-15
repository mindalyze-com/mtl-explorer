import type { Map } from 'maplibre-gl';

/** Publish read-only camera values for diagnostics and independent pixel projection. */
export function observeMapCamera(map: Map): void {
  const publish = () => {
    const center = map.getCenter();
    map.getCanvas().dataset.mtlCamera = JSON.stringify({
      longitude: center.lng,
      latitude: center.lat,
      zoom: map.getZoom(),
      bearing: map.getBearing(),
      pitch: map.getPitch(),
      padding: map.getPadding(),
    });
  };
  map.on('moveend', publish);
  map.on('resize', publish);
  map.on('load', publish);
  publish();
}
