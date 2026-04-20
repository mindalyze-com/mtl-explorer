<template>
  <div>
    <BottomSheet v-model="active" title="Animate" icon="bi bi-play-circle" :detents="sheetDetents" initial-detent="open" noBackdrop @closed="onSheetClosed">
      <div v-if="active" class="am-root">
        <section class="am-overview">
          <div class="am-overview-top">
            <div class="am-hero-controls">
              <button class="am-play-hero"
                      @click="onPlayPauseToggle"
                      :disabled="!hasFeatures"
                      :aria-label="animationInProgress ? 'Pause animation' : 'Play animation'">
                <i :class="animationInProgress ? 'bi bi-pause-fill' : 'bi bi-play-fill'"></i>
              </button>
              <button class="am-stop-btn"
                      @click="onStopAnimation"
                      :disabled="!animationInProgress && animationIndex === rangeValue[0]"
                      aria-label="Stop animation">
                <i class="bi bi-stop-fill"></i>
              </button>
            </div>

            <section class="am-section am-section--inline-speed">
              <div class="am-section-head am-timeline-head">
                <div class="am-tracks-summary">
                  <span class="am-section-label">Speed</span>
                </div>
              </div>
              <div class="am-timeline">
                <div class="am-timeline-slider-wrap">
                  <Slider v-model="speedSliderPos" :min="0" :max="100" :step="1" class="am-timeline-slider" />
                </div>
                <div class="am-timeline-labels">
                  <span class="am-date"></span>
                  <span class="am-date-current">{{ animationSpeed }}ms</span>
                  <span class="am-date am-date--end"></span>
                </div>
              </div>
            </section>
          </div>

          <section class="am-section am-section--timeline">
            <div class="am-section-head am-timeline-head">
              <div class="am-tracks-summary">
                <span class="am-section-label">Tracks</span>
                <span class="am-tracks-separator"> </span>
                <span class="am-tracks-value">{{ visibleCount }} / {{ rangeTrackCount }}</span>
              </div>
            </div>
            <div class="am-timeline">
              <div class="am-timeline-slider-wrap">
                <Slider v-model="rangeValue"
                        :range="true"
                        :min="0"
                        :max="Math.max(totalCount - 1, 0)"
                        :disabled="!sortedFeatures.length"
                        class="am-timeline-slider"
                        @change="onRangeChange" />
                <div v-if="showPlayhead"
                     class="am-playhead"
                     :style="{ left: playheadPercent + '%' }"
                     aria-hidden="true"></div>
              </div>
              <div class="am-timeline-labels">
                <span class="am-date">{{ rangeDateStart }}</span>
                <span class="am-date-current">{{ currentDateLabel }}</span>
                <span class="am-date am-date--end">{{ rangeDateEnd }}</span>
              </div>
            </div>
          </section>

          <section class="am-section am-section--speed">
            <div class="am-section-head">
              <span class="am-section-title">Playback Speed</span>
            </div>
            <Slider v-model="speedSliderPos" :min="0" :max="100" :step="1" class="am-speed-slider" />
            <div class="am-speed-labels">
              <span class="am-speed-edge"><i class="bi bi-hourglass"></i> Slow</span>
              <span class="am-speed-ms">{{ animationSpeed }}ms</span>
              <span class="am-speed-edge am-speed-edge--end">Fast <i class="bi bi-lightning-charge-fill"></i></span>
            </div>
          </section>
        </section>
      </div>
    </BottomSheet>
  </div>
</template>

<script>
import { defineComponent, inject, markRaw } from "vue";
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { formatDate } from '@/utils/Utils';
import { TRACK_COLOR } from '@/utils/trackColors';

const DESKTOP_BP = 769;
const ANIMATE_MAX_VH = 60;
const ANIMATE_DESKTOP_OPEN_HEIGHT = 320;
const ANIMATE_MOBILE_OPEN_HEIGHT = 320;

const EVENTS = {
  animate: "animate",
  animationFinished: "animationFinished",
  animationStart: "animationStart",
  animationStop: "animationStop",
  toolClosed: "toolClosed"
};

export default defineComponent({
  name: 'AnimateMap',
  components: { BottomSheet },
  props: ['map'],
  emits: [EVENTS.animate, EVENTS.animationFinished, EVENTS.animationStart, EVENTS.animationStop, 'tool-opened', 'tool-closed'],
  data() {
    return {
      active: false,

      // Animation state
      animationInProgress: false,
      animationSpeed: 20,
      timerId: null,
      sortedFeatures: [],
      animationIndex: 0,
      color1: [255, 0, 0],
      color2: [0, 0, 255],

      // Combined range + timeline (dual-handle). Playback stays within [lo, hi].
      rangeValue: [0, 0],
      viewportWidth: typeof window !== 'undefined' ? window.innerWidth : DESKTOP_BP,
      viewportHeight: typeof window !== 'undefined' ? window.innerHeight : 900,
    };
  },
  setup() {
    return {
      toast: inject("toast"),
    };
  },
  computed: {
    isMobileViewport() {
      return this.viewportWidth < DESKTOP_BP;
    },
    sheetOpenHeight() {
      return this.isMobileViewport ? ANIMATE_MOBILE_OPEN_HEIGHT : ANIMATE_DESKTOP_OPEN_HEIGHT;
    },
    sheetDetents() {
      const openPx = this.sheetOpenHeight;
      const collapsedPx = Math.max(120, Math.min(openPx - 60, Math.round(openPx * 0.72)));
      return [
        { id: 'collapsed', height: `${collapsedPx}px` },
        { id: 'open', height: `${openPx}px` },
        { id: 'max', height: `${ANIMATE_MAX_VH}vh` },
      ];
    },
    hasFeatures() {
      return this.sortedFeatures.length > 0;
    },
    totalCount() {
      return this.sortedFeatures.length;
    },
    rangeTrackCount() {
      if (!this.sortedFeatures.length) return 0;
      return this.rangeValue[1] - this.rangeValue[0] + 1;
    },
    visibleCount() {
      if (!this.sortedFeatures.length) return 0;
      if (this.animationInProgress) {
        return Math.max(0, this.animationIndex - this.rangeValue[0] + 1);
      }
      return this.rangeTrackCount;
    },
    showPlayhead() {
      return this.hasFeatures && (this.animationInProgress || this.animationIndex > this.rangeValue[0]);
    },
    playheadPercent() {
      if (this.sortedFeatures.length <= 1) return 0;
      const max = this.sortedFeatures.length - 1;
      return Math.min(100, Math.max(0, (this.animationIndex / max) * 100));
    },
    playStateLabel() {
      if (!this.sortedFeatures.length) return 'No tracks';
      if (this.animationInProgress) return 'Running';
      if (this.animationIndex > this.rangeValue[0]) return 'Paused';
      return 'Ready';
    },
    currentDateLabel() {
      if (!this.sortedFeatures.length) return '—';
      const idx = this.animationInProgress || this.animationIndex > this.rangeValue[0]
        ? this.animationIndex
        : this.rangeValue[0];
      const f = this.sortedFeatures[idx];
      const d = f?.properties?.startDate;
      return d ? formatDate(new Date(d)) : '—';
    },
    rangeDateStart() {
      if (!this.sortedFeatures.length) return '—';
      const f = this.sortedFeatures[this.rangeValue[0]];
      const d = f?.properties?.startDate;
      return d ? formatDate(new Date(d)) : '—';
    },
    rangeDateEnd() {
      if (!this.sortedFeatures.length) return '—';
      const f = this.sortedFeatures[this.rangeValue[1]];
      const d = f?.properties?.startDate;
      return d ? formatDate(new Date(d)) : '—';
    },
    // Logarithmic speed slider: maps linear 0–100 position ↔ 1–1000 ms
    speedSliderPos: {
      get() {
        const minMs = 1, maxMs = 1000;
        const pos = Math.round(
          (Math.log(this.animationSpeed) - Math.log(minMs)) /
          (Math.log(maxMs) - Math.log(minMs)) * 100
        );
        return Math.min(100, Math.max(0, pos));
      },
      set(pos) {
        const minMs = 1, maxMs = 1000;
        const ms = Math.round(
          Math.exp(Math.log(minMs) + (pos / 100) * (Math.log(maxMs) - Math.log(minMs)))
        );
        this.animationSpeed = Math.min(1000, Math.max(1, ms));
      },
    },
  },
  watch: {
    active(val) {
      if (val) this.prepareSortedFeatures();
    },
    animationSpeed() {
      // Restart the interval with new speed while animation is running
      if (this.timerId) {
        clearInterval(this.timerId);
        this.timerId = setInterval(this.animationFunction, this.animationSpeed);
      }
    },
  },
  mounted() {
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', this.onViewportResize);
    }
  },
  methods: {
    onViewportResize() {
      this.viewportWidth = window.innerWidth;
      this.viewportHeight = window.innerHeight;
    },

    async toggle() {
      this.active = !this.active;
      if (this.active) {
        this.$emit('tool-opened');
        this.prepareSortedFeatures();
      } else {
        this.onClose();
      }
    },

    close() {
      this.onClose();
    },

    onSheetClosed() {
      this.onStopAnimation();
      this.restoreTracksLayer();
      this.active = false;
      this.$emit('tool-closed');
    },

    onClose() {
      this.onStopAnimation();
      this.restoreTracksLayer();
      this.active = false;
    },

    prepareSortedFeatures() {
      const geojson = this.$parent?.geojson;
      if (!geojson?.features?.length) {
        this.sortedFeatures = [];
        this.rangeValue = [0, 0];
        return;
      }
      this.sortedFeatures = markRaw([...geojson.features].sort((a, b) => {
        const aDate = a.properties?.startDate ?? Infinity;
        const bDate = b.properties?.startDate ?? Infinity;
        return aDate - bDate;
      }));
      this.rangeValue = [0, this.sortedFeatures.length - 1];
      this.animationIndex = 0;
    },

    // ─── Shared layer helpers ──────────────────────────────────────

    ensureAnimationLayer() {
      if (!this.map) return;
      if (this.map.getLayer('tracks-layer')) {
        this.map.setPaintProperty('tracks-layer', 'line-opacity', 0);
      }
      if (this.map.getLayer('animation-layer')) this.map.removeLayer('animation-layer');
      if (this.map.getSource('animation-source')) this.map.removeSource('animation-source');

      this.map.addSource('animation-source', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] },
      });
      this.map.addLayer({
        id: 'animation-layer',
        type: 'line',
        source: 'animation-source',
        layout: { 'line-join': 'round', 'line-cap': 'round' },
        paint: {
          'line-color': ['coalesce', ['get', '_animColor'], TRACK_COLOR],
          'line-width': 4,
          'line-opacity': 1,
        },
      });
    },

    removeAnimationLayer() {
      if (!this.map) return;
      if (this.map.getLayer('animation-layer')) this.map.removeLayer('animation-layer');
      if (this.map.getSource('animation-source')) this.map.removeSource('animation-source');
    },

    restoreTracksLayer() {
      this.removeAnimationLayer();
      if (this.map?.getLayer('tracks-layer')) {
        this.map.setPaintProperty('tracks-layer', 'line-opacity', 1);
      }
    },

    setAnimationSourceData(features) {
      const source = this.map?.getSource('animation-source');
      if (source) {
        source.setData({ type: 'FeatureCollection', features });
      }
    },

    // ─── PLAY ──────────────────────────────────────────────────────

    onPlayPauseToggle() {
      if (this.animationInProgress) {
        this.onPauseAnimation();
      } else {
        this.onStartAnimation();
      }
    },

    async onStartAnimation() {
      if (!this.map || !this.sortedFeatures.length) return;

      if (this.animationInProgress) {
        // Resume from current position
        this.timerId = setInterval(this.animationFunction, this.animationSpeed);
        return;
      }

      // If paused inside range, resume from current index; otherwise start at range start.
      if (this.animationIndex < this.rangeValue[0] || this.animationIndex >= this.rangeValue[1]) {
        this.animationIndex = this.rangeValue[0];
      }
      this.ensureAnimationLayer();
      this.animationInProgress = true;
      this.renderFrame();
      this.timerId = setInterval(this.animationFunction, this.animationSpeed);
      this.$emit(EVENTS.animationStart, "animation has started");
    },

    onPauseAnimation() {
      if (this.timerId) {
        clearInterval(this.timerId);
        this.timerId = null;
      }
      this.animationInProgress = false;
    },

    async onStopAnimation() {
      if (this.timerId) {
        clearInterval(this.timerId);
        this.timerId = null;
      }
      this.animationInProgress = false;
      this.animationIndex = this.rangeValue[0] || 0;
      // Re-render static range view after stopping
      if (this.sortedFeatures.length) {
        this.ensureAnimationLayer();
        this.renderRangeFrame();
      }
      this.$emit(EVENTS.animationStop, "animation stopped");
    },

    animationFunction() {
      this.animationIndex++;
      if (this.animationIndex >= this.rangeValue[1]) {
        this.animationIndex = this.rangeValue[1];
        this.renderFrame();
        this.$emit(EVENTS.animationFinished, "animation has finished");
        this.onPauseAnimation();
        return;
      }
      this.renderFrame();
    },

    renderFrame() {
      const lookBack = 50;
      const visibleFeatures = [];
      const start = this.rangeValue[0];

      for (let i = Math.max(start, this.animationIndex - lookBack); i <= this.animationIndex; i++) {
        const feature = this.sortedFeatures[i];
        const relativeStep = this.animationIndex - i;
        const interpolatedColor = this.interpolateColor(this.color1, this.color2, lookBack, relativeStep);
        const rgbString = `rgb(${interpolatedColor[0]},${interpolatedColor[1]},${interpolatedColor[2]})`;
        visibleFeatures.push({
          ...feature,
          properties: { ...feature.properties, _animColor: rgbString },
        });
      }

      for (let i = start; i < Math.max(start, this.animationIndex - lookBack); i++) {
        visibleFeatures.push({
          ...this.sortedFeatures[i],
          properties: {
            ...this.sortedFeatures[i].properties,
            _animColor: `rgb(${this.color2[0]},${this.color2[1]},${this.color2[2]})`,
          },
        });
      }

      this.setAnimationSourceData(visibleFeatures);

      const currentFeature = this.sortedFeatures[this.animationIndex];
      this.$emit(EVENTS.animate, {
        animateIndexCurrent: this.animationIndex,
        animateIndexMax: this.sortedFeatures.length,
        currentDate: new Date(currentFeature.properties?.startDate),
      });
    },

    renderRangeFrame() {
      const [lo, hi] = this.rangeValue;
      const features = this.sortedFeatures.slice(lo, hi + 1);
      this.setAnimationSourceData(features);
    },

    // ─── RANGE ─────────────────────────────────────────────────────

    onRangeChange() {
      if (!this.sortedFeatures.length) return;
      // Editing the range while animating interrupts playback.
      if (this.animationInProgress) {
        this.onPauseAnimation();
      }
      // Snap scrub index back into the selected range.
      if (this.animationIndex < this.rangeValue[0] || this.animationIndex > this.rangeValue[1]) {
        this.animationIndex = this.rangeValue[0];
      }
      this.ensureAnimationLayer();
      this.renderRangeFrame();
      this.$emit(EVENTS.animationStart, "range filter active");
    },

    // ─── Shared ────────────────────────────────────────────────────

    interpolateColor(color1, color2, steps, currentStep) {
      const color = [];
      for (let i = 0; i < color1.length; i++) {
        const distance = color2[i] - color1[i];
        color[i] = Math.round(color1[i] + (distance / steps) * currentStep);
      }
      return color;
    },
  },

  beforeUnmount() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', this.onViewportResize);
    }
    this.onStopAnimation();
  },
});
</script>

<style scoped>
.am-root {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-y: contain;
  padding: 0.1rem 1rem 1rem;
  color: var(--text-secondary);
}

.am-overview {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-height: 0;
}

.am-overview-top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-height: 2.6rem;
  gap: 0.75rem;
}

.am-tracks-summary {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  gap: 0.35em;
  min-width: 0;
}

/* ── Type scale ─────────────────────────────────────────────────
   xs-caps : 0.65rem / weight 600 / uppercase / text-muted      (labels)
   sm      : 0.75rem / weight 400 / text-secondary              (dates, edges)
   md-val  : 0.9rem  / weight 600 / text-primary or accent-text (key numbers)
─────────────────────────────────────────────────────────────── */

.am-section-label {
  font-size: 0.65rem;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.am-tracks-value {
  font-size: 0.75rem;
  font-weight: 400;
  line-height: 1.1;
  color: var(--text-muted);
}

.am-hero-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.55rem;
}

.am-play-hero {
  width: 2.6rem;
  height: 2.6rem;
  border: none;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.45rem;
  line-height: 1;
  color: white;
  background: var(--accent);
  cursor: pointer;
  transition: background 0.15s ease, opacity 0.15s ease;
  padding: 0;
}
.am-play-hero .bi-play-fill {
  /* Visually center the play triangle */
  transform: translateX(2px);
}

.am-play-hero:hover:not(:disabled) {
  background: var(--accent-hover);
}

.am-play-hero:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.am-stop-btn {
  width: 2.6rem;
  height: 2.6rem;
  border: 1px solid var(--border-medium);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.2rem;
  color: var(--text-muted);
  background: var(--surface-elevated);
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
  flex-shrink: 0;
}
.am-stop-btn:hover:not(:disabled) {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.am-stop-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.am-section {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--border-subtle);
}

.am-section--timeline {
  padding-top: 0;
  border-top: none;
}

.am-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.75rem;
}

.am-section-title {
  font-size: 0.65rem;
  font-weight: 600;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.am-speed-ms {
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--text-muted);
  letter-spacing: 0.01em;
  white-space: nowrap;
}

.am-timeline {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}

.am-timeline-slider-wrap {
  position: relative;
  padding: 0.2rem 0.2rem;
}

.am-timeline-slider-wrap :deep(.p-slider .p-slider-handle) {
  transition: box-shadow 0.15s ease;
}
.am-timeline-slider-wrap :deep(.p-slider .p-slider-handle:hover),
.am-timeline-slider-wrap :deep(.p-slider .p-slider-handle:focus-visible) {
  box-shadow: 0 0 0 5px var(--accent-glow);
}

/* Playhead marker on top of slider */
.am-playhead {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: var(--accent);
  transform: translateX(-1px);
  pointer-events: none;
  border-radius: 1px;
}
.am-playhead::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--accent);
  border: 2px solid var(--surface-elevated);
  transform: translate(-50%, -50%);
}

.am-timeline-labels {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  font-size: 0.75rem;
  color: var(--text-muted);
  gap: 0.5rem;
}
.am-date {
  font-weight: 400;
  color: var(--text-muted);
}
.am-date--end { text-align: right; }
.am-date-current {
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--text-muted);
  text-align: center;
  letter-spacing: 0.01em;
}

.am-speed-edge {
  font-size: 0.75rem;
  font-weight: 400;
  color: var(--text-muted);
}

.am-speed-labels {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.am-speed-edge--end {
  text-align: right;
}

/* Inline speed in first row — desktop only */
.am-section--inline-speed {
  display: none;
}

@media (min-width: 769px) {
  .am-overview {
    display: grid;
    grid-template-columns: auto minmax(360px, 1.6fr) minmax(260px, 0.9fr);
    align-items: start;
    column-gap: 1rem;
    row-gap: 0;
  }

  .am-overview-top {
    display: contents;
  }

  .am-hero-controls {
    grid-column: 1;
    grid-row: 1;
    align-self: center;
  }

  .am-section--inline-speed {
    grid-column: 3;
    grid-row: 1;
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
    min-width: 0;
    padding-top: 0;
    border-top: none;
  }

  .am-section--speed {
    display: none;
  }

  .am-section--timeline {
    grid-column: 2;
    grid-row: 1;
    display: flex;
    flex-direction: column;
    gap: 0.2rem;
    min-width: 0;
    padding-top: 0;
    border-top: none;
  }

  .am-timeline-head {
    margin: 0;
    min-height: 1.35rem;
    align-items: center;
  }

  .am-timeline {
    min-width: 0;
  }
}

/* ── Unified slider track height (single source of truth) ── */
.am-root :deep(.p-slider) {
  background: var(--slider-track);
  height: 4px !important;
  border-radius: 999px;
}
.am-root :deep(.p-slider .p-slider-range),
.am-root :deep(.p-slider-range) {
  background: var(--slider-gradient);
  border-radius: 999px;
}
.am-root :deep(.p-slider .p-slider-handle) {
  transition: box-shadow 0.15s ease;
}
.am-root :deep(.p-slider .p-slider-handle:hover),
.am-root :deep(.p-slider .p-slider-handle:focus-visible) {
  box-shadow: 0 0 0 5px var(--accent-glow);
}

@media (min-width: 769px) {
  .am-root {
    padding: 0.2rem 1rem 1rem;
  }

  .am-overview {
    gap: 0.9rem;
  }
}

@media only screen and (max-width: 600px) {
  .am-root {
    padding: 0.1rem 0.75rem 0.9rem;
  }

  .am-overview {
    gap: 0.65rem;
  }

  .am-overview-top {
    flex-wrap: wrap;
    min-height: 2.3rem;
    gap: 0.65rem;
  }

  .am-play-hero {
    width: 2.3rem;
    height: 2.3rem;
    font-size: 1.2rem;
  }

  .am-stop-btn {
    width: 2.3rem;
    height: 2.3rem;
    font-size: 1.05rem;
  }

  .am-timeline-labels {
    font-size: 0.7rem;
  }
}
</style>
