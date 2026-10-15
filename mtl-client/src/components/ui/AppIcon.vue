<template>
  <i
    v-bind="$attrs"
    class="app-icon"
    :class="glyphClasses"
    :aria-hidden="label ? undefined : (attrs['aria-hidden'] ?? true)"
    :aria-label="label ?? attrs['aria-label']"
    :role="label ? 'img' : attrs.role"
  >
    <span
      v-if="dot"
      class="app-icon__dot"
      :class="[`app-icon__dot--${dot.tone ?? 'notice'}`, { 'app-icon__dot--pulse': dot.pulse }]"
      aria-hidden="true"
    />
  </i>
</template>

<script setup lang="ts">
import { computed, useAttrs, type HTMLAttributes } from 'vue';
import type { AppIconProps, AppIconStatusDot } from './appIconTypes';

defineOptions({ inheritAttrs: false });

const props = defineProps<AppIconProps>();
const attrs = useAttrs() as HTMLAttributes;
const dot = computed<AppIconStatusDot | undefined>(() =>
  props.statusDot === true ? {} : props.statusDot || undefined
);
const glyphClasses = computed(() => dot.value?.icon ?? props.icon);
</script>

<style scoped>
.app-icon {
  --app-icon-dot-size: 7px;
  --app-icon-dot-top: -3px;
  --app-icon-dot-right: -5px;
  --app-icon-dot-pulse-duration: 2s;
  display: inline-block;
  position: relative;
  font-style: normal;
}

.app-icon__dot {
  position: absolute;
  top: var(--app-icon-dot-top);
  right: var(--app-icon-dot-right);
  width: var(--app-icon-dot-size);
  height: var(--app-icon-dot-size);
  border-radius: 50%;
  background: var(--app-icon-dot-color);
  box-shadow:
    0 0 6px var(--app-icon-dot-glow),
    0 0 2px var(--app-icon-dot-color);
  pointer-events: none;
}

.app-icon__dot--notice {
  --app-icon-dot-color: var(--alert-dot);
  --app-icon-dot-glow: var(--alert-dot-glow);
}

.app-icon__dot--warning {
  --app-icon-dot-color: var(--warning);
  --app-icon-dot-glow: color-mix(in srgb, var(--warning) 35%, transparent);
}

.app-icon__dot--danger {
  --app-icon-dot-color: var(--error);
  --app-icon-dot-glow: color-mix(in srgb, var(--error) 35%, transparent);
}

.app-icon__dot--success {
  --app-icon-dot-color: var(--success);
  --app-icon-dot-glow: color-mix(in srgb, var(--success) 35%, transparent);
}

.app-icon__dot--pulse {
  animation: app-icon-dot-pulse var(--app-icon-dot-pulse-duration) ease-in-out infinite;
}

@keyframes app-icon-dot-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(0.75);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-icon__dot--pulse {
    animation: none;
  }
}
</style>
