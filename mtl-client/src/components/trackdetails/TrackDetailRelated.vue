<template>
  <div v-if="relatedTracks" class="related-container" :class="{ 'related-container--loading': showLoadingIndicator }">
    <!-- Thin indeterminate progress bar (top) — only appears if load takes >250ms -->
    <div v-if="showLoadingIndicator" class="loading-bar" aria-busy="true" aria-label="Loading track"></div>
    <!-- Transparent click blocker — always on during isLoading, even before bar appears.
         This prevents double-taps from stacking navigations without any visual flicker. -->
    <div v-if="isLoading" class="loading-click-blocker" aria-hidden="true"></div>

    <!-- ── PREVIOUS TRACKS ───────────────────────────────── -->
    <section class="timeline-section">
      <div v-if="previousTracksInTime.length" class="track-list prev-list">
        <!-- Tracks are stored desc (recent first from backend), we display oldest→newest (reversed) -->
        <button v-if="prevRemaining > 0" class="expand-btn expand-btn-top" @click="showMorePrev()">
          <i class="bi bi-chevron-up"></i> Show {{ Math.min(PAGE_SIZE, prevRemaining) }} older ({{ prevRemaining }}
          remaining)
        </button>
        <button v-if="prevShowCount > PAGE_SIZE" class="expand-btn expand-btn-top" @click="showLessPrev()">
          <i class="bi bi-chevron-up"></i> Show less
        </button>

        <div v-for="track in prevTracksShown" :key="track.id" class="track-card" @click="navigateTrack(track.id)">
          <TrackShapePreview :track-id="track.id!" :width="56" :height="40" class="track-card__shape" />
          <div class="track-dot prev-dot"></div>
          <div class="track-card-body">
            <div class="track-name">{{ track.name }}</div>
            <div v-if="track.startDate" class="track-date">{{ formatDate(track.startDate) }}</div>
            <div v-if="track.description" class="track-desc">{{ track.description }}</div>
          </div>
        </div>
      </div>

      <div class="section-header prev-header">
        <i class="bi bi-arrow-up section-icon"></i>
        <span class="section-label">Previous Tracks</span>
        <span v-if="previousTracksInTime.length" class="section-count">{{ previousTracksInTime.length }}</span>
        <span v-else class="empty-inline">none</span>
      </div>
    </section>

    <!-- ── CURRENT TRACK ─────────────────────────────────── -->
    <section class="current-track-card">
      <div class="current-top-line">
        <i class="bi bi-geo-alt-fill current-star"></i>
        <span class="current-badge">Current Track</span>
      </div>
      <div v-if="gpsTrack && gpsTrack.id" class="current-body">
        <div class="current-name">{{ currentName }}</div>
        <div v-if="gpsTrack.startDate" class="current-date">{{ formatDate(gpsTrack.startDate) }}</div>
        <div v-if="currentDescription" class="current-desc">{{ currentDescription }}</div>
      </div>
    </section>

    <!-- ── NEXT TRACKS ────────────────────────────────────── -->
    <section class="timeline-section">
      <div class="section-header next-header">
        <i class="bi bi-arrow-down section-icon"></i>
        <span class="section-label">Next Tracks</span>
        <span v-if="nextTracksInTime.length" class="section-count">{{ nextTracksInTime.length }}</span>
      </div>

      <div v-if="nextTracksInTime.length" class="track-list next-list">
        <div v-for="track in nextTracksShown" :key="track.id" class="track-card" @click="navigateTrack(track.id)">
          <TrackShapePreview :track-id="track.id!" :width="56" :height="40" class="track-card__shape" />
          <div class="track-dot next-dot"></div>
          <div class="track-card-body">
            <div class="track-name">{{ track.name }}</div>
            <div v-if="track.startDate" class="track-date">{{ formatDate(track.startDate) }}</div>
            <div v-if="track.description" class="track-desc">{{ track.description }}</div>
          </div>
        </div>

        <button v-if="nextRemaining > 0" class="expand-btn" @click="showMoreNext()">
          <i class="bi bi-chevron-down"></i> Show {{ Math.min(PAGE_SIZE, nextRemaining) }} more ({{ nextRemaining }}
          remaining)
        </button>
        <button v-if="nextShowCount > PAGE_SIZE" class="expand-btn" @click="showLessNext()">
          <i class="bi bi-chevron-down"></i> Show less
        </button>
      </div>
      <div v-else class="empty-label">No next tracks</div>
    </section>

    <!-- ── DUPLICATES ─────────────────────────────────────── -->
    <section v-if="duplicates && duplicates.length" class="timeline-section duplicates-section">
      <div class="section-header dup-header">
        <i class="bi bi-files section-icon"></i>
        <span class="section-label">Duplicates</span>
        <span class="section-count">{{ duplicates.length }}</span>
      </div>
      <div class="track-list">
        <div v-for="track in duplicates" :key="track.id" class="track-card dup-card" @click="navigateTrack(track.id)">
          <TrackShapePreview :track-id="track.id!" :width="56" :height="40" class="track-card__shape" />
          <div class="track-dot dup-dot"></div>
          <div class="track-card-body">
            <div class="track-name">{{ track.name }}</div>
            <div v-if="track.startDate" class="track-date">{{ formatDate(track.startDate) }}</div>
            <div v-if="track.description" class="track-desc">{{ track.description }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── DERIVED SEGMENTS ───────────────────────────────── -->
    <section v-if="segmentSiblings && segmentSiblings.length" class="timeline-section segments-section">
      <div class="section-header seg-header">
        <i class="bi bi-scissors section-icon"></i>
        <span class="section-label">Derived Segments</span>
        <span class="section-count">{{ segmentSiblings.length }}</span>
      </div>
      <div class="track-list">
        <div
          v-for="track in segmentSiblings"
          :key="track.id"
          class="track-card seg-card"
          @click="navigateTrack(track.id)"
        >
          <TrackShapePreview :track-id="track.id!" :width="56" :height="40" class="track-card__shape" />
          <div class="track-dot seg-dot"></div>
          <div class="track-card-body">
            <div class="track-name">
              <span v-if="track.sourceSegmentIndex" class="seg-badge">Seg {{ track.sourceSegmentIndex }}</span>
              {{ track.name }}
            </div>
            <div v-if="track.startDate" class="track-date">{{ formatDate(track.startDate) }}</div>
            <div v-if="track.description" class="track-desc">{{ track.description }}</div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue';
import type { GpsTrack, RelatedTracks, RelatedTrackInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { formatDateShort } from '@/utils/Utils';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';

defineOptions({
  name: 'TrackDetailRelated',
});

const props = withDefaults(
  defineProps<{
    relatedTracks?: RelatedTracks | null;
    gpsTrack?: GpsTrack | null;
    isLoading?: boolean;
  }>(),
  {
    relatedTracks: null,
    gpsTrack: null,
    isLoading: false,
  }
);

const emit = defineEmits<{
  'navigate-track': [trackId: number];
}>();

const PAGE_SIZE = 5;

// Delayed loading indicator: only surface the progress bar if the load
// actually takes longer than the threshold below. This avoids a jarring
// flash when the response returns in tens of milliseconds (cached or
// nearby tracks) while still providing clear feedback on slower loads.
const LOADING_INDICATOR_DELAY_MS = 250;
const showLoadingIndicator = ref(false);
let loadingTimer: number | null = null;

watch(
  () => props.isLoading,
  (loading) => {
    if (loading) {
      if (loadingTimer !== null) return;
      loadingTimer = window.setTimeout(() => {
        showLoadingIndicator.value = true;
        loadingTimer = null;
      }, LOADING_INDICATOR_DELAY_MS);
    } else {
      if (loadingTimer !== null) {
        clearTimeout(loadingTimer);
        loadingTimer = null;
      }
      showLoadingIndicator.value = false;
    }
  },
  { immediate: true }
);

onBeforeUnmount(() => {
  if (loadingTimer !== null) {
    clearTimeout(loadingTimer);
    loadingTimer = null;
  }
});

const prevShowCount = ref(PAGE_SIZE);
const nextShowCount = ref(PAGE_SIZE);

const relatedTracks = computed(() => props.relatedTracks);
const gpsTrack = computed(() => props.gpsTrack);

const previousTracksInTime = computed<RelatedTrackInfo[]>(() => {
  const tracks: RelatedTrackInfo[] = props.relatedTracks?.previousTracksInTime ?? [];
  // Backend returns desc (recent first) -> reverse to display oldest at top, nearest just above current.
  return [...tracks].reverse();
});

const nextTracksInTime = computed<RelatedTrackInfo[]>(() => props.relatedTracks?.nextTracksInTime ?? []);

const duplicates = computed<RelatedTrackInfo[]>(() => props.relatedTracks?.duplicates ?? []);

const segmentSiblings = computed<RelatedTrackInfo[]>(() => props.relatedTracks?.segmentSiblings ?? []);

// Show the N entries closest to current (tail of prev, head of next).
const prevTracksShown = computed<RelatedTrackInfo[]>(() => previousTracksInTime.value.slice(-prevShowCount.value));

const nextTracksShown = computed<RelatedTrackInfo[]>(() => nextTracksInTime.value.slice(0, nextShowCount.value));

const prevRemaining = computed(() => Math.max(0, previousTracksInTime.value.length - prevShowCount.value));
const nextRemaining = computed(() => Math.max(0, nextTracksInTime.value.length - nextShowCount.value));

const currentName = computed<string>(() => {
  const track = props.gpsTrack;
  if (!track) return '';
  if (track.trackName?.trim()) return track.trackName.trim();
  if (track.metaName?.trim()) return track.metaName.trim();
  return track.id ? 'Track #' + track.id : '';
});

const currentDescription = computed<string | null>(() => {
  const track = props.gpsTrack;
  if (!track) return null;
  if (track.trackDescription?.trim()) return track.trackDescription.trim();
  if (track.metaDescription?.trim()) return track.metaDescription.trim();
  return null;
});

function showMorePrev() {
  prevShowCount.value = Math.min(prevShowCount.value + PAGE_SIZE, previousTracksInTime.value.length);
}

function showLessPrev() {
  prevShowCount.value = PAGE_SIZE;
}

function showMoreNext() {
  nextShowCount.value = Math.min(nextShowCount.value + PAGE_SIZE, nextTracksInTime.value.length);
}

function showLessNext() {
  nextShowCount.value = PAGE_SIZE;
}

function navigateTrack(trackId: number | null | undefined) {
  if (trackId != null) {
    emit('navigate-track', trackId);
  }
}

function formatDate(dateVal: string | number | Date): string {
  return formatDateShort(dateVal);
}
</script>

<style scoped>
.related-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 0.5rem 0.25rem 1.25rem;
  gap: 0;
  position: relative;
}

/* ── Section Chrome ────────────────────────────────────── */
.timeline-section {
  display: flex;
  flex-direction: column;
  width: 100%;
}

.section-header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.75rem 1rem 0.35rem;
  font-size: var(--text-2xs-size);
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-faint);
}

.section-icon {
  font-size: var(--text-xs-size);
  opacity: 0.7;
}

.section-count {
  margin-left: auto;
  background: var(--surface-elevated);
  border: 1px solid var(--border-subtle);
  border-radius: 999px;
  padding: 0.05rem 0.45rem;
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.prev-header {
  color: var(--text-faint);
}
.next-header {
  color: var(--text-faint);
}
.dup-header {
  color: var(--text-faint);
}

/* ── Track List ────────────────────────────────────────── */
.track-list {
  display: flex;
  flex-direction: column;
  padding-left: 0.875rem;
  border-left: 2px solid var(--border-subtle);
  margin: 0 0.75rem 0.25rem 1.5rem;
  gap: 0.125rem;
}

.prev-list {
  border-color: var(--border-subtle);
}
.next-list {
  border-color: var(--border-subtle);
}

/* ── Track Card ────────────────────────────────────────── */
.track-card {
  display: flex;
  align-items: flex-start;
  gap: 0.625rem;
  padding: 0.5rem 0.625rem;
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 8px;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    transform 0.12s ease;
  position: relative;
}

.track-card:hover {
  background: var(--surface-hover);
  border-color: var(--border-default);
  transform: translateX(2px);
}

.track-card:active {
  transform: translateX(2px) scale(0.995);
}

.track-card__shape {
  flex-shrink: 0;
  border-radius: 6px;
  opacity: 0.7;
  transition: opacity 0.15s ease;
}

.track-card:hover .track-card__shape {
  opacity: 1;
}

/* ── Dot ───────────────────────────────────────────────── */
.track-dot {
  flex-shrink: 0;
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  margin-top: 0.4rem;
  margin-left: -1.3125rem;
  border: 2px solid var(--accent-text);
  background: var(--surface-glass-heavy);
  z-index: 1;
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.track-card:hover .track-dot {
  transform: scale(1.25);
  box-shadow: 0 0 0 3px var(--accent-bg);
}

.prev-dot {
  border-color: var(--accent-text);
}
.next-dot {
  border-color: var(--accent-text);
}
.dup-dot {
  border-color: var(--text-muted);
}

/* ── Card Body ─────────────────────────────────────────── */
.track-card-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.track-name {
  font-size: var(--text-sm-size);
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  transition: color 0.15s ease;
}

.track-card:hover .track-name {
  color: var(--accent-text);
}

.track-date {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  margin-top: 0.125rem;
  font-variant-numeric: tabular-nums;
}

.track-desc {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  margin-top: 0.1875rem;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: var(--text-xs-lh);
}

.empty-label {
  font-size: var(--text-sm-size);
  color: var(--text-muted);
  font-style: italic;
  padding: 0.375rem 1.5rem 0.625rem;
}

/* ── Expand Button ─────────────────────────────────────── */
.expand-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  align-self: flex-start;
  background: none;
  border: none;
  color: var(--accent-text);
  font-size: var(--text-xs-size);
  font-weight: 600;
  cursor: pointer;
  padding: 0.375rem 0.4rem;
  border-radius: 6px;
  opacity: 0.85;
  transition:
    opacity 0.15s ease,
    background 0.15s ease;
}
.expand-btn:hover {
  opacity: 1;
  background: var(--accent-bg);
}

.expand-btn-top {
  margin-bottom: 0.25rem;
}

.empty-inline {
  font-size: var(--text-2xs-size);
  color: var(--text-faint);
  font-style: italic;
  margin-left: 0.125rem;
  text-transform: none;
  letter-spacing: 0;
}

/* ── Current Track Card ────────────────────────────────── */
.current-track-card {
  margin: 0.5rem 0.75rem;
  padding: 0.75rem 0.875rem;
  border-radius: 8px;
  border: 1px solid var(--accent-text);
  background: var(--accent-bg);
  position: relative;
}

.current-top-line {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.375rem;
}

.current-star {
  font-size: var(--text-sm-size);
  color: var(--accent-text);
}

.current-badge {
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--accent-text);
  opacity: 0.85;
}

.current-name {
  font-size: var(--text-base-size);
  font-weight: 600;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.current-date {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  margin-top: 2px;
}

.current-desc {
  font-size: var(--text-xs-size);
  color: var(--text-muted);
  margin-top: 5px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: var(--text-xs-lh);
}

/* ── Duplicates special spacing ────────────────────────── */
.duplicates-section {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

.dup-card .track-name {
  color: var(--text-muted);
}

/* ── Derived Segments ──────────────────────────────────── */
.segments-section {
  margin-top: 1.125rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border-subtle);
}

.seg-dot {
  border-color: var(--accent-text-light) !important;
}

.seg-badge {
  display: inline-block;
  background: var(--accent-text-light);
  color: var(--text-inverse);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  border-radius: 4px;
  padding: 0.0625rem 0.3125rem;
  margin-right: 0.3125rem;
  vertical-align: middle;
}

/* ── Loading indicator ──────────────────────────────────
 * Thin indeterminate progress bar at the top of the panel + transparent
 * click-blocker. The progress bar is delayed by ~250ms (see setup()) so
 * fast loads never flash it; the click-blocker is active immediately to
 * prevent double-tap navigation stacking without any visual flicker. */
.loading-bar {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  overflow: hidden;
  background: var(--border-subtle);
  z-index: 11;
  pointer-events: none;
  border-radius: 2px;
}

.loading-bar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 35%;
  background: linear-gradient(90deg, transparent, var(--accent-text), transparent);
  animation: loading-bar-slide 1.1s ease-in-out infinite;
}

@keyframes loading-bar-slide {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(385%);
  }
}

.loading-click-blocker {
  position: absolute;
  inset: 0;
  z-index: 10;
  background: transparent;
  cursor: wait;
}

/* Soft fade of content while the indicator is visible — reinforces the
 * "busy" state without the harshness of a modal overlay. */
.related-container--loading .timeline-section,
.related-container--loading .current-track-card,
.related-container--loading .duplicates-section,
.related-container--loading .segments-section {
  opacity: 0.55;
  transition: opacity 0.18s ease-in;
}
</style>
