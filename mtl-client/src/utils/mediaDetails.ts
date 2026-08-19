import type { MediaDetailsDto } from 'x8ing-mtl-api-typescript-fetch';
import { formatBytes, formatDateAndTime, formatElevation, formatLocaleNumber } from '@/utils/Utils';

const MEGAPIXEL_DIVISOR = 1_000_000;
const ONE_SECOND = 1;
const SECONDS_PER_MINUTE = 60;
const SECONDS_PER_HOUR = 3_600;
const RECIPROCAL_ROUNDING_TOLERANCE = 0.02;
const UNKNOWN_CODEC_VALUES = new Set(['unknown', 'undefined', 'n/a']);

const VIDEO_CODEC_LABELS: Record<string, string> = {
  avc1: 'H.264',
  avc3: 'H.264',
  h264: 'H.264',
  hvc1: 'HEVC',
  hev1: 'HEVC',
  hevc: 'HEVC',
  vp09: 'VP9',
  vp9: 'VP9',
  av01: 'AV1',
  av1: 'AV1',
  mp4v: 'MPEG-4',
  mjpg: 'Motion JPEG',
  mjpeg: 'Motion JPEG',
};

const AUDIO_CODEC_LABELS: Record<string, string> = {
  mp4a: 'AAC',
  aac: 'AAC',
  'ac-3': 'Dolby Digital',
  ac3: 'Dolby Digital',
  'ec-3': 'Dolby Digital Plus',
  eac3: 'Dolby Digital Plus',
  lpcm: 'Linear PCM',
  mp3: 'MP3',
};

export function formatMediaFileSummary(details: MediaDetailsDto | null): string {
  if (!details) return '';
  const extension = details.fileExtension?.trim().toUpperCase();
  const size = positiveNumber(details.fileSizeBytes);
  return [extension, size == null ? '' : formatBytes(size, 1)].filter(Boolean).join(' · ');
}

export function formatMediaDimensions(details: MediaDetailsDto | null): string {
  const width = positiveNumber(details?.widthPixels);
  const height = positiveNumber(details?.heightPixels);
  if (width == null || height == null) return '';
  const megapixels = (width * height) / MEGAPIXEL_DIVISOR;
  return `${Math.round(width)} × ${Math.round(height)} · ${formatLocaleNumber(megapixels, 1)} MP`;
}

export function formatPhotoExposure(details: MediaDetailsDto | null): string {
  if (!details) return '';
  const focalLength35Mm = positiveNumber(details.focalLength35Mm);
  const focalLengthMm = positiveNumber(details.focalLengthMm);
  const aperture = positiveNumber(details.apertureFNumber);
  const iso = positiveNumber(details.isoSpeed);
  const values = [
    focalLength35Mm != null
      ? `${formatCompactNumber(focalLength35Mm)} mm eq.`
      : focalLengthMm != null
        ? `${formatCompactNumber(focalLengthMm)} mm`
        : '',
    aperture == null ? '' : `ƒ/${formatCompactNumber(aperture)}`,
    formatExposureTime(details.exposureTimeSeconds),
    iso == null ? '' : `ISO ${Math.round(iso)}`,
  ];
  return values.filter(Boolean).join(' · ');
}

export function formatVideoDetails(details: MediaDetailsDto | null): string {
  if (!details) return '';
  const duration = formatMediaDuration(details.durationSeconds);
  const width = positiveNumber(details.widthPixels);
  const height = positiveNumber(details.heightPixels);
  const dimensions = width != null && height != null ? `${Math.round(width)} × ${Math.round(height)}` : '';
  const frameRate = positiveNumber(details.frameRate);
  const fps = frameRate == null ? '' : `${formatCompactNumber(frameRate)} fps`;
  return [duration, dimensions, fps].filter(Boolean).join(' · ');
}

export function formatMediaCodecs(details: MediaDetailsDto | null): string {
  if (!details) return '';
  const video = codecLabel(details.videoCodec, VIDEO_CODEC_LABELS);
  const audio = codecLabel(details.audioCodec, AUDIO_CODEC_LABELS);
  return [video, audio].filter(Boolean).join(' · ');
}

export function formatMediaModified(details: MediaDetailsDto | null): string {
  return details?.lastModifiedAt ? formatDateAndTime(details.lastModifiedAt) : '';
}

export function formatMediaAltitude(details: MediaDetailsDto | null): string {
  const altitude = finiteNumber(details?.gpsAltitudeMeters);
  return altitude == null ? '' : formatElevation(altitude);
}

function formatMediaDuration(value: number | null | undefined): string {
  const seconds = positiveNumber(value);
  if (seconds == null) return '';
  const rounded = Math.round(seconds);
  const hours = Math.floor(rounded / SECONDS_PER_HOUR);
  const minutes = Math.floor((rounded % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
  const remainingSeconds = rounded % SECONDS_PER_MINUTE;
  if (hours > 0) {
    return `${hours}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`;
  }
  return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
}

function formatExposureTime(value: number | null | undefined): string {
  const seconds = positiveNumber(value);
  if (seconds == null) return '';
  if (seconds < ONE_SECOND) {
    const reciprocal = 1 / seconds;
    const rounded = Math.round(reciprocal);
    if (Math.abs(reciprocal - rounded) / reciprocal <= RECIPROCAL_ROUNDING_TOLERANCE) {
      return `1/${rounded} s`;
    }
  }
  return `${formatCompactNumber(seconds)} s`;
}

function codecLabel(value: string | null | undefined, labels: Record<string, string>): string {
  const normalized = value?.replaceAll('\0', '').trim().toLowerCase();
  if (!normalized || UNKNOWN_CODEC_VALUES.has(normalized)) return '';
  if (normalized.includes('advanced audio coding') || normalized.includes('(aac)')) return 'AAC';
  return labels[normalized] ?? normalized.toUpperCase();
}

function formatCompactNumber(value: number): string {
  const fractionDigits = Number.isInteger(value) ? 0 : value < 10 ? 1 : 2;
  return formatLocaleNumber(value, fractionDigits);
}

function positiveNumber(value: number | null | undefined): number | null {
  const number = finiteNumber(value);
  return number != null && number > 0 ? number : null;
}

function finiteNumber(value: number | null | undefined): number | null {
  return value != null && Number.isFinite(value) ? value : null;
}
