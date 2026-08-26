import type { Map as MapLibreMap } from 'maplibre-gl';

const TRANSPARENT_IMAGE_SIZE_PX = 1;

/**
 * Resolve optional style images that are not present in the configured sprite.
 * MapLibre 6 requires the dedicated resolver API for on-demand images.
 */
export function installMissingStyleImageResolver(map: MapLibreMap): void {
  map.setMissingStyleImageResolver((id) => {
    if (map.hasImage(id)) return;

    map.addImage(id, {
      width: TRANSPARENT_IMAGE_SIZE_PX,
      height: TRANSPARENT_IMAGE_SIZE_PX,
      data: new Uint8ClampedArray(TRANSPARENT_IMAGE_SIZE_PX * TRANSPARENT_IMAGE_SIZE_PX * 4),
    });
  });
}
