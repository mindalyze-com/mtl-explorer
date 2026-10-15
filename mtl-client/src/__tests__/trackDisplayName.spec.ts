import { describe, expect, it } from 'vitest';
import { ref } from 'vue';
import { trackDisplayName } from '@/utils/tracks/trackDisplayName';
import { useTrackBrowser } from '@/components/track-browser/useTrackBrowser';
import { useTrackLayers } from '@/components/map/composables/useTrackLayers';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';

describe('Track display names', () => {
  it.each([
    [{ trackName: ' Route ', metaName: 'Metadata' }, 'Route'],
    [{ trackName: ' ', metaName: ' Metadata route ', trackDescription: 'Description' }, 'Metadata route'],
    [{ trackDescription: ' Description route ', metaDescription: 'Metadata description' }, 'Description route'],
    [{ metaDescription: ' Metadata description ' }, 'Metadata description'],
    [{}, 'Track 7'],
  ] as [GpsTrack, string][])('shares the same fallback between the list and map chooser (%s)', (fields, expected) => {
    const track = { id: 7, ...fields };
    expect(trackDisplayName(track)).toBe(expected);
    expect(useTrackBrowser(ref([track])).rows.value[0].displayName).toBe(expected);
    const methods = useTrackLayers();
    const metadata = methods.getTrackPopupMeta.call({ gpsTracksById: new Map([[7, track]]) } as never, 7);
    expect(metadata.displayName).toBe(expected);
  });
});
