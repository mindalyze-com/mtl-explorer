import { describe, expect, it } from 'vitest';
import { GpsTrackToJSON, type GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import { normalizeTrackDates } from '@/utils/tracks/trackApi';

describe('trackApi', () => {
  it('normalizes raw API date strings before generated OpenAPI serialization', () => {
    const rawTrack = {
      id: 42,
      indexedFile: {
        id: 7,
        name: 'sample.gpx',
        lastModifiedDate: '2026-05-31T18:40:00.000+0000',
        createDate: '2026-05-31T18:41:00.000+0000',
        indexAddedDate: '2026-05-31T18:42:00.000+0000',
        indexUpdateDate: '2026-05-31T18:43:00.000+0000',
      },
      metaTime: '2026-05-31T18:44:00.000+0000',
      startDate: '2026-05-31T18:45:00.000+0000',
      endDate: '2026-05-31T18:46:00.000+0000',
      gpsTracksData: [
        {
          id: 8,
          createDate: '2026-05-31T18:47:00.000+0000',
          gpsTrackEvents: [
            {
              id: 9,
              startTimestamp: '2026-05-31T18:48:00.000+0000',
              endTimestamp: '2026-05-31T18:49:00.000+0000',
              createDate: '2026-05-31T18:50:00.000+0000',
              updateDate: '2026-05-31T18:51:00.000+0000',
            },
          ],
        },
      ],
      explorationCalcDate: '2026-05-31T18:52:00.000+0000',
      createDate: '2026-05-31T18:53:00.000+0000',
      updateDate: '2026-05-31T18:54:00.000+0000',
    } as unknown as GpsTrack;

    const normalized = normalizeTrackDates(rawTrack);

    expect(normalized.indexedFile?.lastModifiedDate).toBeInstanceOf(Date);
    expect(normalized.gpsTracksData?.[0]?.createDate).toBeInstanceOf(Date);
    expect(normalized.gpsTracksData?.[0]?.gpsTrackEvents?.[0]?.startTimestamp).toBeInstanceOf(Date);
    expect(() => GpsTrackToJSON({ ...normalized, activityType: 'WALKING' } as GpsTrack)).not.toThrow();
  });
});
