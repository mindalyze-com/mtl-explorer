import { afterEach, describe, expect, it, vi } from 'vitest';
import { GpsTrackToJSON, TracksControllerApi, type GpsTrack } from 'x8ing-mtl-api-typescript-fetch';
import { fetchArchiveTrackIds, fetchDetailTrack, normalizeTrackDates } from '@/utils/tracks/trackApi';
import { DETAIL_TRACK_PRECISION } from '@/utils/tracks/trackConstants';

afterEach(() => vi.restoreAllMocks());

describe('trackApi', () => {
  it('requests complete archive membership without an active filter or scope', async () => {
    const request = vi.spyOn(TracksControllerApi.prototype, 'getTracksSimplified1').mockResolvedValue({
      trackVersions: { '1': 3, '2': 7 },
      standardFilterCount: 2,
      numberOfFilteredMatchedTracks: 2,
    });
    expect(await fetchArchiveTrackIds()).toEqual(new Set([1, 2]));
    expect(request).toHaveBeenCalledWith(
      { mode: 'archive-ids' },
      { cache: 'no-cache', signal: expect.any(AbortSignal) }
    );
  });

  it.each([
    { filteredTracks: [], standardFilterCount: 0, numberOfFilteredMatchedTracks: 0 },
    { trackVersions: { '1': 1 }, standardFilterCount: 2, numberOfFilteredMatchedTracks: 2 },
  ])('rejects incomplete or older-server responses before cache deletion', async (response) => {
    vi.spyOn(TracksControllerApi.prototype, 'getTracksSimplified1').mockResolvedValue(response);
    await expect(fetchArchiveTrackIds()).rejects.toThrow('Complete track archive membership is unavailable.');
  });

  it('preserves structured non-Error transport failures from detail requests', async () => {
    const failure = { name: 'SyntheticTransportFailure', code: 'RETRY', response: { status: 503 } };
    vi.spyOn(TracksControllerApi.prototype, 'getSingleTrackRaw').mockRejectedValueOnce(failure);
    await expect(fetchDetailTrack({ trackId: 42, precision: DETAIL_TRACK_PRECISION })).rejects.toBe(failure);
  });

  it('revalidates detail responses so reopening shows changed activity and energy', async () => {
    const readDetail = vi.spyOn(TracksControllerApi.prototype, 'getSingleTrackRaw');
    for (const track of [
      { id: 42, activityType: 'BICYCLE', energyNetTotalWh: 197.5 },
      { id: 42, activityType: 'WALKING', activityTypeSource: 'USER_SET', energyNetTotalWh: 97.1 },
    ]) {
      readDetail.mockResolvedValueOnce({
        raw: new Response(JSON.stringify(track)),
        value: async () => track as GpsTrack,
      });
    }

    const request = { trackId: 42, precision: DETAIL_TRACK_PRECISION };
    expect((await fetchDetailTrack(request)).gpsTrack.activityType).toBe('BICYCLE');
    expect((await fetchDetailTrack(request)).gpsTrack).toMatchObject({
      activityType: 'WALKING',
      activityTypeSource: 'USER_SET',
      energyNetTotalWh: 97.1,
    });
    expect(readDetail).toHaveBeenCalledTimes(2);
    for (const [, options] of readDetail.mock.calls) {
      expect(options).toMatchObject({ cache: 'no-cache' });
    }
  });

  it('preserves distinct break locations while parsing event dates', () => {
    const rawTrack = {
      gpsTracksData: [
        {
          gpsTrackEvents: [
            {
              id: 1,
              startTimestamp: '2026-01-01T10:00:00Z',
              startPointLongLat: { type: 'Point', coordinates: [8, 47] },
              endPointLongLat: { type: 'Point', coordinates: [8.001, 47.001] },
            },
            {
              id: 2,
              startTimestamp: '2026-01-01T11:00:00Z',
              startPointLongLat: { type: 'Point', coordinates: [8.1, 47.1] },
            },
            { id: 3 },
          ],
        },
      ],
    } as unknown as GpsTrack;

    const events = normalizeTrackDates(rawTrack).gpsTracksData![0].gpsTrackEvents!;

    expect(events.map((event) => event.startPointLongLat)).toEqual(
      rawTrack.gpsTracksData![0].gpsTrackEvents!.map((event) => event.startPointLongLat)
    );
    expect(events[0].endPointLongLat).toEqual(rawTrack.gpsTracksData![0].gpsTrackEvents![0].endPointLongLat);
    expect(events[0].startTimestamp).toBeInstanceOf(Date);
    expect(events[1].startTimestamp).toBeInstanceOf(Date);
    expect(events[2].endPointLongLat).toBeUndefined();
  });

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
