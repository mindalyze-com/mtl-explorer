/**
 * MapLibre custom control: a small button to toggle globe mode.
 * Visually matches the built-in NavigationControl buttons; stacks below them.
 *
 * Usage:
 *   const ctrl = new GlobeControl(() => toggleGlobe());
 *   map.addControl(ctrl, 'top-left');
 *   ctrl.setVisible(true);
 *   ctrl.setActive(true);
 */
export class GlobeControl {
  constructor(onToggle) {
    this._onToggle = onToggle;
    this._container = null;
    this._button = null;
  }

  onAdd() {
    this._container = document.createElement('div');
    this._container.className = 'maplibregl-ctrl maplibregl-ctrl-group mtl-globe-ctrl';

    this._button = document.createElement('button');
    this._button.type = 'button';
    this._button.className = 'mtl-globe-btn';
    this._button.setAttribute('title', 'Globe mode');
    this._button.setAttribute('aria-label', 'Toggle globe mode');
    this._button.innerHTML = '<i class="bi bi-globe2"></i>';
    this._button.addEventListener('click', () => this._onToggle());

    this._container.appendChild(this._button);
    this._container.style.display = 'none';
    return this._container;
  }

  onRemove() {
    this._container?.parentNode?.removeChild(this._container);
    this._container = null;
    this._button = null;
  }

  /** Show or hide the control (only visible at low zoom levels). */
  setVisible(visible) {
    if (this._container) this._container.style.display = visible ? '' : 'none';
  }

  /** Highlight the button when globe projection is active. */
  setActive(active) {
    if (this._button) this._button.classList.toggle('mtl-globe-active', active);
  }
}

/**
 * Compute the minimum zoom for globe mode so the globe just fits the viewport.
 * At zoom Z the globe diameter ≈ 512 × 2^Z / π pixels.
 * Inverted: Z = log2(minDim × fill × π / 512)
 * A fill factor < 1 leaves breathing room so the globe doesn't clip on smaller screens.
 * Falls back to 2.0 if the container is not yet sized.
 *
 * @param {HTMLElement} container - The map container element
 * @returns {number} minZoom
 */
export function computeGlobeMinZoom(container) {
  // 0.80 fill: globe occupies 80% of the short dimension — leaves room on tablets/phones
  const FILL_FACTOR = 0.80;
  const minDim = Math.min(container.clientWidth || 0, container.clientHeight || 0);
  if (minDim <= 0) return 2.0;
  return Math.log2((minDim * FILL_FACTOR * Math.PI) / 512);
}
