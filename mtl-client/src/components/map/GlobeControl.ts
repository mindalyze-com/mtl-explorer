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
export class GlobeControl implements maplibregl.IControl {
  private readonly onToggle: () => void;
  private container: HTMLDivElement | null = null;
  private button: HTMLButtonElement | null = null;

  constructor(onToggle: () => void) {
    this.onToggle = onToggle;
  }

  onAdd(): HTMLElement {
    this.container = document.createElement('div');
    this.container.className = 'maplibregl-ctrl maplibregl-ctrl-group mtl-globe-ctrl';

    this.button = document.createElement('button');
    this.button.type = 'button';
    this.button.className = 'mtl-globe-btn';
    this.button.setAttribute('title', 'Globe mode');
    this.button.setAttribute('aria-label', 'Toggle globe mode');
    this.button.innerHTML = '<i class="bi bi-globe2"></i>';
    this.button.addEventListener('click', () => this.onToggle());

    this.container.appendChild(this.button);
    this.container.style.display = 'none';
    return this.container;
  }

  onRemove(): void {
    this.container?.parentNode?.removeChild(this.container);
    this.container = null;
    this.button = null;
  }

  /** Show or hide the control (only visible at low zoom levels). */
  setVisible(visible: boolean): void {
    if (this.container) this.container.style.display = visible ? '' : 'none';
  }

  /** Highlight the button when globe projection is active. */
  setActive(active: boolean): void {
    if (this.button) this.button.classList.toggle('mtl-globe-active', active);
  }
}

/**
 * Compute the minimum zoom for globe mode so the globe just fits the viewport.
 * At zoom Z the globe diameter ≈ 512 × 2^Z / π pixels.
 * Inverted: Z = log2(minDim × fill × π / 512)
 * A fill factor < 1 leaves breathing room so the globe doesn't clip on smaller screens.
 * Falls back to 2.0 if the container is not yet sized.
 *
 */
export function computeGlobeMinZoom(container: HTMLElement): number {
  // 0.80 fill: globe occupies 80% of the short dimension — leaves room on tablets/phones
  const FILL_FACTOR = 0.8;
  const minDim = Math.min(container.clientWidth || 0, container.clientHeight || 0);
  if (minDim <= 0) return 2.0;
  return Math.log2((minDim * FILL_FACTOR * Math.PI) / 512);
}
import type maplibregl from 'maplibre-gl';
