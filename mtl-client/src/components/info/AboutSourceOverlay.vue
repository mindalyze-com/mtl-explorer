<template>
  <BottomSheet
    :model-value="visible"
    title="About MTL Explorer"
    icon="bi bi-info-circle"
    :detents="[
      { id: 'comfortable', height: 'min(650px, 84vh)' },
      { id: 'large', height: '94vh' },
    ]"
    initial-detent="comfortable"
    sheet-class="sheet--solid-over-map sheet--about-source"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="about-source">
      <section class="about-source__overview" aria-labelledby="about-source-title">
        <div class="about-source__identity">
          <span class="about-source__logo" aria-hidden="true">
            <img :src="logoMark" alt="" />
          </span>
          <div>
            <span class="settings-eyebrow about-source__eyebrow">This installation</span>
            <h2 id="about-source-title">MTL Explorer</h2>
            <p>Self-hosted GPS track and trail log.</p>
          </div>
        </div>

        <dl class="about-source__build" aria-label="Installation information">
          <div>
            <dt>Version</dt>
            <dd>{{ version }}</dd>
          </div>
          <div>
            <dt>License</dt>
            <dd>AGPL-3.0-or-later</dd>
          </div>
        </dl>

        <a class="about-source__primary" :href="APP_SOURCE_URL" target="_blank" rel="noopener noreferrer">
          <i class="bi bi-code-slash" aria-hidden="true"></i>
          View source code
        </a>
      </section>

      <section class="about-source__section" aria-labelledby="about-source-license">
        <div class="settings-section-heading about-source__section-heading">
          <h3 id="about-source-license">Open source</h3>
          <p>Use, inspect, modify, and share under the AGPL.</p>
        </div>

        <p class="about-source__license-copy">
          MTL Explorer is dual-licensed under the
          <a href="https://www.gnu.org/licenses/agpl-3.0.html" target="_blank" rel="noopener noreferrer">
            GNU AGPL, version 3 or later</a
          >, and a separate commercial license.
        </p>
      </section>

      <section class="about-source__section" aria-labelledby="about-source-links">
        <div class="settings-section-heading about-source__section-heading">
          <h3 id="about-source-links">Project</h3>
          <p>Source, licensing, and full project information.</p>
        </div>

        <div class="settings-list about-source__list">
          <a
            class="settings-row"
            :href="`${APP_SOURCE_URL}/blob/main/LICENSE`"
            target="_blank"
            rel="noopener noreferrer"
          >
            <i class="bi bi-file-text settings-row__icon about-source__row-icon" aria-hidden="true"></i>
            <span class="settings-row__body about-source__row-copy">
              <strong>License text</strong>
              <span class="settings-row__description">Read the full license terms for this project.</span>
            </span>
            <span class="settings-row__value about-source__row-value">AGPL-3.0-or-later</span>
            <i class="bi bi-box-arrow-up-right settings-row__chevron about-source__row-arrow" aria-hidden="true"></i>
          </a>

          <a class="settings-row" :href="`mailto:${APP_CONTACT_EMAIL}`">
            <i class="bi bi-briefcase settings-row__icon about-source__row-icon" aria-hidden="true"></i>
            <span class="settings-row__body about-source__row-copy">
              <strong>Commercial licensing</strong>
              <span class="settings-row__description">Ask about alternative license terms.</span>
            </span>
            <span class="settings-row__value about-source__row-value">{{ APP_CONTACT_EMAIL }}</span>
            <i class="bi bi-chevron-right settings-row__chevron about-source__row-arrow" aria-hidden="true"></i>
          </a>

          <button class="settings-row about-source__details-trigger" type="button" @click="showFullAbout = true">
            <i class="bi bi-book settings-row__icon about-source__row-icon" aria-hidden="true"></i>
            <span class="settings-row__body about-source__row-copy">
              <strong>About and credits</strong>
              <span class="settings-row__description">Read licenses, attribution, and project details.</span>
            </span>
            <span class="settings-row__value about-source__row-value">Full details</span>
            <i class="bi bi-chevron-right settings-row__chevron about-source__row-arrow" aria-hidden="true"></i>
          </button>
        </div>
      </section>

      <footer class="about-source__footer">
        <span>&copy; 2020-2026 Patrick Heusser and contributors</span>
        <span>AGPL-3.0-or-later</span>
      </footer>
    </div>
  </BottomSheet>

  <AboutView v-if="showFullAbout" embedded @closed="closeFullAbout" />
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from 'vue';
import logoMark from '@/assets/logo/logo3/mtl_logo_3_only_vector.svg';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { APP_CONTACT_EMAIL, APP_SOURCE_URL } from '@/utils/appBranding';

defineOptions({ name: 'AboutSourceOverlay' });

defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void;
}>();

const AboutView = defineAsyncComponent(() => import('@/views/AboutView.vue'));
const version = computed<string>(
  () =>
    (import.meta.env.VITE_APP_VERSION as string) ||
    (typeof __APP_PKG_VERSION__ !== 'undefined' ? __APP_PKG_VERSION__ : 'unknown')
);
const showFullAbout = ref(false);

function closeFullAbout() {
  showFullAbout.value = false;
  emit('update:visible', false);
}
</script>

<style scoped>
.about-source {
  width: 100%;
  margin: 0 auto;
  padding: 0.45rem 1rem calc(1rem + env(safe-area-inset-bottom));
  box-sizing: border-box;
  color: var(--text-secondary);
}

.about-source__overview {
  position: relative;
  display: flex;
  min-height: 13.25rem;
  box-sizing: border-box;
  flex-direction: column;
  padding: 0.75rem 0.25rem 0.9rem 1.05rem;
}

.about-source__overview::before {
  position: absolute;
  top: 0.8rem;
  bottom: 0.8rem;
  left: 0;
  width: 0.2rem;
  border-radius: 999px;
  background: var(--accent);
  content: '';
}

.about-source__identity {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 0.8rem;
}

.about-source__logo {
  display: grid;
  width: 3.25rem;
  height: 3.25rem;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--accent) 18%, var(--border-default));
  border-radius: 0.75rem;
  background: color-mix(in srgb, var(--accent) 7%, transparent);
}

.about-source__logo img {
  width: 48%;
  height: 62%;
  object-fit: contain;
}

.about-source__identity h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-xl-size);
  font-weight: var(--font-bold);
  line-height: var(--text-xl-lh);
}

.about-source__eyebrow {
  margin-bottom: 0.15rem;
}

.about-source__identity p {
  margin: 0.18rem 0 0;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.about-source__build {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
  margin: 0.9rem 0 0;
}

.about-source__build div {
  min-width: 0;
}

.about-source__build dt {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.about-source__build dd {
  margin: 0.08rem 0 0;
  color: var(--text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-sm-lh);
}

.about-source__primary {
  display: inline-flex;
  min-height: 2.5rem;
  align-self: flex-start;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  margin-top: auto;
  padding: 0.48rem 0.7rem;
  border-radius: 0.55rem;
  background: color-mix(in srgb, var(--accent) 11%, transparent);
  color: var(--accent-text);
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  text-decoration: none;
}

.about-source__primary:hover {
  background: color-mix(in srgb, var(--accent) 17%, transparent);
}

.about-source__primary:focus-visible,
.about-source__list > :is(a, button):focus-visible {
  position: relative;
  z-index: 1;
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.about-source__section {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  padding: 1rem 0.15rem 0;
}

.about-source__section + .about-source__section {
  margin-top: 1.2rem;
}

.about-source__license-copy {
  margin: 0;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: 1.55;
}

.about-source__license-copy a {
  color: var(--accent-text);
  text-underline-offset: 2px;
}

.about-source__list > .settings-row {
  grid-template-columns: 1.6rem minmax(0, 1fr) minmax(7rem, auto) auto;
}

.about-source__footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.25rem 1rem;
  padding: 1.15rem 0.15rem 0.25rem;
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

@media screen and (max-width: 600px) {
  :global(.sheet.sheet--about-source .sheet-fullscreen-btn) {
    display: none;
  }

  .about-source {
    padding: 0.4rem 1rem calc(1.1rem + env(safe-area-inset-bottom));
  }

  .about-source__overview {
    min-height: 13.5rem;
    padding-left: 0.9rem;
  }

  .about-source__identity {
    align-items: flex-start;
    gap: 0.7rem;
  }

  .about-source__logo {
    width: 2.9rem;
    height: 2.9rem;
  }

  .about-source__build {
    gap: 0.8rem 1.25rem;
  }

  .about-source__section + .about-source__section {
    margin-top: 1rem;
  }

  .about-source__list > :is(a, button) {
    min-height: 4.25rem;
    grid-template-columns: 1.45rem minmax(0, 1fr) auto;
    gap: 0.65rem;
    padding: 0.7rem 0.1rem;
  }

  .about-source__row-value {
    display: none;
  }

  .about-source__footer {
    flex-direction: column;
  }
}
</style>
