const VIDEO_FILE_NAME_PATTERN = /\.(mp4|mov|m4v|3gp|avi|mkv)$/i;

export function isVideoMedia(fileName?: string | null, mediaKind?: string | null): boolean {
  return mediaKind?.toUpperCase() === 'VIDEO' || VIDEO_FILE_NAME_PATTERN.test(fileName ?? '');
}
