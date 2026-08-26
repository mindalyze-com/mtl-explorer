<!--
  SPDX-License-Identifier: AGPL-3.0-or-later
  Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors.
-->
<template>
  <div :class="embedded ? 'about-embedded' : 'about-route'">
    <div v-if="!embedded" class="about-route__ambient" aria-hidden="true">
      <img :src="logoMark" alt="" />
    </div>

    <BottomSheet
      v-model="isOpen"
      title="About MTL Explorer"
      icon="bi bi-info-circle"
      :detents="[
        { id: 'comfortable', height: 'min(700px, 82vh)' },
        { id: 'large', height: '92vh' },
      ]"
      initial-detent="comfortable"
      :viewport-centered="viewportCentered"
      sheet-class="sheet--solid-over-map sheet--about"
      @closed="leaveAbout"
    >
      <div class="about-sheet">
        <section class="about-overview" aria-labelledby="about-title">
          <div class="about-overview__identity">
            <span class="about-logo" aria-hidden="true">
              <img :src="logoMark" alt="" />
            </span>
            <div>
              <p class="about-eyebrow">About this installation</p>
              <h1 id="about-title">MTL Explorer</h1>
              <p class="about-tagline">Self-hosted GPS track and trail log.</p>
            </div>
          </div>

          <dl class="about-build" aria-label="Build information">
            <div>
              <dt>Version</dt>
              <dd>{{ version }}</dd>
            </div>
            <div>
              <dt>Build</dt>
              <dd>{{ buildInfo }}</dd>
            </div>
          </dl>

          <div class="about-overview__actions">
            <a
              class="about-action about-action--primary about-overview__source"
              :href="sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              <i class="bi bi-code-slash" aria-hidden="true" />
              View source
            </a>
            <a class="about-action" :href="'mailto:' + contactEmail">
              <i class="bi bi-envelope" aria-hidden="true" />
              Contact
            </a>
          </div>
        </section>

        <section class="about-reference" aria-labelledby="reference-heading">
          <header class="about-reference__header">
            <p class="about-eyebrow">Reference</p>
            <h2 id="reference-heading">Project details</h2>
            <p>Licensing, credits, contribution, and policy information.</p>
          </header>

          <div class="about-disclosures">
            <details class="about-disclosure" data-test="about-license">
              <summary>
                <span class="about-disclosure__icon"><i class="bi bi-unlock" aria-hidden="true" /></span>
                <span class="about-disclosure__summary">
                  <strong>License and commercial use</strong>
                  <span>AGPL terms and alternative licensing.</span>
                </span>
                <i class="bi bi-chevron-right about-disclosure__chevron" aria-hidden="true" />
              </summary>
              <div class="about-disclosure__body">
                <p class="about-lead">
                  MTL Explorer is free software, dual-licensed under the
                  <strong>GNU Affero General Public License, version 3 or later</strong>
                  (AGPL-3.0-or-later) and a separate commercial license.
                </p>
                <p>
                  This program is distributed in the hope that it will be useful, but
                  <strong>WITHOUT ANY WARRANTY</strong>; without even the implied warranty of
                  <em>MERCHANTABILITY</em> or <em>FITNESS FOR A PARTICULAR PURPOSE</em>. See the GNU AGPL for more
                  details.
                </p>

                <div class="about-source-row">
                  <div>
                    <span class="about-source-row__label">Source code for this installation</span>
                    <a :href="sourceUrl" target="_blank" rel="noopener noreferrer">{{ sourceUrl }}</a>
                  </div>
                  <i class="bi bi-box-arrow-up-right" aria-hidden="true" />
                </div>

                <p class="about-lead">
                  Personal, private, and home use is free. A commercial license is available for use under terms other
                  than the AGPL.
                </p>
                <div class="about-links" aria-label="License documents">
                  <a href="https://www.gnu.org/licenses/agpl-3.0.html" target="_blank" rel="noopener noreferrer">
                    AGPL-3.0
                  </a>
                  <a :href="sourceUrl + '/blob/main/LICENSE'" target="_blank" rel="noopener noreferrer">LICENSE</a>
                  <a
                    :href="sourceUrl + '/blob/main/documentation/legal/NOTICE'"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    NOTICE
                  </a>
                  <a
                    :href="sourceUrl + '/blob/main/documentation/legal/THIRD_PARTY_LICENSES.md'"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Third-party licenses
                  </a>
                  <a
                    :href="sourceUrl + '/blob/main/documentation/legal/COMMERCIAL-LICENSE.md'"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Commercial license
                  </a>
                  <a :href="'mailto:' + contactEmail">Licensing contact</a>
                </div>
              </div>
            </details>

            <details class="about-disclosure" data-test="about-credits">
              <summary>
                <span class="about-disclosure__icon"><i class="bi bi-boxes" aria-hidden="true" /></span>
                <span class="about-disclosure__summary">
                  <strong>Credits and data sources</strong>
                  <span>Libraries, maps, routing, search, and demo data.</span>
                </span>
                <i class="bi bi-chevron-right about-disclosure__chevron" aria-hidden="true" />
              </summary>
              <div class="about-disclosure__body about-disclosure__body--credits">
                <AttributionTab :is-demo-mode="isDemoMode" />
              </div>
            </details>

            <details class="about-disclosure" data-test="about-contribute">
              <summary>
                <span class="about-disclosure__icon"><i class="bi bi-git" aria-hidden="true" /></span>
                <span class="about-disclosure__summary">
                  <strong>Contribute</strong>
                  <span>Guidelines, contributor agreement, conduct, and security.</span>
                </span>
                <i class="bi bi-chevron-right about-disclosure__chevron" aria-hidden="true" />
              </summary>
              <div class="about-disclosure__body">
                <p class="about-lead">
                  Bug reports, patches, and improvements are welcome. Contributors must sign the project CLA so the
                  dual-licensing model can continue.
                </p>
                <div class="about-links">
                  <a :href="sourceUrl + '/blob/main/.github/CONTRIBUTING.md'" target="_blank" rel="noopener noreferrer">
                    Contributing
                  </a>
                  <a
                    :href="sourceUrl + '/blob/main/documentation/legal/CLA.md'"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Contributor agreement
                  </a>
                  <a
                    :href="sourceUrl + '/blob/main/.github/CODE_OF_CONDUCT.md'"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Code of Conduct
                  </a>
                  <a :href="sourceUrl + '/blob/main/.github/SECURITY.md'" target="_blank" rel="noopener noreferrer">
                    Security
                  </a>
                </div>
              </div>
            </details>

            <details class="about-disclosure" data-test="about-trademark">
              <summary>
                <span class="about-disclosure__icon"><i class="bi bi-badge-tm" aria-hidden="true" /></span>
                <span class="about-disclosure__summary">
                  <strong>Trademark</strong>
                  <span>Name and logo use.</span>
                </span>
                <i class="bi bi-chevron-right about-disclosure__chevron" aria-hidden="true" />
              </summary>
              <div class="about-disclosure__body">
                <p class="about-lead">
                  “MTL Explorer” and the MTL Explorer logo are trademarks of Patrick Heusser. The open source license
                  does not grant trademark rights; forks must use a different name and logo.
                </p>
                <div class="about-links">
                  <a
                    :href="sourceUrl + '/blob/main/documentation/legal/TRADEMARK.md'"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Trademark policy
                  </a>
                </div>
              </div>
            </details>

            <details class="about-disclosure" data-test="about-disclaimer">
              <summary>
                <span class="about-disclosure__icon about-disclosure__icon--warning">
                  <i class="bi bi-exclamation-triangle" aria-hidden="true" />
                </span>
                <span class="about-disclosure__summary">
                  <strong>Safety disclaimer</strong>
                  <span>Navigation limits and liability.</span>
                </span>
                <i class="bi bi-chevron-right about-disclosure__chevron" aria-hidden="true" />
              </summary>
              <div class="about-disclosure__body">
                <p class="about-lead">
                  MTL Explorer is <strong>not a safety-critical navigation system</strong>. Do not rely on it as the
                  sole means of navigation where inaccuracy could cause injury or loss of life. Carry suitable backup
                  navigation tools.
                </p>
                <div class="about-links">
                  <a
                    :href="sourceUrl + '/blob/main/documentation/legal/DISCLAIMER.md'"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Full disclaimer and limitation of liability
                  </a>
                </div>
              </div>
            </details>
          </div>
        </section>

        <footer class="about-footer">
          <p>Copyright &copy; 2020-2026 Patrick Heusser and MTL Explorer contributors.</p>
          <span>Licensed under AGPL-3.0-or-later.</span>
        </footer>
      </div>
    </BottomSheet>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import AttributionTab from '@/components/admin/AttributionTab.vue';
import logoMark from '@/assets/logo/logo3/mtl_logo_3_only_vector.svg';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { getDemoStatus } from '@/utils/ServiceHelper';
import { APP_CONTACT_EMAIL, APP_SOURCE_URL } from '@/utils/appBranding';
import { isAuthenticated } from '@/utils/auth';

defineOptions({ name: 'AboutView' });

const props = withDefaults(
  defineProps<{
    embedded?: boolean;
    viewportCentered?: boolean;
  }>(),
  {
    embedded: false,
    viewportCentered: true,
  }
);

const emit = defineEmits<{
  (event: 'closed'): void;
}>();

// Release images inject their immutable image identity. Local builds use Vite's
// package version and build timestamp so the values remain precise in dev.
const version = computed<string>(
  () =>
    (import.meta.env.VITE_APP_VERSION as string) ||
    (typeof __APP_PKG_VERSION__ !== 'undefined' ? __APP_PKG_VERSION__ : 'unknown')
);
const buildInfo = computed<string>(
  () =>
    (import.meta.env.VITE_APP_BUILD as string) || (typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : 'unknown')
);
const sourceUrl = APP_SOURCE_URL;
const contactEmail = APP_CONTACT_EMAIL;
const isDemoMode = ref(false);
const isOpen = ref(true);
const router = useRouter();

onMounted(async () => {
  try {
    isDemoMode.value = (await getDemoStatus()).demoMode;
  } catch {
    isDemoMode.value = false;
  }
});

function leaveAbout() {
  if (props.embedded) {
    emit('closed');
    return;
  }

  const previousRoute = (window.history.state as { back?: unknown } | null)?.back;
  if (typeof previousRoute === 'string' && previousRoute.length > 0) {
    router.back();
    return;
  }
  void router.push({ name: isAuthenticated() ? 'home' : 'login' });
}
</script>

<style scoped>
.about-route {
  min-height: 100vh;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 28%, color-mix(in srgb, var(--accent) 8%, transparent), transparent 24rem),
    var(--map-container-bg);
}

.about-embedded {
  display: contents;
}

.about-route__ambient {
  min-height: 100vh;
  display: grid;
  place-items: center;
  opacity: 0.16;
}

.about-route__ambient img {
  width: min(7rem, 22vw);
  max-height: 9rem;
}

.about-sheet {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: 1.55;
}

.about-sheet strong {
  font-weight: 650;
}

.about-overview {
  padding: 0.9rem clamp(1rem, 3vw, 1.4rem) 1.1rem;
  border-bottom: 1px solid var(--border-default);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent-bg) 42%, transparent), transparent 58%),
    var(--surface-sheet-solid);
}

.about-overview__identity {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.about-logo {
  width: 3.5rem;
  aspect-ratio: 1;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border: 1px solid color-mix(in srgb, var(--accent) 20%, var(--border-default));
  border-radius: 0.8rem;
  background: color-mix(in srgb, var(--surface-sheet-solid) 84%, transparent);
}

.about-logo img {
  width: 52%;
  height: 62%;
  object-fit: contain;
}

.about-eyebrow {
  margin: 0;
  color: var(--accent-text);
  font-size: var(--text-2xs-size);
  font-weight: 700;
  letter-spacing: 0.07em;
  line-height: 1.3;
  text-transform: uppercase;
}

.about-overview h1 {
  margin: 0.15rem 0 0;
  color: var(--text-primary);
  font-size: var(--text-2xl-size);
  letter-spacing: -0.025em;
  line-height: 1.1;
}

.about-tagline {
  margin: 0.25rem 0 0;
  color: var(--text-muted);
}

.about-build {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0.85rem 0 0;
  padding-block: 0.2rem;
  border-block: 1px solid var(--border-default);
}

.about-build div {
  min-width: 0;
  padding: 0.35rem 0.75rem;
  border-left: 1px solid var(--border-default);
}

.about-build div:first-child {
  padding-left: 0;
  border-left: 0;
}

.about-build dt {
  color: var(--text-faint);
  font-size: var(--text-2xs-size);
  font-weight: 650;
  text-transform: uppercase;
}

.about-build dd {
  margin: 0.1rem 0 0;
  color: var(--text-secondary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.about-overview__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-top: 0.75rem;
}

.about-action {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.4rem 0.7rem;
  border: 1px solid var(--border-medium);
  border-radius: 0.55rem;
  background: var(--surface-sheet-solid);
  color: var(--text-secondary);
  font-weight: 650;
  text-decoration: none;
}

.about-action:hover {
  border-color: var(--border-hover);
  background: var(--surface-hover);
}

.about-action--primary {
  border-color: color-mix(in srgb, var(--accent) 32%, transparent);
  background: var(--accent-bg);
  color: var(--accent-text);
}

.about-action:focus-visible,
.about-links a:focus-visible,
.about-source-row:focus-within,
.about-disclosure > summary:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 3px;
  border-radius: 0.45rem;
}

.about-reference {
  padding: 1rem clamp(1rem, 3vw, 1.4rem) 1.15rem;
  background: var(--surface-sheet-solid);
}

.about-reference__header h2 {
  margin: 0.15rem 0 0;
  color: var(--text-primary);
  font-size: var(--text-lg-size);
  letter-spacing: -0.015em;
  line-height: 1.2;
}

.about-reference__header > p:last-child {
  margin: 0.2rem 0 0;
  color: var(--text-faint);
}

.about-disclosures {
  margin-top: 0.85rem;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: 0.7rem;
  background: var(--surface-elevated);
}

.about-disclosure + .about-disclosure {
  border-top: 1px solid var(--border-default);
}

.about-disclosure > summary {
  min-height: 3.6rem;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.7rem;
  padding: 0.65rem 0.8rem;
  box-sizing: border-box;
  color: var(--text-secondary);
  cursor: pointer;
  list-style: none;
}

.about-disclosure > summary::-webkit-details-marker {
  display: none;
}

.about-disclosure > summary:hover,
.about-disclosure[open] > summary {
  background: var(--surface-hover);
}

.about-disclosure__icon {
  width: 2rem;
  height: 2rem;
  display: grid;
  place-items: center;
  border-radius: 0.55rem;
  background: color-mix(in srgb, var(--accent-bg) 64%, transparent);
  color: var(--accent-text);
  font-size: var(--text-sm-size);
}

.about-disclosure__icon--warning {
  background: color-mix(in srgb, var(--warning-bg) 45%, transparent);
  color: var(--warning);
}

.about-disclosure__summary {
  min-width: 0;
  display: flex;
  flex-direction: column;
  line-height: 1.35;
}

.about-disclosure__summary strong {
  color: var(--text-primary);
  font-size: var(--text-sm-size);
}

.about-disclosure__summary span {
  margin-top: 0.05rem;
  color: var(--text-faint);
  font-size: var(--text-xs-size);
}

.about-disclosure__chevron {
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  transition: transform 0.15s ease;
}

.about-disclosure[open] .about-disclosure__chevron {
  transform: rotate(90deg);
}

.about-disclosure__body {
  padding: 0.9rem 1rem 1rem 3.5rem;
  border-top: 1px solid var(--border-default);
  background: var(--surface-sheet-solid);
}

.about-disclosure__body > :first-child {
  margin-top: 0;
}

.about-disclosure__body p {
  margin: 0.55rem 0 0;
}

.about-disclosure__body a {
  color: var(--accent-text);
}

.about-lead {
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
}

.about-source-row {
  margin-top: 0.9rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.65rem 0;
  border-block: 1px solid var(--border-default);
}

.about-source-row > div {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.about-source-row__label {
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  font-weight: 650;
}

.about-source-row a {
  overflow-wrap: anywhere;
  text-decoration: none;
}

.about-source-row > i {
  flex: 0 0 auto;
  color: var(--text-faint);
}

.about-links {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.75rem;
}

.about-links a {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.3rem 0.6rem;
  border: 1px solid var(--border-default);
  border-radius: 999px;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: 650;
  text-decoration: none;
}

.about-links a:hover {
  border-color: color-mix(in srgb, var(--accent) 28%, var(--border-default));
  color: var(--accent-text);
}

.about-disclosure__body--credits :deep(.attribution-header) {
  margin-bottom: 0.7rem;
}

.about-disclosure__body--credits :deep(.attribution-header h2) {
  font-size: var(--text-base-size);
  letter-spacing: -0.015em;
}

.about-disclosure__body--credits :deep(.attrib-list) {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  border-top: 1px solid var(--border-default);
}

.about-disclosure__body--credits :deep(.attrib-entry) {
  min-width: 0;
  padding: 0.68rem 0;
  border: 0;
  border-bottom: 1px solid var(--border-default);
  border-radius: 0;
  background: transparent;
}

.about-disclosure__body--credits :deep(.attrib-entry:nth-child(odd)) {
  padding-right: 1rem;
}

.about-disclosure__body--credits :deep(.attrib-entry:nth-child(even)) {
  padding-left: 1rem;
  border-left: 1px solid var(--border-default);
}

.about-disclosure__body--credits :deep(.attrib-entry:hover) {
  background: transparent;
}

.about-footer {
  padding: 1rem 1rem 1.2rem;
  background: var(--surface-sheet-solid);
  color: var(--text-faint);
  font-size: var(--text-xs-size);
  text-align: center;
}

.about-footer p {
  margin: 0;
}

.about-footer span {
  display: block;
  margin-top: 0.15rem;
}

@media (max-width: 640px) {
  :global(.sheet.sheet--about .sheet-fullscreen-btn) {
    display: none;
  }

  .about-overview {
    padding-top: 0.65rem;
  }

  .about-overview__identity {
    align-items: flex-start;
    gap: 0.65rem;
  }

  .about-logo {
    width: 3rem;
    border-radius: 0.7rem;
  }

  .about-overview h1 {
    font-size: var(--text-xl-size);
  }

  .about-tagline {
    margin-top: 0.15rem;
  }

  .about-build {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    margin-top: 0.7rem;
  }

  .about-reference {
    padding-inline: 0.75rem;
  }

  .about-disclosure > summary {
    gap: 0.6rem;
    padding-inline: 0.7rem;
  }

  .about-disclosure__body {
    padding: 0.85rem 0.8rem 1rem;
  }

  .about-disclosure__body--credits :deep(.attrib-list) {
    grid-template-columns: 1fr;
  }

  .about-disclosure__body--credits :deep(.attrib-entry:nth-child(odd)),
  .about-disclosure__body--credits :deep(.attrib-entry:nth-child(even)) {
    padding: 0.85rem 0;
    border-left: 0;
  }

  .about-disclosure__body--credits :deep(.attrib-entry-name),
  .about-disclosure__body--credits :deep(.attrib-entry-desc) {
    flex-basis: calc(100% - 1.5rem);
  }

  .about-action,
  .about-links a {
    min-height: 42px;
  }
}
</style>
