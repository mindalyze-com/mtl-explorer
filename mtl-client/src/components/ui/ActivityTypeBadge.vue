<template>
  <span v-if="type" class="activity-badge" :class="[sizeClass, { 'activity-badge--icon-only': iconOnly }]" :style="colorStyle">
    <i :class="icon" class="activity-badge__icon"></i>
    <span v-if="!iconOnly" class="activity-badge__label">{{ label }}</span>
  </span>
</template>

<script lang="ts">
/**
 * Canonical mapping of activity types to Bootstrap Icons and brand colours.
 * Importable for use outside this component (charts, legends, etc.).
 */
export const ACTIVITY_ICONS: Record<string, string> = {
  BICYCLE: 'bi bi-bicycle',
  HIKING: 'bi bi-compass',
  WALKING: 'bi bi-person-walking',
  CAR: 'bi bi-car-front',
  RUNNING: 'bi bi-lightning-charge',
  SKIING: 'bi bi-snow',
  MOUNTAIN_BIKING: 'bi bi-bicycle',
  STAND_UP_PADDLE: 'bi bi-water',
  ROWING: 'bi bi-water',
  KAYAKING: 'bi bi-droplet',
  MOTORBIKING: 'bi bi-scooter',
  AIRPLANE: 'bi bi-airplane',
  SUPER_SONIC: 'bi bi-rocket',
};

export const ACTIVITY_COLORS: Record<string, string> = {
  BICYCLE: '#6366f1',
  HIKING: '#22c55e',
  WALKING: '#3b82f6',
  RUNNING: '#f97316',
  CAR: '#64748b',
  SKIING: '#06b6d4',
  MOUNTAIN_BIKING: '#a855f7',
  STAND_UP_PADDLE: '#0ea5e9',
  ROWING: '#14b8a6',
  KAYAKING: '#06b6d4',
  MOTORBIKING: '#f59e0b',
  AIRPLANE: '#64748b',
  SUPER_SONIC: '#ef4444',
};

export const ACTIVITY_FALLBACK_COLOR = '#94a3b8';

export function activityIconFor(type: string): string {
  return ACTIVITY_ICONS[type] || 'bi bi-activity';
}

export function activityColorFor(type: string): string {
  return ACTIVITY_COLORS[type] || ACTIVITY_FALLBACK_COLOR;
}

export function activityLabel(type: string): string {
  return type.charAt(0) + type.slice(1).toLowerCase().replace(/_/g, ' ');
}
</script>

<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(defineProps<{
  type: string | null | undefined;
  size?: 'xs' | 'sm' | 'md';
  iconOnly?: boolean;
  colored?: boolean;
}>(), {
  size: 'sm',
  iconOnly: false,
  colored: false,
});

const icon = computed(() => activityIconFor(props.type ?? ''));
const label = computed(() => activityLabel(props.type ?? ''));
const sizeClass = computed(() => `activity-badge--${props.size}`);

const colorStyle = computed(() => {
  if (!props.colored || !props.type) return {};
  const c = activityColorFor(props.type);
  return {
    '--badge-color': c,
    '--badge-bg': c + '14',
    '--badge-border': c + '30',
  };
});
</script>

<style scoped>
.activity-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-weight: 600;
  text-transform: capitalize;
  white-space: nowrap;
  border-radius: 999px;
  background: var(--badge-bg, var(--accent-bg));
  color: var(--badge-color, var(--accent-text));
  border: 1px solid var(--badge-border, var(--accent-subtle));
  line-height: 1;
}

/* ── Sizes ── */
.activity-badge--xs {
  font-size: var(--text-2xs-size);
  padding: 0.1rem 0.35rem;
  gap: 0.18rem;
}
.activity-badge--xs .activity-badge__icon { font-size: var(--text-2xs-size); }

.activity-badge--sm {
  font-size: var(--text-xs-size);
  padding: 0.15rem 0.5rem;
}
.activity-badge--sm .activity-badge__icon { font-size: var(--text-2xs-size); }

.activity-badge--md {
  font-size: var(--text-xs-size);
  padding: 0.2rem 0.6rem;
  gap: 0.3rem;
}
.activity-badge--md .activity-badge__icon { font-size: var(--text-xs-size); }

/* ── Icon-only ── */
.activity-badge--icon-only {
  padding: 0.2rem;
  border-radius: 50%;
}
</style>
