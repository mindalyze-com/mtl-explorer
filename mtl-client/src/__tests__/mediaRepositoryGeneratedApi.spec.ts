import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  clearManualMediaLocation,
  cancelVideoTranscodeSession,
  createVideoTranscodeSession,
  getMediaByTrack,
  getMediaInfo,
  getMediaTrendItems,
  getMediaTrends,
  getVideoTranscodeSession,
  saveMediaTimeCorrections,
  setManualMediaLocation,
} from '@/repositories/mediaRepository';

const generatedApiMock = vi.hoisted(() => ({
  configuration: { test: 'configuration' },
  constructor: vi.fn(),
  getMediaByTrack: vi.fn(),
  getMediaTrends: vi.fn(),
  getMediaTrendItems: vi.fn(),
  getMediaDetails: vi.fn(),
  saveMediaTimeCorrections: vi.fn(),
  setManualMediaLocation: vi.fn(),
  clearManualMediaLocation: vi.fn(),
  createVideoTranscodeSession: vi.fn(),
  getVideoTranscodeSession: vi.fn(),
  cancelVideoTranscodeSession: vi.fn(),
}));

vi.mock('@/utils/openApiClient', () => ({
  getApiConfiguration: () => generatedApiMock.configuration,
}));

vi.mock('x8ing-mtl-api-typescript-fetch', async (importOriginal) => {
  const original = await importOriginal<typeof import('x8ing-mtl-api-typescript-fetch')>();
  return {
    ...original,
    MediaControllerApi: class {
      constructor(configuration: unknown) {
        generatedApiMock.constructor(configuration);
      }

      getMediaByTrack = generatedApiMock.getMediaByTrack;
      getMediaTrends = generatedApiMock.getMediaTrends;
      getMediaTrendItems = generatedApiMock.getMediaTrendItems;
      getMediaDetails = generatedApiMock.getMediaDetails;
      saveMediaTimeCorrections = generatedApiMock.saveMediaTimeCorrections;
      setManualMediaLocation = generatedApiMock.setManualMediaLocation;
      clearManualMediaLocation = generatedApiMock.clearManualMediaLocation;
    },
    VideoTranscodeControllerApi: class {
      constructor(configuration: unknown) {
        generatedApiMock.constructor(configuration);
      }

      createVideoTranscodeSession = generatedApiMock.createVideoTranscodeSession;
      getVideoTranscodeSession = generatedApiMock.getVideoTranscodeSession;
      cancelVideoTranscodeSession = generatedApiMock.cancelVideoTranscodeSession;
    },
  };
});

describe('mediaRepository activity timeline', () => {
  beforeEach(() => {
    generatedApiMock.constructor.mockClear();
    generatedApiMock.getMediaByTrack.mockReset();
    generatedApiMock.getMediaTrends.mockReset();
    generatedApiMock.getMediaTrendItems.mockReset();
    generatedApiMock.getMediaDetails.mockReset();
    generatedApiMock.saveMediaTimeCorrections.mockReset();
    generatedApiMock.setManualMediaLocation.mockReset();
    generatedApiMock.clearManualMediaLocation.mockReset();
    generatedApiMock.createVideoTranscodeSession.mockReset();
    generatedApiMock.getVideoTranscodeSession.mockReset();
    generatedApiMock.cancelVideoTranscodeSession.mockReset();
  });

  it('uses generated media-trend methods and forwards cancellation', async () => {
    const trends = { scope: 'MATCHED_ACTIVITIES', buckets: [] };
    const items = { items: [], page: 0, pageSize: 60, totalItems: 0, totalPages: 0 };
    generatedApiMock.getMediaTrends.mockResolvedValue(trends);
    generatedApiMock.getMediaTrendItems.mockResolvedValue(items);
    const signal = new AbortController().signal;
    const trendRequest = { grouping: 'MONTH' as const, scope: 'MATCHED_ACTIVITIES' as const, trackIds: [42] };
    const itemRequest = {
      grouping: 'MONTH' as const,
      scope: 'MATCHED_ACTIVITIES' as const,
      bucketKey: '2026-08',
      kind: 'ALL' as const,
      trackIds: [42],
      page: 0,
      pageSize: 60,
    };

    await expect(getMediaTrends(trendRequest, signal)).resolves.toBe(trends);
    await expect(getMediaTrendItems(itemRequest, signal)).resolves.toBe(items);

    expect(generatedApiMock.getMediaTrends).toHaveBeenCalledWith({ mediaTrendRequest: trendRequest }, { signal });
    expect(generatedApiMock.getMediaTrendItems).toHaveBeenCalledWith(
      { mediaTrendItemsRequest: itemRequest },
      { signal }
    );
  });

  it('uses the generated controller, query parameter, and abort signal', async () => {
    const response = {
      items: [{ id: 17, positionOrigin: 'TRACK_INTERPOLATED', estimatedPosition: true }],
      page: 2,
      pageSize: 50,
      totalItems: 101,
      totalPages: 3,
    };
    generatedApiMock.getMediaByTrack.mockResolvedValue(response);
    const signal = new AbortController().signal;

    await expect(getMediaByTrack(42, -3600, 2, 50, signal)).resolves.toBe(response);

    expect(generatedApiMock.constructor).toHaveBeenCalledWith(generatedApiMock.configuration);
    expect(generatedApiMock.getMediaByTrack).toHaveBeenCalledWith(
      { trackId: 42, cameraOffsetSeconds: -3600, page: 2, pageSize: 50 },
      { signal }
    );
  });

  it('uses the generated media details method and forwards cancellation', async () => {
    const details = { id: 17, mediaKind: 'IMAGE', fileName: 'photo.jpg', fileSizeBytes: 4_800_000 };
    generatedApiMock.getMediaDetails.mockResolvedValue(details);
    const signal = new AbortController().signal;

    await expect(getMediaInfo(17, signal)).resolves.toBe(details);

    expect(generatedApiMock.constructor).toHaveBeenCalledWith(generatedApiMock.configuration);
    expect(generatedApiMock.getMediaDetails).toHaveBeenCalledWith({ id: 17 }, { signal });
  });

  it('uses generated mutation methods for persisted corrections and manual locations', async () => {
    generatedApiMock.saveMediaTimeCorrections.mockResolvedValue(undefined);
    generatedApiMock.setManualMediaLocation.mockResolvedValue(undefined);
    generatedApiMock.clearManualMediaLocation.mockResolvedValue(undefined);

    await expect(saveMediaTimeCorrections({ mediaIds: [17], offsetSeconds: 3600 })).resolves.toBeUndefined();
    await setManualMediaLocation(17, { latitude: 47.4, longitude: 8.5, note: 'Trail' });
    await clearManualMediaLocation(17);

    expect(generatedApiMock.saveMediaTimeCorrections).toHaveBeenCalledWith({
      mediaTimeCorrectionRequest: { mediaIds: [17], offsetSeconds: 3600 },
    });
    expect(generatedApiMock.setManualMediaLocation).toHaveBeenCalledWith({
      mediaId: 17,
      manualMediaLocationRequest: { latitude: 47.4, longitude: 8.5, note: 'Trail' },
    });
    expect(generatedApiMock.clearManualMediaLocation).toHaveBeenCalledWith({ mediaId: 17 });
  });

  it('uses the generated video transcode controller and forwards cancellation', async () => {
    const created = { sessionId: 'session-1', quality: 'P720', state: 'STARTING' };
    const running = { ...created, state: 'RUNNING' };
    generatedApiMock.createVideoTranscodeSession.mockResolvedValue(created);
    generatedApiMock.getVideoTranscodeSession.mockResolvedValue(running);
    generatedApiMock.cancelVideoTranscodeSession.mockResolvedValue(undefined);
    const signal = new AbortController().signal;

    await expect(createVideoTranscodeSession(17, 'P720', signal)).resolves.toBe(created);
    await expect(getVideoTranscodeSession('session-1', signal)).resolves.toBe(running);
    await expect(cancelVideoTranscodeSession('session-1')).resolves.toBeUndefined();

    expect(generatedApiMock.createVideoTranscodeSession).toHaveBeenCalledWith(
      { mediaId: 17, videoTranscodeSessionRequest: { quality: 'P720' } },
      { signal }
    );
    expect(generatedApiMock.getVideoTranscodeSession).toHaveBeenCalledWith({ sessionId: 'session-1' }, { signal });
    expect(generatedApiMock.cancelVideoTranscodeSession).toHaveBeenCalledWith({ sessionId: 'session-1' });
  });
});
