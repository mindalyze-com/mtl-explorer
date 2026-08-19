import { beforeEach, describe, expect, it } from 'vitest';
import {
  formatMediaCodecs,
  formatMediaDimensions,
  formatMediaFileSummary,
  formatPhotoExposure,
  formatVideoDetails,
} from '@/utils/mediaDetails';

describe('media detail formatting', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('formats a compact file and photo summary', () => {
    const details = {
      fileExtension: 'heic',
      fileSizeBytes: 5_033_165,
      widthPixels: 4032,
      heightPixels: 3024,
      apertureFNumber: 1.8,
      exposureTimeSeconds: 1 / 250,
      isoSpeed: 50,
      focalLengthMm: 6.86,
      focalLength35Mm: 26,
    };

    expect(formatMediaFileSummary(details)).toBe('HEIC · 4.8 MB');
    expect(formatMediaDimensions(details)).toMatch(/^4032 × 3024 · 12[.,]2 MP$/);
    expect(formatPhotoExposure(details)).toMatch(/^26 mm eq\. · ƒ\/1[.,]8 · 1\/250 s · ISO 50$/);
  });

  it('formats video duration, dimensions, frame rate, and known codecs', () => {
    const details = {
      durationSeconds: 84.25,
      widthPixels: 3840,
      heightPixels: 2160,
      frameRate: 29.97,
      videoCodec: 'hvc1',
      audioCodec: 'mp4a',
    };

    expect(formatVideoDetails(details)).toMatch(/^1:24 · 3840 × 2160 · 29[.,]97 fps$/);
    expect(formatMediaCodecs(details)).toBe('HEVC · AAC');
  });

  it('hides unknown codec placeholders and shortens verbose AAC metadata', () => {
    const details = {
      videoCodec: 'UNKNOWN',
      audioCodec: 'MPEG-4, ADVANCED AUDIO CODING (AAC)',
    };

    expect(formatMediaCodecs(details)).toBe('AAC');
  });

  it('omits missing or invalid metadata without placeholders', () => {
    expect(formatMediaFileSummary({ fileSizeBytes: -1 })).toBe('');
    expect(formatMediaDimensions({ widthPixels: 0, heightPixels: 3024 })).toBe('');
    expect(formatPhotoExposure({})).toBe('');
    expect(formatVideoDetails({ durationSeconds: Number.NaN })).toBe('');
    expect(formatMediaCodecs({ videoCodec: 'vp09' })).toBe('VP9');
  });
});
