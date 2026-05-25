<template>
  <div class="app-brand">
    <button
      class="app-brand__button"
      type="button"
      :aria-label="`About ${APP_DISPLAY_NAME}`"
      :title="`About ${APP_DISPLAY_NAME}`"
      @click="showAbout = true"
    >
      <span class="app-brand__mark" :style="logoMaskStyle" aria-hidden="true"></span>
    </button>
    <AboutSourceOverlay v-model:visible="showAbout" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import logoMark from '@/assets/logo/logo3/mtl_logo_3_only_vector.svg';
import AboutSourceOverlay from '@/components/info/AboutSourceOverlay.vue';
import { APP_DISPLAY_NAME } from '@/utils/appBranding';

const showAbout = ref(false);
const logoMaskStyle = computed(() => ({
  '--app-brand-logo': `url("${logoMark}")`,
}));
</script>

<style scoped>
.app-brand {
  display: flex;
  justify-content: center;
  width: 100%;
}

.app-brand__button {
  width: 42px;
  height: 42px;
  border: none;
  border-radius: 0.625rem;
  background: transparent;
  color: var(--text-faint);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.66;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    opacity 0.15s ease,
    transform 0.15s ease;
}

.app-brand__button:hover,
.app-brand__button:focus-visible {
  background: var(--surface-hover);
  color: var(--text-secondary);
  opacity: 1;
}

.app-brand__button:focus {
  outline: none;
}

.app-brand__button:focus-visible {
  outline: 2px solid var(--accent-muted);
  outline-offset: 2px;
}

.app-brand__button:active {
  transform: scale(0.94);
}

.app-brand__mark {
  display: block;
  flex: 0 0 auto;
  background: currentColor;
  mask: var(--app-brand-logo) center / contain no-repeat;
  -webkit-mask: var(--app-brand-logo) center / contain no-repeat;
}

.app-brand__mark {
  width: 22px;
  height: 27px;
}

@media (min-width: 1280px) {
  .app-brand__button {
    width: 46px;
    height: 46px;
  }

  .app-brand__mark {
    width: 24px;
    height: 29px;
  }
}
</style>
