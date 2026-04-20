<template>
  <div class="related-container" :class="{ 'related-container--loading': showLoadingIndicator }" v-if="relatedTracks">

    <!-- Thin indeterminate progress bar (top) — only appears if load takes >250ms -->
    <div v-if="showLoadingIndicator" class="loading-bar" aria-busy="true" aria-label="Loading track"></div>
    <!-- Transparent click blocker — always on during isLoading, even before bar appears.
         This prevents double-taps from stacking navigations without any visual flicker. -->
    <div v-if="isLoading" class="loading-click-blocker" aria-hidden="true"></div>

    <!-- ── PREVIOUS TRACKS ───────────────────────────────── -->
    <section class="timeline-section">
      <div v-if="previousTracksInTime.length" class="track-list prev-list">
        <!-- Tracks are stored desc (recent first from backend), we display oldest→newest (reversed) -->
        <button
          v-if="prevRemaining > 0"
          class="expand-btn expand-btn-top"
          @click="showMorePrev()"
        >
          ▲ Show {{ Math.min(PAGE_SIZE, prevRemaining) }} older ({{ prevRemaining }} remaining)
        </button>
        <button
          v-if="prevShowCount > PAGE_SIZE"
          class="expand-btn expand-btn-top"
          @click="showLessPrev()"
        >
          ▲ Show less
        </button>

        <div
          v-for="track in prevTracksShown"
          :key="track.id"
          class="track-card"
          @click="$emit('navigate-track', track.id)"
        >
          <TrackShapePreview :trackId="track.id!" :width="56" :height="40" class="track-card__shape" />
          <div class="track-dot prev-dot"></div>
          <div class="track-card-body">
            <div class="track-name">{{ track.name }}</div>
            <div class="track-date" v-if="track.startDate">{{ formatDate(track.startDate) }}</div>
            <div class="track-desc" v-if="track.description">{{ track.description }}</div>
          </div>
        </div>
      </div>

      <div class="section-header prev-header">
        <span class="section-icon">↑</span>
        <span class="section-label">Previous Tracks</span>
        <span class="section-count" v-if="previousTracksInTime.length">{{ previousTracksInTime.length }}</span>
        <span class="empty-inline" v-else>— none</span>
      </div>
    </section>

    <!-- ── CURRENT TRACK ─────────────────────────────────── -->
    <section class="current-track-card">
      <div class="current-top-line">
        <span class="current-star">★</span>
        <span class="current-badge">Current Track</span>
      </div>
      <div class="current-body" v-if="gpsTrack && gpsTrack.id">
        <div class="current-name">{{ currentName }}</div>
        <div class="current-date" v-if="gpsTrack.startDate">{{ formatDate(gpsTrack.startDate) }}</div>
        <div class="current-desc" v-if="currentDescription">{{ currentDescription }}</div>
      </div>
    </section>

    <!-- ── NEXT TRACKS ────────────────────────────────────── -->
    <section class="timeline-section">
      <div class="section-header next-header">
        <span class="section-icon">↓</span>
        <span class="section-label">Next Tracks</span>
        <span class="section-count" v-if="nextTracksInTime.length">{{ nextTracksInTime.length }}</span>
      </div>

      <div v-if="nextTracksInTime.length" class="track-list next-list">
        <div
          v-for="track in nextTracksShown"
          :key="track.id"
          class="track-card"
          @click="$emit('navigate-track', track.id)"
        >
          <TrackShapePreview :trackId="track.id!" :width="56" :height="40" class="track-card__shape" />
          <div class="track-dot next-dot"></div>
          <div class="track-card-body">
            <div class="track-name">{{ track.name }}</div>
            <div class="track-date" v-if="track.startDate">{{ formatDate(track.startDate) }}</div>
            <div class="track-desc" v-if="track.description">{{ track.description }}</div>
          </div>
        </div>

        <button
          v-if="nextRemaining > 0"
          class="expand-btn"
          @click="showMoreNext()"
        >
          ▼ Show {{ Math.min(PAGE_SIZE, nextRemaining) }} more ({{ nextRemaining }} remaining)
        </button>
        <button
          v-if="nextShowCount > PAGE_SIZE"
          class="expand-btn"
          @click="showLessNext()"
        >
          ▼ Show less
        </button>
      </div>
      <div v-else class="empty-label">No next tracks</div>
    </section>

    <!-- ── DUPLICATES ─────────────────────────────────────── -->
    <section class="timeline-section duplicates-section" v-if="duplicates && duplicates.length">
      <div class="section-header dup-header">
        <span class="section-icon">⊛</span>
        <span class="section-label">Duplicates</span>
        <span class="section-count">{{ duplicates.length }}</span>
      </div>
      <div class="track-list">
        <div
          v-for="track in duplicates"
          :key="track.id"
          class="track-card dup-card"
          @click="$emit('navigate-track', track.id)"
        >
          <TrackShapePreview :trackId="track.id!" :width="56" :height="40" class="track-card__shape" />
          <div class="track-dot dup-dot"></div>
          <div class="track-card-body">
            <div class="track-name">{{ track.name }}</div>
            <div class="track-date" v-if="track.startDate">{{ formatDate(track.startDate) }}</div>
            <div class="track-desc" v-if="track.description">{{ track.description }}</div>
          </div>
        </div>
      </div>
    </section>

    <!-- ── DERIVED SEGMENTS ───────────────────────────────── -->
    <section class="timeline-section segments-section" v-if="segmentSiblings && segmentSiblings.length">
      <div class="section-header seg-header">
        <span class="section-icon">◧</span>
        <span class="section-label">Derived Segments</span>
        <span class="section-count">{{ segmentSiblings.length }}</span>
      </div>
      <div class="track-list">
        <div
          v-for="track in segmentSiblings"
          :key="track.id"
          class="track-card seg-card"
          @click="$emit('navigate-track', track.id)"
        >
          <TrackShapePreview :trackId="track.id!" :width="56" :height="40" class="track-card__shape" />
          <div class="track-dot seg-dot"></div>
          <div class="track-card-body">
            <div class="track-name">
              <span v-if="track.sourceSegmentIndex" class="seg-badge">Seg {{ track.sourceSegmentIndex }}</span>
              {{ track.name }}
            </div>
            <div class="track-date" v-if="track.startDate">{{ formatDate(track.startDate) }}</div>
            <div class="track-desc" v-if="track.description">{{ track.description }}</div>
          </div>
        </div>
      </div>
    </section>

  </div>
</template>

<script lang="ts">
import { defineComponent, computed, ref, watch, onBeforeUnmount } from "vue";
import type { RelatedTracks, RelatedTrackInfo } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import { formatDateShort } from '@/utils/Utils';
import TrackShapePreview from '@/components/ui/TrackShapePreview.vue';

export default defineComponent({
  name: 'TrackDetailRelated',
  components: { TrackShapePreview },
  props: {
    relatedTracks: { type: Object, default: null },
    gpsTrack: { type: Object, default: null },
    isLoading: { type: Boolean, default: false },
  },
  emits: ['navigate-track'],
  setup(props) {
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
      { immediate: true },
    );

    onBeforeUnmount(() => {
      if (loadingTimer !== null) {
        clearTimeout(loadingTimer);
        loadingTimer = null;
      }
    });
    const prevShowCount = ref(PAGE_SIZE);
    const nextShowCount = ref(PAGE_SIZE);

    const previousTracksInTime = computed<RelatedTrackInfo[]>(() => {
      const tracks: RelatedTrackInfo[] = (props.relatedTracks as RelatedTracks)?.previousTracksInTime ?? [];
      // Backend returns desc (recent first) → reverse to display oldest at top, nearest just above current
      return [...tracks].reverse();
    });

    const nextTracksInTime = computed<RelatedTrackInfo[]>(() =>
      (props.relatedTracks as RelatedTracks)?.nextTracksInTime ?? []
    );

    const duplicates = computed<RelatedTrackInfo[]>(() =>
      (props.relatedTracks as RelatedTracks)?.duplicates ?? []
    );

    const segmentSiblings = computed<RelatedTrackInfo[]>(() =>
      (props.relatedTracks as RelatedTracks)?.segmentSiblings ?? []
    );

    // Show the N entries closest to current (tail of prev, head of next)
    const prevTracksShown = computed<RelatedTrackInfo[]>(() =>
      previousTracksInTime.value.slice(-prevShowCount.value)
    );

    const nextTracksShown = computed<RelatedTrackInfo[]>(() =>
      nextTracksInTime.value.slice(0, nextShowCount.value)
    );

    const prevRemaining = computed(() =>
      Math.max(0, previousTracksInTime.value.length - prevShowCount.value)
    );
    const nextRemaining = computed(() =>
      Math.max(0, nextTracksInTime.value.length - nextShowCount.value)
    );

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

    const currentName = computed<string>(() => {
      const t = props.gpsTrack;
      if (!t) return '';
      if (t.trackName?.trim()) return t.trackName.trim();
      if (t.metaName?.trim()) return t.metaName.trim();
      return t.id ? 'Track #' + t.id : '';
    });

    const currentDescription = computed<string | null>(() => {
      const t = props.gpsTrack;
      if (!t) return null;
      if (t.trackDescription?.trim()) return t.trackDescription.trim();
      if (t.metaDescription?.trim()) return t.metaDescription.trim();
      return null;
    });

    function formatDate(dateVal: string | number | Date): string {
      return formatDateShort(dateVal);
    }

    return {
      PAGE_SIZE,
      prevShowCount,
      nextShowCount,
      previousTracksInTime,
      nextTracksInTime,
      duplicates,
      segmentSiblings,
      prevTracksShown,
      nextTracksShown,
      prevRemaining,
      nextRemaining,
      showMorePrev,
      showLessPrev,
      showMoreNext,
      showLessNext,
      currentName,
      currentDescription,
      formatDate,
      showLoadingIndicator,
    };
  },
});
</script>

<style scoped>
.related-container {
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 12px 4px 24px;
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
  gap: 6px;
  padding: 8px 10px 6px;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.section-icon {
  font-size: 0.9rem;
  opacity: 0.7;
}

.section-count {
  margin-left: auto;
  background: var(--surface-glass, rgba(255,255,255,0.1));
  border-radius: 10px;
  padding: 1px 7px;
  font-size: 0.68rem;
  color: var(--text-muted);
}

.prev-header  { color: var(--text-muted); }
.next-header  { color: var(--text-muted); }
.dup-header   { color: var(--text-muted); }

/* ── Track List ────────────────────────────────────────── */
.track-list {
  display: flex;
  flex-direction: column;
  padding-left: 14px;
  border-left: 2px solid var(--border-subtle, rgba(255,255,255,0.12));
  margin: 0 10px 4px 20px;
  gap: 0;
}

.prev-list { border-color: var(--border-subtle, rgba(255,255,255,0.12)); }
.next-list { border-color: var(--border-subtle, rgba(255,255,255,0.12)); }

/* ── Track Card ────────────────────────────────────────── */
.track-card {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px 8px 0;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.15s;
  position: relative;
}

.track-card:hover {
  background: var(--surface-glass, rgba(255,255,255,0.06));
}

.track-card__shape {
  flex-shrink: 0;
  opacity: 0.7;
}

.track-card:hover .track-card__shape {
  opacity: 1;
}

/* ── Dot ───────────────────────────────────────────────── */
.track-dot {
  flex-shrink: 0;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  margin-top: 5px;
  margin-left: -19px;
  border: 2px solid var(--accent-text, #6ea6ff);
  background: var(--bg-primary, #1a1a2e);
  z-index: 1;
}

.prev-dot { border-color: var(--accent-text, #6ea6ff); }
.next-dot { border-color: var(--accent-text, #6ea6ff); }
.dup-dot  { border-color: var(--text-muted, #888); }

/* ── Card Body ─────────────────────────────────────────── */
.track-card-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.track-name {
  font-size: 0.88rem;
  font-weight: 500;
  color: var(--accent-text, #6ea6ff);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.track-date {
  font-size: 0.72rem;
  color: var(--text-muted);
  margin-top: 1px;
}

.track-desc {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-top: 3px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.empty-label {
  font-size: 0.8rem;
  color: var(--text-muted);
  font-style: italic;
  padding: 6px 20px 10px;
}

/* ── Expand Button ─────────────────────────────────────── */
.expand-btn {
  align-self: flex-start;
  background: none;
  border: none;
  color: var(--accent-text, #6ea6ff);
  font-size: 0.75rem;
  cursor: pointer;
  padding: 4px 0 6px;
  opacity: 0.75;
  transition: opacity 0.15s;
}
.expand-btn:hover { opacity: 1; }

.expand-btn-top {
  margin-bottom: 4px;
}

.empty-inline {
  font-size: 0.75rem;
  color: var(--text-muted);
  font-style: italic;
  margin-left: 2px;
}

/* ── Current Track Card ────────────────────────────────── */
.current-track-card {
  margin: 8px 10px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1.5px solid var(--accent-text, #6ea6ff);
  background: var(--surface-glass, rgba(110,166,255,0.07));
  position: relative;
}

.current-top-line {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.current-star {
  font-size: 1rem;
  color: var(--accent-text, #6ea6ff);
}

.current-badge {
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--accent-text, #6ea6ff);
  opacity: 0.85;
}

.current-name {
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary, #fff);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.current-date {
  font-size: 0.74rem;
  color: var(--text-muted);
  margin-top: 2px;
}

.current-desc {
  font-size: 0.78rem;
  color: var(--text-muted);
  margin-top: 5px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.45;
}

/* ── Duplicates special spacing ────────────────────────── */
.duplicates-section {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle, rgba(255,255,255,0.08));
}

.dup-card .track-name {
  color: var(--text-muted);
}

/* ── Derived Segments ──────────────────────────────────── */
.segments-section {
  margin-top: 18px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle, rgba(255,255,255,0.08));
}

.seg-dot {
  border-color: var(--accent-secondary, #a78bfa) !important;
}

.seg-badge {
  display: inline-block;
  background: var(--accent-secondary, #a78bfa);
  color: var(--bg-primary, #1a1a2e);
  font-size: 0.62rem;
  font-weight: 700;
  border-radius: 4px;
  padding: 1px 5px;
  margin-right: 5px;
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
  background: var(--border-subtle, rgba(255, 255, 255, 0.08));
  z-index: 11;
  pointer-events: none;
  border-radius: 2px;
}

.loading-bar::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  width: 35%;
  background: linear-gradient(
    90deg,
    transparent,
    var(--accent-text, #6ea6ff),
    transparent
  );
  animation: loading-bar-slide 1.1s ease-in-out infinite;
}

@keyframes loading-bar-slide {
  0%   { transform: translateX(-100%); }
  100% { transform: translateX(385%); }
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

