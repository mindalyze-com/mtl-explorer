<template>
  <div class="cvp">
    <video
      ref="videoEl"
      :src="activeSrc"
      :poster="poster"
      :controls="!showBlockingStatus"
      playsinline
      preload="metadata"
      :aria-label="label"
      class="cvp__video mp__media mp__media--video"
      :class="{ 'cvp__video--entering mp__media--entering': entering }"
      @click="onVideoClick"
      @play="onVideoPlay"
      @ended="onVideoEnded"
      @loadedmetadata="onLoadedMetadata"
      @error="onVideoError"
    />

    <button
      v-if="showPlayOverlay"
      type="button"
      class="cvp__play mp__video-play"
      :aria-label="playLabel"
      :title="playLabel"
      @dblclick.stop
      @pointerdown.stop
      @pointerup.stop
      @pointercancel.stop
      @click.stop="playVideo"
    >
      <i class="bi bi-play-fill" aria-hidden="true"></i>
    </button>

    <section
      v-if="showBlockingStatus"
      class="cvp__panel"
      :role="mode === 'failed' ? 'alert' : 'status'"
      aria-live="polite"
      data-test="video-transcode-panel"
    >
      <i :class="statusIcon" aria-hidden="true"></i>
      <strong>{{ statusTitle }}</strong>
      <span>{{ statusDetail }}</span>
      <span v-if="slowTranscode" class="cvp__warning">This quality may pause during playback.</span>

      <div class="cvp__actions">
        <label class="cvp__quality">
          <span>Quality</span>
          <select v-model="selectedQuality" aria-label="Compatible video quality" @change="onQualityChange">
            <option v-for="option in VIDEO_TRANSCODE_QUALITY_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>
        <button v-if="canRetry" type="button" class="cvp__button" @click="startCompatiblePlayback()">
          <i class="bi bi-arrow-clockwise" aria-hidden="true"></i>
          {{ mode === 'fallback' ? 'Create compatible stream' : 'Retry' }}
        </button>
        <button v-if="canCancel" type="button" class="cvp__button" @click="cancelCurrentSession(true)">
          <i class="bi bi-x-lg" aria-hidden="true"></i>
          Cancel
        </button>
      </div>
    </section>

    <section
      v-else-if="showCompactStatus"
      class="cvp__status"
      role="status"
      aria-live="polite"
      data-test="video-transcode-status"
    >
      <div>
        <strong>{{ statusTitle }}</strong>
        <span>{{ statusDetail }}</span>
      </div>
      <label class="cvp__quality cvp__quality--compact">
        <span class="visually-hidden">Compatible video quality</span>
        <select v-model="selectedQuality" aria-label="Compatible video quality" @change="onQualityChange">
          <option v-for="option in VIDEO_TRANSCODE_QUALITY_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <button
        type="button"
        class="cvp__cancel"
        aria-label="Cancel compatible video"
        @click="cancelCurrentSession(true)"
      >
        <i class="bi bi-x-lg" aria-hidden="true"></i>
      </button>
    </section>
  </div>
</template>

<script setup lang="ts">
import type Hls from 'hls.js';
import type { ErrorData } from 'hls.js';
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import {
  cancelVideoTranscodeSession,
  createVideoTranscodeSession,
  getVideoTranscodeSession,
  videoTranscodePlaylistUrl,
  VIDEO_TRANSCODE_QUALITY_OPTIONS,
  type VideoTranscodeQuality,
  type VideoTranscodeSession,
} from '@/repositories/mediaRepository';

type PlayerMode = 'original' | 'fallback' | 'starting' | 'transcoding' | 'compatible' | 'failed' | 'cancelled';

const HLS_MIME_TYPE = 'application/vnd.apple.mpegurl';
const TRANSCODE_STATUS_POLL_INTERVAL_MS = 1_000;
const REAL_TIME_TRANSCODE_SPEED = 1;
const MEDIA_ERROR_ABORTED = 1;
const MEDIA_ERROR_DECODE = 3;
const MEDIA_ERROR_SOURCE_NOT_SUPPORTED = 4;
const MAX_HLS_MEDIA_RECOVERY_ATTEMPTS = 2;
const DEFAULT_VIDEO_TRANSCODE_QUALITY: VideoTranscodeQuality = 'AUTO';

const props = withDefaults(
  defineProps<{
    mediaId: number;
    src: string;
    poster?: string;
    label?: string;
    entering?: boolean;
  }>(),
  {
    poster: undefined,
    label: 'Selected video',
    entering: false,
  }
);

const emit = defineEmits<{
  'media-error': [event: Event];
}>();

const videoEl = ref<HTMLVideoElement | null>(null);
const activeSrc = ref<string | undefined>(props.src);
const mode = ref<PlayerMode>('original');
const selectedQuality = ref<VideoTranscodeQuality>(DEFAULT_VIDEO_TRANSCODE_QUALITY);
const session = ref<VideoTranscodeSession | null>(null);
const failureMessage = ref('');
const playlistAttached = ref(false);
const videoHasStarted = ref(false);
const videoEnded = ref(false);

let hls: Hls | null = null;
let pollTimer: ReturnType<typeof setTimeout> | null = null;
let requestController: AbortController | null = null;
let operationId = 0;
let hlsMediaRecoveryAttempts = 0;

const isActiveSession = computed(() => mode.value === 'starting' || mode.value === 'transcoding');
const canCancel = computed(() => isActiveSession.value);
const canRetry = computed(() => mode.value === 'fallback' || mode.value === 'failed' || mode.value === 'cancelled');
const showBlockingStatus = computed(() => canRetry.value || (isActiveSession.value && !playlistAttached.value));
const showCompactStatus = computed(
  () =>
    Boolean(session.value?.sessionId) &&
    playlistAttached.value &&
    session.value?.state !== 'COMPLETED' &&
    session.value?.state !== 'FAILED' &&
    session.value?.state !== 'CANCELLED'
);
const showPlayOverlay = computed(
  () =>
    !showBlockingStatus.value &&
    (mode.value === 'original' || mode.value === 'compatible') &&
    (!videoHasStarted.value || videoEnded.value)
);
const playLabel = computed(() => (props.label === 'Selected video' ? 'Play video' : `Play ${props.label}`));
const selectedQualityLabel = computed(
  () => VIDEO_TRANSCODE_QUALITY_OPTIONS.find((option) => option.value === selectedQuality.value)?.label ?? 'Auto'
);
const progressPercent = computed(() => {
  const current = session.value;
  if (!current || current.sourceDurationSeconds <= 0 || current.encodedSeconds < 0) return null;
  return Math.min(100, Math.round((current.encodedSeconds / current.sourceDurationSeconds) * 100));
});
const slowTranscode = computed(() => {
  const speed = session.value?.transcodeSpeed;
  return speed != null && speed > 0 && speed < REAL_TIME_TRANSCODE_SPEED;
});
const statusIcon = computed(() => {
  if (mode.value === 'failed') return 'bi bi-exclamation-triangle';
  if (mode.value === 'cancelled') return 'bi bi-stop-circle';
  if (mode.value === 'fallback') return 'bi bi-film';
  return 'bi bi-arrow-repeat cvp__spin';
});
const statusTitle = computed(() => {
  if (mode.value === 'fallback') return 'This browser cannot play the original video';
  if (mode.value === 'failed') return 'Compatible playback failed';
  if (mode.value === 'cancelled') return 'Compatible video cancelled';
  if (playlistAttached.value) return `Creating compatible ${selectedQualityLabel.value} video`;
  return `Preparing compatible ${selectedQualityLabel.value} video…`;
});
const statusDetail = computed(() => {
  if (mode.value === 'fallback') return 'Create a temporary browser-compatible stream.';
  if (mode.value === 'failed') return failureMessage.value || 'The compatible stream could not be created.';
  if (mode.value === 'cancelled') return 'The temporary stream was removed.';

  const details: string[] = [];
  if (session.value?.reused) details.push('Reusing prepared work');
  if (progressPercent.value != null) details.push(`${progressPercent.value}%`);
  if (session.value?.transcodeSpeed && session.value.transcodeSpeed > 0) {
    details.push(`${session.value.transcodeSpeed.toFixed(1)}× speed`);
  }
  return details.length > 0 ? details.join(' · ') : 'Playback will start when enough video is ready.';
});

function clearPoll(): void {
  if (pollTimer != null) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
}

function abortRequest(): void {
  requestController?.abort();
  requestController = null;
}

function destroyHls(): void {
  hls?.destroy();
  hls = null;
  hlsMediaRecoveryAttempts = 0;
}

function resetPlayer(): void {
  operationId++;
  clearPoll();
  abortRequest();
  destroyHls();
  mode.value = 'original';
  activeSrc.value = props.src;
  session.value = null;
  failureMessage.value = '';
  playlistAttached.value = false;
  videoHasStarted.value = false;
  videoEnded.value = false;
}

function schedulePoll(id: number): void {
  clearPoll();
  pollTimer = setTimeout(() => void pollSession(id), TRANSCODE_STATUS_POLL_INTERVAL_MS);
}

function isCurrentOperation(id: number): boolean {
  return id === operationId;
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError';
}

function requestErrorMessage(error: unknown): string {
  const status = (error as { response?: { status?: number; data?: { message?: string } } })?.response?.status;
  const serverMessage = (error as { response?: { data?: { message?: string } } })?.response?.data?.message;
  if (status === 429) return 'The server is already preparing another video. Try again shortly.';
  return serverMessage || 'The compatible stream could not be created.';
}

async function attachPlaylist(currentSession: VideoTranscodeSession, id: number): Promise<void> {
  if (!isCurrentOperation(id) || playlistAttached.value) return;
  const video = videoEl.value;
  if (!video) return;

  playlistAttached.value = true;
  const playlistUrl = videoTranscodePlaylistUrl(currentSession.playlistUrl);
  if (video.canPlayType(HLS_MIME_TYPE)) {
    activeSrc.value = playlistUrl;
    await nextTick();
    if (!isCurrentOperation(id)) return;
    video.load();
    return;
  }

  const hlsModule = await import('hls.js');
  if (!isCurrentOperation(id)) return;
  const HlsConstructor = hlsModule.default;
  if (!HlsConstructor.isSupported()) {
    failCompatiblePlayback('This browser cannot play the compatible HLS stream.');
    return;
  }

  activeSrc.value = undefined;
  await nextTick();
  if (!isCurrentOperation(id)) return;

  const nextHls = new HlsConstructor({
    xhrSetup(xhr) {
      xhr.withCredentials = true;
    },
  });
  hls = nextHls;
  nextHls.on(hlsModule.Events.MEDIA_ATTACHED, () => {
    if (isCurrentOperation(id)) nextHls.loadSource(playlistUrl);
  });
  nextHls.on(hlsModule.Events.MANIFEST_PARSED, () => {
    if (isCurrentOperation(id)) mode.value = 'compatible';
  });
  nextHls.on(hlsModule.Events.ERROR, (_event, data: ErrorData) => onHlsError(data, hlsModule.ErrorTypes));
  nextHls.attachMedia(video);
}

function onHlsError(data: ErrorData, errorTypes: typeof import('hls.js').ErrorTypes): void {
  if (!data.fatal || !hls) return;
  if (data.type === errorTypes.NETWORK_ERROR) {
    hls.startLoad();
    return;
  }
  if (data.type === errorTypes.MEDIA_ERROR) {
    if (hlsMediaRecoveryAttempts < MAX_HLS_MEDIA_RECOVERY_ATTEMPTS) {
      hlsMediaRecoveryAttempts++;
      hls.recoverMediaError();
      return;
    }
    failCompatiblePlayback('The compatible stream could not be decoded by the browser.');
    return;
  }
  failCompatiblePlayback('The compatible stream stopped unexpectedly.');
}

function applySessionStatus(nextSession: VideoTranscodeSession, id: number): void {
  if (!isCurrentOperation(id)) return;
  session.value = nextSession;
  selectedQuality.value = nextSession.quality;

  if (nextSession.state === 'FAILED') {
    failCompatiblePlayback(nextSession.message || 'The server could not create a compatible stream.');
    return;
  }
  if (nextSession.state === 'CANCELLED') {
    mode.value = 'cancelled';
    clearPoll();
    return;
  }
  if (nextSession.playlistReady) void attachPlaylist(nextSession, id);
  if (nextSession.state === 'COMPLETED') {
    clearPoll();
    return;
  }
  if (mode.value !== 'compatible') mode.value = 'transcoding';
  schedulePoll(id);
}

async function pollSession(id: number): Promise<void> {
  const sessionId = session.value?.sessionId;
  if (!sessionId || !isCurrentOperation(id)) return;

  abortRequest();
  const controller = new AbortController();
  requestController = controller;
  try {
    const nextSession = await getVideoTranscodeSession(sessionId, controller.signal);
    if (requestController === controller) requestController = null;
    applySessionStatus(nextSession, id);
  } catch (error) {
    if (!isCurrentOperation(id) || controller.signal.aborted || isAbortError(error)) return;
    requestController = null;
    schedulePoll(id);
  }
}

async function startCompatiblePlayback(): Promise<void> {
  const id = ++operationId;
  clearPoll();
  abortRequest();
  destroyHls();
  activeSrc.value = undefined;
  session.value = null;
  playlistAttached.value = false;
  failureMessage.value = '';
  videoHasStarted.value = false;
  videoEnded.value = false;
  mode.value = 'starting';

  const controller = new AbortController();
  requestController = controller;
  try {
    const nextSession = await createVideoTranscodeSession(props.mediaId, selectedQuality.value, controller.signal);
    if (!isCurrentOperation(id)) return;
    requestController = null;
    applySessionStatus(nextSession, id);
  } catch (error) {
    if (!isCurrentOperation(id) || controller.signal.aborted || isAbortError(error)) return;
    requestController = null;
    failCompatiblePlayback(requestErrorMessage(error));
  }
}

function failCompatiblePlayback(message: string): void {
  clearPoll();
  abortRequest();
  destroyHls();
  activeSrc.value = undefined;
  playlistAttached.value = false;
  failureMessage.value = message;
  mode.value = 'failed';
}

async function cancelCurrentSession(showCancelled: boolean): Promise<boolean> {
  const sessionId = session.value?.sessionId;
  operationId++;
  clearPoll();
  abortRequest();
  destroyHls();
  activeSrc.value = undefined;
  playlistAttached.value = false;
  videoHasStarted.value = false;
  videoEnded.value = false;

  if (sessionId) {
    try {
      await cancelVideoTranscodeSession(sessionId);
    } catch (error) {
      failCompatiblePlayback(requestErrorMessage(error));
      return false;
    }
  }
  if (showCancelled) mode.value = 'cancelled';
  return true;
}

async function onQualityChange(): Promise<void> {
  if (mode.value === 'fallback' || mode.value === 'failed' || mode.value === 'cancelled') return;
  if (!(await cancelCurrentSession(false))) return;
  await startCompatiblePlayback();
}

function onVideoError(event: Event): void {
  const code = videoEl.value?.error?.code;
  if (code === MEDIA_ERROR_ABORTED) return;
  if (mode.value === 'original') {
    if (code === MEDIA_ERROR_DECODE || code === MEDIA_ERROR_SOURCE_NOT_SUPPORTED) {
      activeSrc.value = undefined;
      mode.value = 'fallback';
      return;
    }
    emit('media-error', event);
    return;
  }
  if (mode.value === 'compatible' || mode.value === 'transcoding') {
    if (hls) return;
    failCompatiblePlayback('The compatible stream could not be decoded by the browser.');
  }
}

function playVideo(): void {
  const video = videoEl.value;
  if (!video) return;
  if (videoEnded.value) video.currentTime = 0;
  const playback = video.play();
  if (playback) void playback.catch(() => undefined);
}

function onVideoClick(): void {
  if (showPlayOverlay.value) playVideo();
}

function onVideoPlay(): void {
  videoHasStarted.value = true;
  videoEnded.value = false;
  hlsMediaRecoveryAttempts = 0;
}

function onVideoEnded(): void {
  videoEnded.value = true;
}

function onLoadedMetadata(): void {
  if (playlistAttached.value) mode.value = 'compatible';
}

watch(() => [props.mediaId, props.src] as const, resetPlayer);

onBeforeUnmount(() => {
  operationId++;
  clearPoll();
  abortRequest();
  destroyHls();
});
</script>

<style scoped>
.cvp {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.cvp__video {
  position: relative;
  z-index: 1;
  display: block;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  pointer-events: auto;
}

.cvp__video--entering {
  animation: cvp-dissolve-in 190ms ease-out;
}

.cvp__play {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 4;
  display: grid;
  width: clamp(3.75rem, 7vw, 4.5rem);
  height: clamp(3.75rem, 7vw, 4.5rem);
  padding: 0 0 0 0.15rem;
  place-items: center;
  color: #fff;
  background: rgba(7, 11, 18, 0.76);
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 999px;
  box-shadow: var(--mp-overlay-shadow);
  font-size: clamp(2rem, 4vw, 2.5rem);
  line-height: 1;
  cursor: pointer;
  pointer-events: auto;
  transform: translate(-50%, -50%);
  transition:
    background 0.15s,
    border-color 0.15s,
    transform 0.15s;
}

.cvp__play:hover {
  background: rgba(7, 11, 18, 0.9);
  border-color: #fff;
}

.cvp__play:active {
  transform: translate(-50%, -50%) scale(0.96);
}

.cvp__play:focus-visible,
.cvp__button:focus-visible,
.cvp__cancel:focus-visible,
.cvp select:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.cvp__panel {
  position: absolute;
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.45rem;
  width: min(28rem, calc(100% - 2rem));
  padding: 1rem;
  color: var(--mp-text);
  text-align: center;
  background: color-mix(in srgb, var(--mp-surface) 94%, transparent);
  border: 1px solid var(--mp-border-strong);
  border-radius: 10px;
  box-shadow: var(--mp-overlay-shadow);
  pointer-events: auto;
}

.cvp__panel > i {
  color: var(--warning);
  font-size: var(--text-2xl-size);
}

.cvp__panel strong,
.cvp__status strong {
  color: var(--mp-text-strong);
}

.cvp__panel > span,
.cvp__status span {
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.cvp__warning {
  color: var(--warning);
}

.cvp__actions {
  display: flex;
  align-items: end;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 0.25rem;
  flex-wrap: wrap;
}

.cvp__quality {
  display: flex;
  flex-direction: column;
  align-items: start;
  gap: 0.2rem;
  color: var(--mp-text-muted);
  font-size: var(--text-xs-size);
}

.cvp__quality select,
.cvp__button,
.cvp__cancel {
  min-height: 2rem;
  color: var(--mp-text-strong);
  background: var(--mp-control-bg);
  border: 1px solid var(--mp-border-strong);
  border-radius: 6px;
}

.cvp__quality select {
  padding: 0.25rem 1.75rem 0.25rem 0.5rem;
}

.cvp__button {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.25rem 0.65rem;
  cursor: pointer;
}

.cvp__status {
  position: absolute;
  top: 0.75rem;
  left: 0.75rem;
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 0.65rem;
  max-width: calc(100% - 1.5rem);
  padding: 0.45rem 0.55rem;
  color: var(--mp-text);
  background: color-mix(in srgb, var(--mp-surface) 92%, transparent);
  border: 1px solid var(--mp-border);
  border-radius: 8px;
  box-shadow: var(--mp-overlay-shadow);
  pointer-events: auto;
}

.cvp__status > div {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.cvp__quality--compact {
  flex: 0 0 auto;
}

.cvp__cancel {
  display: inline-grid;
  flex: 0 0 auto;
  width: 2rem;
  padding: 0;
  place-items: center;
  cursor: pointer;
}

.cvp__spin {
  animation: cvp-spin 0.9s linear infinite;
}

@keyframes cvp-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes cvp-dissolve-in {
  from {
    opacity: 0;
    transform: scale(1.008);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 640px) {
  .cvp__status {
    right: 0.5rem;
    left: 0.5rem;
    max-width: none;
  }

  .cvp__status > div {
    flex: 1 1 auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .cvp__video--entering,
  .cvp__spin,
  .cvp__play {
    animation: none !important;
    transition: none !important;
  }
}
</style>
