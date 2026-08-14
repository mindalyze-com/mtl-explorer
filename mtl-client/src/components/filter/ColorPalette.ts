import type { ConfigEntity } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ConfigEntity';

import { CONFIG_DOMAIN1_CLIENT, fetchConfig } from '@/utils/ServiceHelper';

import { markRaw } from 'vue';

export class ColorPalette {
  id!: number;
  domain1?: string;
  domain2?: string;
  domain3?: string;
  value?: string;
  description?: string;

  pLabel?: string;
  pDescription?: string;
  pColors?: string[];

  // Keep a mapping of groups to assigned colors
  // mark the member "RAW" to tell vuejs there's no need to re-evaluate the DOM after a change here...
  private groupColorMap: Map<string, string> = markRaw(new Map());

  private groupColorCounter: Map<string, number> = markRaw(new Map());

  public reset() {
    this.groupColorMap.clear();
    this.groupColorCounter.clear();
  }

  private incrementGroupCounter(group: string, countForStatistics: boolean) {
    if (!countForStatistics) return;
    if (!this.groupColorCounter.has(group)) {
      this.groupColorCounter.set(group, 0);
    }
    this.groupColorCounter.set(group, (this.groupColorCounter.get(group) || 0) + 1);
  }

  // Method to assign a color to a group
  public getColorForGroup(group: string, countForStatistics: boolean = false): string {
    return this.assignGroupColor(group, countForStatistics, (colors) => this.groupColorMap.size % colors.length);
  }

  public getColorForGroupAtIndex(group: string, colorIndex: number, countForStatistics: boolean = false): string {
    return this.assignGroupColor(group, countForStatistics, (colors) =>
      Math.max(0, Math.min(colors.length - 1, colorIndex))
    );
  }

  private assignGroupColor(
    group: string,
    countForStatistics: boolean,
    resolveColorIndex: (colors: string[]) => number
  ): string {
    this.incrementGroupCounter(group, countForStatistics);

    if (this.groupColorMap.has(group)) return this.groupColorMap.get(group)!;

    if (!this.pColors || this.pColors.length === 0) {
      return '#FF0000';
    }

    const assignedColor = this.pColors[resolveColorIndex(this.pColors)];
    this.groupColorMap.set(group, assignedColor);
    return assignedColor;
  }

  public getColorMap() {
    return this.groupColorMap;
  }

  public getGroupColorCounter() {
    return this.groupColorCounter;
  }

  public isColorPaletteExhausted(): boolean {
    if (this.isEmptyColorPalette()) {
      return false;
    }
    // Check if the number of assigned groups exceeds or equals the number of available colors
    return this.groupColorMap.size > (this.pColors?.length || 0);
  }

  public isEmptyColorPalette(): boolean {
    return !(this.pColors && this.pColors.length > 0 && this.id);
  }

  static of(configEntity: ConfigEntity | ColorPalette | undefined | null): ColorPalette {
    if (configEntity instanceof ColorPalette) return configEntity;

    const colorPalette = new ColorPalette();

    if (configEntity) {
      colorPalette.id = configEntity.id!;
      colorPalette.domain1 = configEntity.domain1;
      colorPalette.domain2 = configEntity.domain2;
      colorPalette.domain3 = configEntity.domain3;
      colorPalette.value = configEntity.value;
      colorPalette.description = configEntity.description;

      if (configEntity.value) {
        const p = JSON.parse(configEntity.value);
        if (p) {
          colorPalette.pLabel = p.label;
          colorPalette.pDescription = p.description;
          colorPalette.pColors = p.colors;
        }
      }
    }

    return colorPalette;
  }

  static ofArray(configEntities: ConfigEntity[]): ColorPalette[] {
    if (configEntities) {
      return configEntities.map((entity) => ColorPalette.of(entity));
    } else {
      return [];
    }
  }

  static async fetch(): Promise<ColorPalette[]> {
    try {
      const configs = await fetchConfig(CONFIG_DOMAIN1_CLIENT, 'COLOR_PALETTE');
      if (configs) {
        return this.ofArray(configs);
      }
    } catch {
      // Server unreachable or config not yet saved — colour palette is optional, degrade gracefully
    }
    return [];
  }
}
