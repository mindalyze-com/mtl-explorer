<template>
  <Toast
    class="app-notifications"
    position="top-center"
    :auto-z-index="false"
    :pt="{ message: messageAttributes }"
    :on-mouse-enter="pauseOnHover"
    :on-mouse-leave="pauseOnHover"
    @close="onMessageClosed"
    @life-end="onMessageClosed"
  >
    <template #container="{ message, closeCallback }">
      <NotificationCard v-bind="cardProps(message)" @close="closeCallback" />
    </template>
  </Toast>
</template>

<script setup lang="ts">
import Toast, { type ToastMessageOptions, type ToastPassThroughMethodOptions } from 'primevue/toast';
import NotificationCard from '@/components/ui/NotificationCard.vue';
import type { ToastMessage } from '@/types/ui';

function cardProps(message: ToastMessageOptions) {
  const notification = message as ToastMessage;
  return {
    title: notification.summary,
    detail: notification.detail,
    severity: notification.severity,
    icon: notification.icon,
    actions: notification.actions,
    closable: notification.closable !== false,
  };
}

function messageAttributes({ props }: ToastPassThroughMethodOptions) {
  const urgent = (props as { message?: ToastMessageOptions }).message?.severity === 'error';
  return { role: urgent ? 'alert' : 'status', 'aria-live': urgent ? 'assertive' : 'polite' };
}

function onMessageClosed({ message }: { message: ToastMessageOptions }) {
  (message as ToastMessage).onClose?.();
}

// PrimeVue pauses timed messages when hover callbacks are supplied.
const pauseOnHover = () => undefined;
</script>

<style>
.app-notifications.p-toast {
  z-index: var(--z-toast);
  top: calc(1rem + env(safe-area-inset-top)) !important;
  width: min(456px, calc(100% - 1rem));
  max-height: calc(100svh - 2rem - env(safe-area-inset-top) - env(safe-area-inset-bottom));
  padding: 0.5rem;
  margin: -0.5rem 0;
  box-sizing: border-box;
  overflow-y: auto;
  pointer-events: none;
}
.app-notifications .p-toast-message {
  margin: 0 0 0.75rem;
  padding: 0;
  border: 0;
  border-radius: 1rem;
  background: transparent;
  box-shadow: none;
  backdrop-filter: none;
}
.app-notifications .p-toast-message:last-child {
  margin-bottom: 0;
}
@media (prefers-reduced-motion: reduce) {
  .app-notifications .p-toast-message {
    animation: none;
  }
}
</style>
