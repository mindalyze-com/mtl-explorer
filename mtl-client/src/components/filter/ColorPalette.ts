import {
    ConfigEntityFromJSONTyped,
    type ConfigEntity
} from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/ConfigEntity';

import {CONFIG_DOMAIN1_CLIENT, fetchConfig} from "@/utils/ServiceHelper";

import {markRaw} from 'vue';

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

    // Method to assign a color to a group
    public getColorForGroup(group: string, countForStatistics: boolean=false): string {

        // handle the counting
        if (countForStatistics && !this.groupColorCounter.has(group)) {
            this.groupColorCounter.set(group, 0);
        }
        this.groupColorCounter.set(group, (this.groupColorCounter.get(group) || 0) + 1); // increment

        // If the group is already assigned a color, return it
        if (this.groupColorMap.has(group)) {
            return this.groupColorMap.get(group)!;
        }

        // If no colors are defined, return a default color (red in this case)
        if (!this.pColors || this.pColors.length === 0) {
            return '#FF0000';
        }

        // Calculate the index of the color for this group by cycling through available colors
        const colorIndex = this.groupColorMap.size % this.pColors.length;

        // Assign the calculated color to the group
        const assignedColor: string = this.pColors[colorIndex];
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
        if(this.isEmptyColorPalette()){
            return false;
        }
        // Check if the number of assigned groups exceeds or equals the number of available colors
        return this.groupColorMap.size > (this.pColors?.length || 0);
    }

    public isEmptyColorPalette(): boolean {
        return !(this.pColors && this.pColors.length > 0 && this.id);
    }

    static of(configEntity: ConfigEntity | undefined | null): ColorPalette {
        let colorPalette = new ColorPalette();

        if (configEntity) {
            colorPalette.id = configEntity.id!;
            colorPalette.domain1 = configEntity.domain1;
            colorPalette.domain2 = configEntity.domain2;
            colorPalette.domain3 = configEntity.domain3;
            colorPalette.value = configEntity.value;
            colorPalette.description = configEntity.description;

            if (configEntity.value) {
                let p = JSON.parse(configEntity.value);
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
            return configEntities.map(entity => ColorPalette.of(entity));
        } else {
            return [];
        }
    }

    static async fetch(): Promise<ColorPalette[]> {
        try {
            const configs = await fetchConfig(CONFIG_DOMAIN1_CLIENT, "COLOR_PALETTE");
            if (configs) {
                return this.ofArray(configs);
            }
        } catch {
            // Server unreachable or config not yet saved — colour palette is optional, degrade gracefully
        }
        return [];
    }

}
