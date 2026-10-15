import 'fake-indexeddb/auto';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch';

const mocks = vi.hoisted(() => ({ saveTrackRiderWeight: vi.fn(), getSingleTrackRaw: vi.fn() }));
vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => ({
  ...(await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>()),
  EnergyControllerApi: vi.fn(function () {
    return mocks;
  }),
  TracksControllerApi: vi.fn(function () {
    return mocks;
  }),
}));
vi.mock('@/utils/openApiClient', () => ({ getApiConfiguration: vi.fn(() => ({})) }));

import { refreshTrackEnergy, saveTrackEnergyRiderWeight } from '@/utils/ServiceHelper';
import { fetchDetailTrack } from '@/utils/tracks/trackApi';
import { DETAIL_TRACK_PRECISION } from '@/utils/tracks/trackConstants';
import {
  clearTrackCacheDb,
  loadGeometry,
  loadTrack,
  saveGeometryRecords,
  saveTrackRecords,
} from '@/utils/tracks/trackCacheDb';

const previous: GpsTrack = {
  id: 42,
  energyNetTotalWh: 18,
  metabolicEnergy: { activeKcal: 93, totalKcal: 128, bodyWeightKg: 75, coverage: 'COMPLETE' },
};

describe('saved energy in the persistent track cache', () => {
  it('refreshes missing energy using coarse geometry and saves that geometry with its version', async () => {
    const updated = {
      ...previous,
      version: 2,
      energyNetTotalWh: 30,
      gpsTracksData: [
        {
          track: {
            type: 'LineString',
            coordinates: [
              [8, 47],
              [8.1, 47.1],
            ],
          },
        },
      ],
    };
    mocks.getSingleTrackRaw.mockResolvedValue({ raw: new Response(JSON.stringify(updated)) });
    await refreshTrackEnergy(42);
    expect(mocks.getSingleTrackRaw).toHaveBeenCalledWith(
      { gpsTrackId: 42, precisionInMeter: 1000 },
      { cache: 'no-cache' }
    );
    expect((await loadTrack(42))?.energyNetTotalWh).toBe(30);
    expect((await loadGeometry(42, 1000))?.coordinates).toEqual([
      [8, 47],
      [8.1, 47.1],
    ]);
  });
  beforeEach(async () => {
    vi.clearAllMocks();
    await clearTrackCacheDb();
    await saveTrackRecords(new Map([[42, previous]]));
  });

  it('replaces cached calories and work before reporting save success, retaining geometry and other tracks', async () => {
    const saved: GpsTrack = {
      ...previous,
      energyNetTotalWh: 20,
      metabolicEnergy: { activeKcal: 105, totalKcal: 145, bodyWeightKg: 85, coverage: 'COMPLETE' },
    };
    await saveTrackRecords(new Map([[43, { id: 43, trackName: 'Unchanged' }]]));
    await saveGeometryRecords([
      {
        trackId: 42,
        precision: 100,
        coordinates: [
          [8, 47],
          [8.1, 47.1],
        ],
      },
    ]);
    mocks.saveTrackRiderWeight.mockResolvedValueOnce(saved);

    expect(await saveTrackEnergyRiderWeight('42', 85)).toEqual(saved);
    expect(mocks.saveTrackRiderWeight).toHaveBeenCalledWith({ gpsTrackId: 42, riderWeightKg: 85 });
    expect(await loadTrack(42)).toEqual(saved);
    expect(await loadTrack(43)).toEqual({ id: 43, trackName: 'Unchanged' });
    expect((await loadGeometry(42, 100))?.coordinates).toEqual([
      [8, 47],
      [8.1, 47.1],
    ]);
  });

  it('retains the saved estimate when the server rejects an update', async () => {
    mocks.saveTrackRiderWeight.mockRejectedValueOnce(new Error('Save failed'));
    await expect(saveTrackEnergyRiderWeight(42, 85)).rejects.toThrow('Save failed');
    expect(await loadTrack(42)).toEqual(previous);
  });

  it('revalidates the HTTP response when reopening mutable track details', async () => {
    mocks.getSingleTrackRaw.mockResolvedValueOnce({ raw: new Response(JSON.stringify(previous)) });
    const controller = new AbortController();
    const result = await fetchDetailTrack({
      trackId: 42,
      precision: DETAIL_TRACK_PRECISION,
      signal: controller.signal,
    });
    expect(result.gpsTrack.metabolicEnergy).toEqual(previous.metabolicEnergy);
    expect(mocks.getSingleTrackRaw).toHaveBeenCalledWith(
      { gpsTrackId: 42, precisionInMeter: DETAIL_TRACK_PRECISION },
      { signal: controller.signal, cache: 'no-cache' }
    );
  });
});
