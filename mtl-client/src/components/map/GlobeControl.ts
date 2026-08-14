import { MapToggleControl } from '@/components/map/MapToggleControl';

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
export class GlobeControl extends MapToggleControl {
  constructor(onToggle: () => void) {
    super(onToggle, {
      containerClass: 'mtl-globe-ctrl',
      buttonClass: 'mtl-globe-btn',
      activeClass: 'mtl-globe-active',
      iconClass: 'bi bi-globe2',
      inactiveTitle: 'Globe mode',
      inactiveAriaLabel: 'Toggle globe mode',
      activeAriaLabel: 'Toggle globe mode',
      initiallyVisible: false,
    });
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
