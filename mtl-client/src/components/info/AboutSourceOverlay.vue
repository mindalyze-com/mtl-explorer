<template>
  <Teleport to="body">
    <Transition name="about-fade">
      <div v-if="visible" class="about-overlay-backdrop" @click="close">
        <div
          class="about-overlay-panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="about-source-title"
          @click.stop
        >
          <button class="about-overlay-close" aria-label="Close" @click="close">&times;</button>
          <div class="about-overlay-header">
            <p class="about-overlay-kicker">About &amp; Source</p>
            <h2 id="about-source-title" class="about-overlay-title">MTL Explorer</h2>
            <p class="about-overlay-version">Version {{ version }}</p>
          </div>
          <div class="about-overlay-chips" aria-label="License summary">
            <span class="about-overlay-chip">AGPL-3.0-or-later</span>
            <span class="about-overlay-chip">Commercial license available</span>
          </div>
          <div class="about-overlay-divider"></div>
          <div class="about-overlay-body">
            <p class="about-overlay-text">
              MTL Explorer is dual-licensed under
              <a href="https://www.gnu.org/licenses/agpl-3.0.html" target="_blank" rel="noopener">AGPL-3.0-or-later</a>
              and a separate commercial license.
            </p>
            <p class="about-overlay-text">
              If you modify the software and make it available over a network, you must offer the corresponding source
              code of that running version.
            </p>
            <div class="about-overlay-section">
              <p class="about-overlay-label">Source code</p>
              <p class="about-overlay-text">
                <a :href="APP_SOURCE_URL" target="_blank" rel="noopener">{{ APP_SOURCE_URL }}</a>
              </p>
            </div>
            <div class="about-overlay-section">
              <p class="about-overlay-label">Commercial inquiries</p>
              <p class="about-overlay-text">
                <a :href="`mailto:${APP_CONTACT_EMAIL}`">{{ APP_CONTACT_EMAIL }}</a>
              </p>
            </div>
            <p class="about-overlay-text about-overlay-copyright">
              &copy; 2020-2026 Patrick Heusser &amp; contributors
            </p>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { APP_CONTACT_EMAIL, APP_SOURCE_URL } from '@/utils/appBranding';

defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void;
}>();

const version = computed<string>(() => (import.meta.env.VITE_APP_VERSION as string) || 'dev');

function close(): void {
  emit('update:visible', false);
}
</script>

<style scoped>
.about-overlay-backdrop {
  position: fixed;
  inset: 0;
  z-index: var(--z-popup-over-bottomsheet, 6000);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(1rem, 3vw, 2rem);
  background: linear-gradient(to bottom, rgba(9, 12, 18, 0.08), rgba(9, 12, 18, 0.18));
  backdrop-filter: blur(1.5px);
  -webkit-backdrop-filter: blur(1.5px);
}

.about-overlay-panel {
  position: relative;
  width: 100%;
  max-width: min(34rem, calc(100vw - 2rem));
  max-height: min(40rem, calc(100vh - 3rem));
  overflow: hidden;
  padding: 1rem 1rem 0.95rem;
  border-radius: 18px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0.78));
  backdrop-filter: blur(20px) saturate(115%);
  -webkit-backdrop-filter: blur(20px) saturate(115%);
  border: 1px solid rgba(255, 255, 255, 0.42);
  box-shadow:
    0 14px 40px rgba(0, 0, 0, 0.14),
    inset 0 1px 0 rgba(255, 255, 255, 0.48);
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

:global([data-theme='dark']) .about-overlay-panel {
  background: linear-gradient(180deg, rgba(13, 17, 24, 0.86), rgba(13, 17, 24, 0.78));
  border-color: rgba(255, 255, 255, 0.09);
  box-shadow:
    0 18px 44px rgba(0, 0, 0, 0.42),
    inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.about-overlay-close {
  position: absolute;
  top: 0.75rem;
  right: 0.8rem;
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 999px;
  cursor: pointer;
  font-size: 0.65rem;
  color: rgba(0, 0, 0, 0.28);
  width: 1.5rem;
  height: 1.5rem;
  padding: 0;
  line-height: 1;
  transition:
    color 0.15s ease,
    background-color 0.15s ease,
    border-color 0.15s ease;
}
.about-overlay-close:hover {
  color: rgba(0, 0, 0, 0.62);
  background: rgba(0, 0, 0, 0.08);
  border-color: rgba(0, 0, 0, 0.08);
}

:global([data-theme='dark']) .about-overlay-close {
  color: rgba(255, 255, 255, 0.28);
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.06);
}

:global([data-theme='dark']) .about-overlay-close:hover {
  color: rgba(255, 255, 255, 0.68);
  background: rgba(255, 255, 255, 0.08);
  border-color: rgba(255, 255, 255, 0.08);
}

.about-overlay-header {
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
  padding-right: 2rem;
}

.about-overlay-kicker {
  margin: 0;
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(0, 0, 0, 0.36);
}

.about-overlay-title {
  margin: 0;
  font-size: clamp(1.05rem, 2vw, 1.25rem);
  font-weight: 600;
  line-height: 1.15;
  color: rgba(0, 0, 0, 0.72);
}

.about-overlay-version {
  margin: 0;
  font-size: 0.7rem;
  color: rgba(0, 0, 0, 0.36);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

:global([data-theme='dark']) .about-overlay-kicker {
  color: rgba(255, 255, 255, 0.34);
}

:global([data-theme='dark']) .about-overlay-title {
  color: rgba(255, 255, 255, 0.74);
}

:global([data-theme='dark']) .about-overlay-version {
  color: rgba(255, 255, 255, 0.3);
}

.about-overlay-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.about-overlay-chip {
  display: inline-flex;
  align-items: center;
  min-height: 1.5rem;
  padding: 0.15rem 0.55rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.46);
  border: 1px solid rgba(0, 0, 0, 0.06);
  color: rgba(0, 0, 0, 0.46);
  font-size: 0.68rem;
  white-space: nowrap;
}

:global([data-theme='dark']) .about-overlay-chip {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.4);
}

.about-overlay-divider {
  height: 1px;
  background: linear-gradient(90deg, rgba(0, 0, 0, 0.08), rgba(0, 0, 0, 0.03));
  margin: 0;
}
:global([data-theme='dark']) .about-overlay-divider {
  background: linear-gradient(90deg, rgba(255, 255, 255, 0.1), rgba(255, 255, 255, 0.03));
}

.about-overlay-body {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  overflow-y: auto;
  padding-right: 0.2rem;
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.14) transparent;
}

.about-overlay-body::-webkit-scrollbar {
  width: 5px;
}

.about-overlay-body::-webkit-scrollbar-track {
  background: transparent;
}

.about-overlay-body::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.14);
}

:global([data-theme='dark']) .about-overlay-body {
  scrollbar-color: rgba(255, 255, 255, 0.14) transparent;
}

:global([data-theme='dark']) .about-overlay-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.14);
}

.about-overlay-section {
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
  padding: 0.55rem 0.7rem;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.34);
  border: 1px solid rgba(0, 0, 0, 0.05);
}

:global([data-theme='dark']) .about-overlay-section {
  background: rgba(255, 255, 255, 0.04);
  border-color: rgba(255, 255, 255, 0.06);
}

.about-overlay-label {
  margin: 0;
  font-size: 0.66rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(0, 0, 0, 0.34);
}

:global([data-theme='dark']) .about-overlay-label {
  color: rgba(255, 255, 255, 0.3);
}

.about-overlay-text {
  margin: 0;
  font-size: 0.76rem;
  line-height: 1.45;
  color: rgba(0, 0, 0, 0.5);
}
.about-overlay-text a {
  color: rgba(0, 0, 0, 0.58);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.about-overlay-text a:hover {
  color: rgba(0, 0, 0, 0.78);
}

:global([data-theme='dark']) .about-overlay-text {
  color: rgba(255, 255, 255, 0.42);
}

:global([data-theme='dark']) .about-overlay-text a {
  color: rgba(255, 255, 255, 0.48);
}

:global([data-theme='dark']) .about-overlay-text a:hover {
  color: rgba(255, 255, 255, 0.68);
}

.about-overlay-copyright {
  color: rgba(0, 0, 0, 0.3);
  padding-top: 0.15rem;
}
:global([data-theme='dark']) .about-overlay-copyright {
  color: rgba(255, 255, 255, 0.24);
}

@media (max-width: 540px) {
  .about-overlay-panel {
    max-width: calc(100vw - 1.25rem);
    max-height: calc(100vh - 1.5rem);
    border-radius: 16px;
    padding: 0.9rem 0.9rem 0.85rem;
  }
}

.about-fade-enter-active,
.about-fade-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}
.about-fade-enter-from,
.about-fade-leave-to {
  opacity: 0;
  transform: scale(0.97);
}
</style>
