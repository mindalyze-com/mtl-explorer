<template>
  <div class="rto" aria-label="3D replay telemetry">
    <section class="rto__panel">
      <div class="rto__row rto__row--speed">
        <i class="bi bi-speedometer2"></i>
        <div class="rto__metric rto__metric--speed">
          <strong class="rto__speed-readout">
            <span class="rto__speed-number">{{ speedNumberLabel }}</span>
            <span class="rto__speed-unit">km/h</span>
          </strong>
          <span>Speed</span>
        </div>
      </div>
      <div class="rto__row">
        <i class="bi bi-clock"></i>
        <div class="rto__metric">
          <strong>{{ elapsedLabel }}</strong>
          <span>Elapsed</span>
        </div>
        <div class="rto__metric rto__metric--secondary">
          <strong>{{ remainingLabel }}</strong>
          <span>Remaining</span>
        </div>
      </div>
      <div class="rto__row">
        <i class="bi bi-signpost-split"></i>
        <div class="rto__metric">
          <strong>{{ distanceCurrentLabel }}</strong>
          <span>Distance</span>
        </div>
        <div class="rto__metric rto__metric--secondary">
          <strong>{{ distanceTotalLabel }}</strong>
          <span>Total</span>
        </div>
      </div>
      <div class="rto__row">
        <i class="bi bi-triangle"></i>
        <div class="rto__metric">
          <strong>{{ elevationGainCurrentLabel }}</strong>
          <span>Elev Gain</span>
        </div>
        <div class="rto__metric rto__metric--secondary">
          <strong>{{ elevationMaxLabel }}</strong>
          <span>Elev Max</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

defineOptions({ name: 'ReplayTelemetryOverlay' });

const props = defineProps<{
  currentSpeedKmh: number | null;
  maxSpeedKmh: number;
  elapsedLabel: string;
  remainingLabel: string;
  distanceCurrentLabel: string;
  distanceTotalLabel: string;
  elevationGainCurrentLabel: string;
  elevationMaxLabel: string;
}>();

const speedNumberLabel = computed(() =>
  Number.isFinite(props.currentSpeedKmh) ? Number(props.currentSpeedKmh).toFixed(1) : '--'
);
</script>

<style scoped>
.rto {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  color: #fff;
  --rto-map-control-inset: 0.6rem;
}

.rto__panel {
  position: absolute;
  top: calc(var(--rto-map-control-inset) + var(--safe-top, 0px));
  right: calc(var(--rto-map-control-inset) + var(--safe-right, 0px));
  display: flex;
  flex-direction: column;
  width: max-content;
  min-width: 12.25rem;
  max-width: min(18rem, calc(100vw - 1.2rem));
  overflow: hidden;
  border-radius: 0.42rem;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: rgba(18, 30, 36, 0.64);
  box-shadow: 0 14px 38px rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(18px) saturate(125%);
  -webkit-backdrop-filter: blur(18px) saturate(125%);
}

.rto__row {
  display: grid;
  grid-template-columns: 1.45rem minmax(4.15rem, max-content) minmax(4.15rem, max-content);
  align-items: center;
  gap: 0.44rem;
  min-height: 2.82rem;
  padding: 0.38rem 0.52rem;
}

.rto__row--speed {
  grid-template-columns: 1.45rem minmax(8.74rem, 1fr);
}

.rto__row + .rto__row {
  border-top: 1px solid rgba(255, 255, 255, 0.11);
}

.rto__row i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.45rem;
  height: 1.45rem;
  border-radius: 50%;
  color: rgba(255, 255, 255, 0.9);
  font-size: 0.92rem;
}

.rto__metric {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.08rem;
}

.rto__metric--secondary {
  min-width: 4.15rem;
  padding-left: 0.42rem;
  border-left: 1px solid rgba(255, 255, 255, 0.11);
  text-align: right;
}

.rto__metric strong {
  overflow: hidden;
  color: rgba(255, 255, 255, 0.96);
  font-size: var(--text-sm-size);
  font-weight: 720;
  line-height: var(--text-base-lh);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rto__metric--speed strong {
  font-size: var(--text-lg-size);
  font-weight: 760;
  line-height: var(--text-lg-lh);
}

.rto__metric--speed {
  align-items: center;
  text-align: center;
}

.rto__speed-readout {
  display: inline-flex;
  align-items: baseline;
  justify-content: center;
  gap: 0.24rem;
  min-width: 7.1rem;
  margin-inline: auto;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0;
}

.rto__speed-number {
  color: rgba(255, 255, 255, 0.96);
  font-size: inherit;
  font-weight: inherit;
  line-height: inherit;
  text-align: inherit;
}

.rto__speed-unit {
  color: rgba(255, 255, 255, 0.78);
  font-size: var(--text-sm-size);
  font-weight: 720;
  line-height: var(--text-sm-lh);
}

.rto__metric > span {
  overflow: hidden;
  color: rgba(255, 255, 255, 0.68);
  font-size: var(--text-2xs-size);
  font-weight: 750;
  letter-spacing: 0.08em;
  line-height: var(--text-2xs-lh);
  text-overflow: ellipsis;
  text-transform: uppercase;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .rto__panel {
    top: calc(var(--rto-map-control-inset) + var(--safe-top, 0px));
    right: calc(var(--rto-map-control-inset) + var(--safe-right, 0px));
    min-width: 11.7rem;
    max-width: calc(100vw - 1.2rem);
  }

  .rto__row {
    grid-template-columns: 1.05rem minmax(3.9rem, max-content) minmax(3.9rem, max-content);
    gap: 0.3rem;
    min-height: 2.02rem;
    padding: 0.24rem 0.38rem;
  }

  .rto__row--speed {
    grid-template-columns: 1.05rem minmax(8.1rem, 1fr);
  }

  .rto__row i {
    width: 1.05rem;
    height: 1.05rem;
    font-size: 0.76rem;
  }

  .rto__metric--secondary {
    min-width: 3.9rem;
    padding-left: 0.32rem;
  }

  .rto__metric strong,
  .rto__metric--speed strong {
    font-size: var(--text-xs-size);
    line-height: var(--text-xs-lh);
  }

  .rto__metric--speed strong {
    font-size: var(--text-sm-size);
    line-height: var(--text-sm-lh);
  }

  .rto__speed-readout {
    gap: 0.18rem;
    min-width: 6.25rem;
  }

  .rto__speed-unit {
    font-size: var(--text-xs-size);
    line-height: var(--text-xs-lh);
  }

  .rto__metric > span {
    font-size: 0.54rem;
    letter-spacing: 0.06em;
    line-height: 1;
  }
}

@media (max-width: 390px) {
  .rto__panel {
    min-width: 11.3rem;
    max-width: calc(100vw - 1rem);
  }

  .rto__row {
    padding-right: 0.34rem;
    padding-left: 0.34rem;
  }
}
</style>
