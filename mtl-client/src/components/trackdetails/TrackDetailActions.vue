<template>
  <div class="track-actions" role="group" aria-label="Track actions">
    <button
      v-if="canDownloadTrackSource"
      type="button"
      class="track-action"
      :disabled="currentTrackDownloadKind !== null"
      aria-label="Download original"
      title="Download original indexed file"
      @click.stop="runTrackDownload('original')"
    >
      <i :class="currentTrackDownloadKind === 'original' ? 'pi pi-spin pi-spinner' : 'bi bi-download'"></i>
    </button>
    <button
      v-if="canDownloadGpx"
      type="button"
      class="track-action"
      :disabled="currentTrackDownloadKind !== null"
      aria-label="Download GPX"
      title="Download as GPX"
      @click.stop="runTrackDownload('gpx')"
    >
      <i :class="currentTrackDownloadKind === 'gpx' ? 'pi pi-spin pi-spinner' : 'bi bi-file-earmark-code'"></i>
    </button>
    <button
      type="button"
      class="track-action track-action--replay"
      data-test="track-start-3d-replay"
      :disabled="!replayEnabled"
      aria-label="Start 3D replay"
      :title="replayEnabled ? 'Start 3D replay' : '3D replay requires loaded track geometry'"
      @click.stop="emit('start-3d-replay')"
    >
      <i class="bi bi-badge-3d"></i>
      <span>3D Replay</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { warnSanitized } from '@/utils/safeLogging';

import { computed, inject, ref } from 'vue';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { downloadTrackGpx, downloadTrackSourceFile } from '@/utils/ServiceHelper';
import type { ToastService } from '@/types/ui';

type TrackDownloadKind = 'original' | 'gpx';
type ActiveTrackDownload = { kind: TrackDownloadKind; serial: number; trackId: number };

const GPS_INDEX_NAME = 'GPS';
const GPX_FILE_EXTENSION = '.gpx';
const UNAVAILABLE_INDEXER_STATUSES = new Set(['REMOVED', 'EXCLUDED']);
const DOWNLOAD_ERROR_TOAST_MS = 4000;

const props = defineProps<{ gpsTrack?: GpsTrack | null; replayEnabled?: boolean }>();
const emit = defineEmits<{ 'start-3d-replay': [] }>();
const toast = inject<ToastService>('toast', { add: () => undefined });
const activeDownload = ref<ActiveTrackDownload | null>(null);
let trackDownloadSerial = 0;

const sourceFileName = computed(
  () => props.gpsTrack?.indexedFile?.name || props.gpsTrack?.trackName || props.gpsTrack?.metaName || 'track-source'
);
const canDownloadTrackSource = computed(() => {
  const track = props.gpsTrack;
  const indexedFile = track?.indexedFile;
  return (
    !!track?.id &&
    indexedFile?.index === GPS_INDEX_NAME &&
    !UNAVAILABLE_INDEXER_STATUSES.has(indexedFile.indexerStatus ?? '')
  );
});
const canDownloadGpx = computed(
  () => canDownloadTrackSource.value && !sourceFileName.value.toLowerCase().endsWith(GPX_FILE_EXTENSION)
);
const currentTrackDownloadKind = computed(() => {
  const download = activeDownload.value;
  return download?.trackId === props.gpsTrack?.id ? (download?.kind ?? null) : null;
});

async function runTrackDownload(kind: TrackDownloadKind): Promise<void> {
  const trackId = props.gpsTrack?.id;
  if (trackId == null || !canDownloadTrackSource.value || currentTrackDownloadKind.value !== null) return;
  const serial = ++trackDownloadSerial;
  activeDownload.value = { kind, serial, trackId };
  try {
    if (kind === 'original') {
      await downloadTrackSourceFile(trackId, sourceFileName.value);
    } else {
      await downloadTrackGpx(trackId, sourceFileName.value);
    }
  } catch (error) {
    warnSanitized(
      '[track-details] failed to download track file',
      { trackId, kind, error },
      { topic1: 'tracks', topic2: 'TrackDetailActions', topic3: 'runTrackDownload' }
    );
    toast.add({
      severity: 'error',
      summary: 'Download failed',
      detail: kind === 'original' ? 'Could not download the original indexed file.' : 'Could not download GPX.',
      life: DOWNLOAD_ERROR_TOAST_MS,
    });
  } finally {
    if (activeDownload.value?.serial === serial) activeDownload.value = null;
  }
}
</script>

<style scoped>
.track-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-right: 8px;
}
.track-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-width: 36px;
  min-height: 36px;
  padding: 8px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-card);
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.track-action--replay {
  color: var(--accent-text);
  padding-inline: 12px;
}
.track-action:hover:not(:disabled) {
  background: var(--accent-bg);
  border-color: var(--accent-muted);
  color: var(--accent-text);
}
.track-action:disabled {
  opacity: 0.45;
  cursor: default;
}
.track-action:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
@media (max-width: 640px) {
  .track-actions {
    gap: 2px;
    margin-right: 0;
  }
  .track-action {
    min-width: 32px;
    min-height: 44px;
    padding: 6px;
  }
  .track-action--replay span {
    display: none;
  }
}
</style>
