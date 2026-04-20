<!--
  SPDX-License-Identifier: AGPL-3.0-or-later
  Copyright (C) 2020-2026 Patrick Heusser and MyTrailLog contributors.
-->
<template>
  <div class="about-page">
    <header class="about-header">
      <h1>About MyTrailLog</h1>
      <p class="tagline">Self-hosted GPS track &amp; trail log.</p>
    </header>

    <section class="about-card">
      <h2>Build</h2>
      <dl>
        <dt>Version</dt><dd>{{ version }}</dd>
        <dt>Build</dt><dd>{{ buildInfo }}</dd>
      </dl>
    </section>

    <section class="about-card">
      <h2>License</h2>
      <p>
        MyTrailLog is free software, dual-licensed under the
        <strong>GNU Affero General Public License, version 3 or later</strong>
        (AGPL-3.0-or-later) and a separate commercial license.
      </p>
      <p>
        This program is distributed in the hope that it will be useful, but
        <strong>WITHOUT ANY WARRANTY</strong>; without even the implied warranty
        of <em>MERCHANTABILITY</em> or <em>FITNESS FOR A PARTICULAR PURPOSE</em>.
        See the GNU AGPL for more details.
      </p>
      <p>
        <strong>Network use is distribution.</strong> If you modify this program
        and make it available to users over a network, you must offer those
        users access to the complete corresponding source code of your modified
        version under the AGPL.
      </p>
      <p>
        You can obtain the complete corresponding source code of this running
        instance at:
        <a :href="sourceUrl" target="_blank" rel="noopener">{{ sourceUrl }}</a>
      </p>
      <p class="license-actions">
        <a href="https://www.gnu.org/licenses/agpl-3.0.html" target="_blank" rel="noopener">Read AGPL-3.0</a>
        &middot;
        <a :href="sourceUrl + '/blob/main/LICENSE'" target="_blank" rel="noopener">LICENSE</a>
        &middot;
        <a :href="sourceUrl + '/blob/main/NOTICE'" target="_blank" rel="noopener">NOTICE</a>
        &middot;
        <a :href="sourceUrl + '/blob/main/THIRD_PARTY_LICENSES.md'" target="_blank" rel="noopener">Third-party licenses</a>
      </p>
    </section>

    <section class="about-card">
      <h2>Commercial use</h2>
      <p>
        Using MyTrailLog for <strong>personal, private, or home</strong> purposes
        is always free.
      </p>
      <p>
        If you want to offer MyTrailLog as a hosted service to third parties,
        embed it in a proprietary product, or otherwise use it under terms
        different from the AGPL, a <strong>commercial license</strong> is
        available.
      </p>
      <p>
        Commercial inquiries:
        <a :href="'mailto:' + contactEmail">{{ contactEmail }}</a>
        &middot;
        <a :href="sourceUrl + '/blob/main/COMMERCIAL-LICENSE.md'" target="_blank" rel="noopener">COMMERCIAL-LICENSE.md</a>
      </p>
    </section>

    <section class="about-card">
      <h2>Contribute</h2>
      <p>
        Bug reports, patches and improvements are welcome. All contributors
        must sign the project CLA so the dual-licensing model can continue.
      </p>
      <p>
        <a :href="sourceUrl + '/blob/main/CONTRIBUTING.md'" target="_blank" rel="noopener">CONTRIBUTING.md</a>
        &middot;
        <a :href="sourceUrl + '/blob/main/CLA.md'" target="_blank" rel="noopener">CLA</a>
        &middot;
        <a :href="sourceUrl + '/blob/main/CODE_OF_CONDUCT.md'" target="_blank" rel="noopener">Code of Conduct</a>
        &middot;
        <a :href="sourceUrl + '/blob/main/SECURITY.md'" target="_blank" rel="noopener">Security</a>
      </p>
    </section>

    <section class="about-card">
      <h2>Trademark</h2>
      <p>
        &ldquo;MyTrailLog&rdquo; and the MyTrailLog logo are trademarks of
        Patrick Heusser. The open source license does not grant trademark
        rights; forks must use a different name and logo.
        See <a :href="sourceUrl + '/blob/main/TRADEMARK.md'" target="_blank" rel="noopener">TRADEMARK.md</a>.
      </p>
    </section>

    <section class="about-card disclaimer">
      <h2>Disclaimer</h2>
      <p>
        MyTrailLog is <strong>not a safety-critical navigation system</strong>.
        Do not rely on it as the sole means of navigation in the backcountry,
        at sea, in the air, or in any situation where inaccuracy could cause
        injury or loss of life. Always carry a map, a compass, and a working
        brain.
      </p>
      <p>
        <a :href="sourceUrl + '/blob/main/DISCLAIMER.md'" target="_blank" rel="noopener">Full disclaimer &amp; limitation of liability</a>
      </p>
    </section>

    <footer class="about-footer">
      <p>
        Copyright &copy; 2020-2026 Patrick Heusser and MyTrailLog contributors.
        Licensed under AGPL-3.0-or-later.
      </p>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

// Values are injected at build time via Vite env (see vite.config.ts).
// Fallbacks keep the view working in dev.
const version = computed<string>(
  () => (import.meta.env.VITE_APP_VERSION as string) || 'dev'
);
const buildInfo = computed<string>(
  () => (import.meta.env.VITE_APP_BUILD as string) || 'local build'
);
const sourceUrl = 'https://github.com/mindalyze-com/mtl-explorer';
const contactEmail = 'hey.lueg@gmail.com';
</script>

<style scoped>
.about-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 2rem 1.25rem 4rem;
  font-family: system-ui, -apple-system, Segoe UI, Roboto, sans-serif;
  line-height: 1.55;
  color: #1f2933;
}

.about-header {
  margin-bottom: 2rem;
  text-align: center;
}

.about-header h1 {
  margin: 0 0 0.25rem;
  font-size: 2rem;
}

.tagline {
  margin: 0;
  color: #52606d;
}

.about-card {
  background: #ffffff;
  border: 1px solid #e4e7eb;
  border-radius: 12px;
  padding: 1.25rem 1.5rem;
  margin-bottom: 1rem;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}

.about-card h2 {
  margin: 0 0 0.5rem;
  font-size: 1.1rem;
  color: #102a43;
}

.about-card p {
  margin: 0.4rem 0;
}

.about-card a {
  color: #0b6bcb;
  text-decoration: none;
}

.about-card a:hover {
  text-decoration: underline;
}

.about-card dl {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 0.25rem 1rem;
  margin: 0;
}

.about-card dt {
  color: #52606d;
  font-weight: 500;
}

.about-card dd {
  margin: 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  word-break: break-all;
}

.license-actions {
  font-size: 0.9rem;
  color: #52606d;
}

.about-card.disclaimer {
  background: #fff8e6;
  border-color: #f7c948;
}

.about-footer {
  text-align: center;
  margin-top: 2rem;
  font-size: 0.85rem;
  color: #7b8794;
}

@media (prefers-color-scheme: dark) {
  .about-page { color: #e4e7eb; }
  .about-card { background: #1f2933; border-color: #323f4b; }
  .about-card h2 { color: #ffffff; }
  .about-card dt { color: #9aa5b1; }
  .about-card a { color: #7cb8ff; }
  .about-card.disclaimer { background: #3d2f00; border-color: #8a6d00; }
  .tagline, .about-footer { color: #9aa5b1; }
}
</style>