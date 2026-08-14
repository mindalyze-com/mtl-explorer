import { MapToggleControl } from '@/components/map/MapToggleControl';

/**
 * MapLibre custom control for MTL Explorer's 3D terrain view.
 */
export class TerrainViewControl extends MapToggleControl {
  constructor(onToggle: () => void) {
    super(onToggle, {
      containerClass: 'mtl-terrain-ctrl',
      buttonClass: 'mtl-terrain-btn',
      activeClass: 'mtl-terrain-active',
      iconClass: 'bi bi-badge-3d',
      inactiveTitle: 'Enable 3D terrain',
      activeTitle: 'Disable 3D terrain',
    });
  }
}
