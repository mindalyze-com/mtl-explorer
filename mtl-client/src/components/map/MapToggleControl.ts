import type * as maplibregl from 'maplibre-gl';

export interface MapToggleControlOptions {
  containerClass: string;
  buttonClass: string;
  activeClass: string;
  iconClass: string;
  inactiveTitle: string;
  activeTitle?: string;
  inactiveAriaLabel?: string;
  activeAriaLabel?: string;
  initiallyVisible?: boolean;
}

/** Shared DOM lifecycle and accessibility behavior for map toggle controls. */
export class MapToggleControl implements maplibregl.IControl {
  private container: HTMLDivElement | null = null;
  private button: HTMLButtonElement | null = null;

  constructor(
    private readonly onToggle: () => void,
    private readonly options: MapToggleControlOptions
  ) {}

  onAdd(): HTMLElement {
    this.container = document.createElement('div');
    this.container.className = `maplibregl-ctrl maplibregl-ctrl-group ${this.options.containerClass}`;

    this.button = document.createElement('button');
    this.button.type = 'button';
    this.button.className = this.options.buttonClass;
    this.button.innerHTML = `<i class="${this.options.iconClass}"></i>`;
    this.button.addEventListener('click', this.onToggle);
    this.updateActiveState(false);

    this.container.appendChild(this.button);
    if (this.options.initiallyVisible === false) this.container.style.display = 'none';
    return this.container;
  }

  onRemove(): void {
    this.button?.removeEventListener('click', this.onToggle);
    this.container?.remove();
    this.container = null;
    this.button = null;
  }

  setVisible(visible: boolean): void {
    if (this.container) this.container.style.display = visible ? '' : 'none';
  }

  setActive(active: boolean): void {
    this.updateActiveState(active);
  }

  private updateActiveState(active: boolean): void {
    if (!this.button) return;
    const title = active ? (this.options.activeTitle ?? this.options.inactiveTitle) : this.options.inactiveTitle;
    const ariaLabel = active
      ? (this.options.activeAriaLabel ?? this.options.activeTitle ?? title)
      : (this.options.inactiveAriaLabel ?? title);

    this.button.classList.toggle(this.options.activeClass, active);
    this.button.setAttribute('aria-pressed', String(active));
    this.button.setAttribute('title', title);
    this.button.setAttribute('aria-label', ariaLabel);
  }
}
