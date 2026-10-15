<template>
  <div class="notification-card" :class="`notification-card--${tone}`">
    <span class="notification-card__icon" aria-hidden="true">
      <AppIcon :icon="icon ?? icons[tone]" />
    </span>
    <div class="notification-card__copy">
      <strong v-if="title" class="notification-card__title">{{ title }}</strong>
      <p v-if="detail" class="notification-card__detail">{{ detail }}</p>
      <div v-if="actions?.length" class="notification-card__actions">
        <button
          v-for="action in actions"
          :key="action.label"
          type="button"
          class="notification-card__action"
          :class="{ 'notification-card__action--primary': action.primary }"
          @click="action.run()"
        >
          {{ action.label }}
        </button>
      </div>
    </div>
    <button
      v-if="closable"
      type="button"
      class="notification-card__close"
      :aria-label="title ? `Dismiss notification: ${title}` : 'Dismiss notification'"
      @click="$emit('close')"
    >
      <AppIcon icon="bi bi-x-lg" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import AppIcon from '@/components/ui/AppIcon.vue';
import type { NotificationAction } from '@/types/ui';

const props = withDefaults(
  defineProps<{
    title?: string;
    detail?: string;
    severity?: string;
    icon?: string;
    closable?: boolean;
    actions?: NotificationAction[];
  }>(),
  { title: undefined, detail: undefined, severity: 'info', icon: undefined, closable: true, actions: undefined }
);
defineEmits<{ (event: 'close'): void }>();

const icons = {
  info: 'bi bi-info-circle',
  success: 'bi bi-check-circle',
  warn: 'bi bi-exclamation-triangle',
  error: 'bi bi-x-circle',
};
const tone = computed(() => {
  const severity = props.severity === 'warning' ? 'warn' : props.severity;
  return severity in icons ? (severity as keyof typeof icons) : 'info';
});
</script>

<style scoped>
.notification-card {
  --notification-color: var(--toast-info);
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 1rem;
  border: 1px solid var(--border-medium);
  border-radius: 1rem;
  background: var(--toast-surface);
  color: var(--text-primary);
  box-shadow: var(--toast-shadow);
  backdrop-filter: var(--blur-heavy);
  -webkit-backdrop-filter: var(--blur-heavy);
  font-family: var(--font-family-ui);
  pointer-events: auto;
}
.notification-card--success {
  --notification-color: var(--toast-success);
}
.notification-card--warn {
  --notification-color: var(--toast-warning);
}
.notification-card--error {
  --notification-color: var(--toast-error);
}
.notification-card__icon {
  display: grid;
  place-items: center;
  flex: 0 0 2rem;
  height: 2rem;
  border-radius: 0.625rem;
  background: color-mix(in srgb, var(--notification-color) 12%, transparent);
  color: var(--notification-color);
  font-size: var(--text-base-size);
}
.notification-card__copy {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.notification-card__title {
  display: block;
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-sm-lh);
}
.notification-card__detail {
  margin: 0.25rem 0 0;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}
.notification-card__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.75rem;
}
.notification-card__action,
.notification-card__close {
  min-height: 2.5rem;
  border: 1px solid transparent;
  border-radius: 0.625rem;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
}
.notification-card__action {
  padding: 0.375rem 0.75rem;
  font-size: var(--text-sm-size);
  font-weight: var(--font-medium);
}
.notification-card__action--primary {
  border-color: var(--border-medium);
  background: var(--surface-hover);
  color: var(--text-primary);
}
.notification-card__close {
  display: grid;
  place-items: center;
  flex: 0 0 2.5rem;
  margin: -0.375rem -0.375rem 0 0;
  font-size: var(--text-xs-size);
}
.notification-card__action:hover,
.notification-card__close:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}
.notification-card__action:focus-visible,
.notification-card__close:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}
@media (pointer: coarse) {
  .notification-card__action,
  .notification-card__close {
    min-height: 2.75rem;
    min-width: 2.75rem;
  }
}
</style>
