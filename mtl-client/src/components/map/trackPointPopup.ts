import * as maplibregl from 'maplibre-gl';
import './trackPointPopup.css';

export const TRACK_POINT_POPUP_MAX_WIDTH = 'min(360px, calc(100vw - 16px))';

export type TrackPointPopupRow = {
  label: string;
  value: string;
};

type CreateTrackPointPopupOptions = {
  map: maplibregl.Map;
  lngLat: maplibregl.LngLatLike;
  title: string;
  rows: readonly TrackPointPopupRow[];
  closeOnClick?: boolean;
};

function createTrackPointPopupContent(title: string, rows: readonly TrackPointPopupRow[]): HTMLDivElement {
  const content = document.createElement('div');
  content.className = 'mtl-point-popup';

  const header = document.createElement('div');
  header.className = 'mtl-point-popup-header';
  header.textContent = title;
  content.appendChild(header);

  const table = document.createElement('table');
  table.className = 'mtl-point-popup-table';
  const body = table.createTBody();

  for (const row of rows) {
    const tableRow = body.insertRow();
    const label = tableRow.insertCell();
    label.className = 'mtl-pp-label';
    label.textContent = row.label;

    const value = tableRow.insertCell();
    value.className = 'mtl-pp-value';
    value.textContent = row.value;
  }

  content.appendChild(table);
  return content;
}

export function updateTrackPointPopupContent(
  popup: maplibregl.Popup,
  title: string,
  rows: readonly TrackPointPopupRow[]
): maplibregl.Popup {
  return popup.setDOMContent(createTrackPointPopupContent(title, rows));
}

export function createTrackPointPopup({
  map,
  lngLat,
  title,
  rows,
  closeOnClick = true,
}: CreateTrackPointPopupOptions): maplibregl.Popup {
  return new maplibregl.Popup({
    closeButton: true,
    closeOnClick,
    maxWidth: TRACK_POINT_POPUP_MAX_WIDTH,
    className: 'mtl-point-popup-container',
  })
    .setLngLat(lngLat)
    .setDOMContent(createTrackPointPopupContent(title, rows))
    .addTo(map);
}
