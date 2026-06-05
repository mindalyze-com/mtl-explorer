import type maplibregl from 'maplibre-gl';

/**
 * MapLibre custom control for MTL Explorer's 3D terrain view.
 */
export class TerrainViewControl implements maplibregl.IControl {
  private readonly onToggle: () => void;
  private container: HTMLDivElement | null = null;
  private button: HTMLButtonElement | null = null;

  constructor(onToggle: () => void) {
    this.onToggle = onToggle;
  }

  onAdd(): HTMLElement {
    this.container = document.createElement('div');
    this.container.className = 'maplibregl-ctrl maplibregl-ctrl-group mtl-terrain-ctrl';

    this.button = document.createElement('button');
    this.button.type = 'button';
    this.button.className = 'mtl-terrain-btn';
    this.button.setAttribute('title', 'Enable 3D terrain');
    this.button.setAttribute('aria-label', 'Enable 3D terrain');
    this.button.setAttribute('aria-pressed', 'false');
    this.button.innerHTML = '<i class="bi bi-badge-3d"></i>';
    this.button.addEventListener('click', () => this.onToggle());

    this.container.appendChild(this.button);
    return this.container;
  }

  onRemove(): void {
    this.container?.parentNode?.removeChild(this.container);
    this.container = null;
    this.button = null;
  }

  setActive(active: boolean): void {
    if (this.button) {
      this.button.classList.toggle('mtl-terrain-active', active);
      this.button.setAttribute('aria-pressed', String(active));
      this.button.setAttribute('title', active ? 'Disable 3D terrain' : 'Enable 3D terrain');
      this.button.setAttribute('aria-label', active ? 'Disable 3D terrain' : 'Enable 3D terrain');
    }
  }
}
