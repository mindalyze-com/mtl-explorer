import type { Map as MapLibreMap } from 'maplibre-gl';
import { POI_CATEGORIES } from './poiCategories';
import { POI_ICON_PIXEL_RATIO, POI_ICON_SIZE, POI_LAYER_PREFIX } from './poiLayers';

const iconSources = import.meta.glob<string>(
  [
    '/node_modules/bootstrap-icons/icons/triangle-fill.svg',
    '/node_modules/bootstrap-icons/icons/binoculars-fill.svg',
    '/node_modules/bootstrap-icons/icons/droplet-fill.svg',
    '/node_modules/bootstrap-icons/icons/house-door-fill.svg',
    '/node_modules/bootstrap-icons/icons/signpost-split-fill.svg',
    '/node_modules/bootstrap-icons/icons/info-circle-fill.svg',
    '/node_modules/bootstrap-icons/icons/cup-hot-fill.svg',
    '/node_modules/bootstrap-icons/icons/basket-fill.svg',
    '/node_modules/bootstrap-icons/icons/bicycle.svg',
    '/node_modules/bootstrap-icons/icons/bus-front-fill.svg',
    '/node_modules/bootstrap-icons/icons/fuel-pump-fill.svg',
    '/node_modules/bootstrap-icons/icons/person-arms-up.svg',
    '/node_modules/bootstrap-icons/icons/heart-pulse-fill.svg',
    '/node_modules/bootstrap-icons/icons/bank.svg',
    '/node_modules/bootstrap-icons/icons/camera-fill.svg',
    '/node_modules/bootstrap-icons/icons/tree-fill.svg',
    '/node_modules/bootstrap-icons/icons/shop.svg',
    '/node_modules/bootstrap-icons/icons/water.svg',
    '/node_modules/bootstrap-icons/icons/buildings-fill.svg',
    '/node_modules/bootstrap-icons/icons/geo-alt-fill.svg',
    '/node_modules/bootstrap-icons/icons/p-circle-fill.svg',
    '/node_modules/bootstrap-icons/icons/fire.svg',
  ],
  {
    query: '?raw',
    import: 'default',
    eager: true,
  }
);
const imageCache = new Map<string, Promise<ImageData>>();
const ICON_INSET = 4;
const BOOTSTRAP_ICON_SIZE = 16;
const ICON_BORDER_INSET = 1;
const ICON_BORDER_WIDTH = 1.2;

export function createPoiIconImage(icon: string, color: string): Promise<ImageData> {
  const key = `${icon}:${color}`;
  const cached = imageCache.get(key);
  if (cached) return cached;
  const promise = (async () => {
    const raw = iconSources[`/node_modules/bootstrap-icons/icons/${icon}.svg`];
    if (!raw) throw new Error(`Missing POI icon: ${icon}`);
    const paths = raw.slice(raw.indexOf('>') + 1, raw.lastIndexOf('</svg>'));
    const center = POI_ICON_SIZE / 2;
    const scale = (POI_ICON_SIZE - ICON_INSET * 2) / BOOTSTRAP_ICON_SIZE;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${POI_ICON_SIZE}" height="${POI_ICON_SIZE}" viewBox="0 0 ${POI_ICON_SIZE} ${POI_ICON_SIZE}"><circle cx="${center}" cy="${center}" r="${center - ICON_BORDER_INSET}" fill="#fffdf8" stroke="${color}" stroke-width="${ICON_BORDER_WIDTH}"/><g transform="translate(${ICON_INSET} ${ICON_INSET}) scale(${scale})" fill="${color}">${paths}</g></svg>`;
    const img = new Image();
    const loaded = new Promise<void>((resolve, reject) => {
      img.onload = () => resolve();
      img.onerror = () => reject(new Error(`Could not load POI icon: ${icon}`));
    });
    img.src = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
    await loaded;
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = POI_ICON_SIZE * POI_ICON_PIXEL_RATIO;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('Could not draw POI icons');
    context.drawImage(img, 0, 0, canvas.width, canvas.height);
    return context.getImageData(0, 0, canvas.width, canvas.height);
  })();
  imageCache.set(key, promise);
  return promise;
}

export async function installPoiImages(map: MapLibreMap, isCurrent: () => boolean): Promise<boolean> {
  const images = await Promise.all(
    POI_CATEGORIES.map(async (category) => ({
      id: `${POI_LAYER_PREFIX}${category.id}`,
      image: await createPoiIconImage(category.icon, category.color),
    }))
  );
  if (!isCurrent()) return false;
  for (const { id, image } of images)
    if (!map.hasImage(id)) map.addImage(id, image, { pixelRatio: POI_ICON_PIXEL_RATIO });
  return true;
}
