/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors. */
(() => {
  const back = document.querySelector('[data-guide-back]');
  if (!back) return;

  const appUrl = new URL(back.href);
  const guidePath = `${appUrl.pathname}guide/`;

  // Keep outside pages separate from the guide's connection to the app tab.
  document.querySelectorAll('a[href]').forEach((link) => {
    if (link === back) return;
    const url = new URL(link.href);
    if (url.origin !== appUrl.origin || !url.pathname.startsWith(guidePath)) {
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
    }
  });

  back.addEventListener('click', (event) => {
    if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    const app = window.opener;
    if (!app || app.closed) return;

    try {
      const url = new URL(app.location.href);
      if (url.origin !== appUrl.origin || !url.pathname.startsWith(appUrl.pathname) || url.pathname.startsWith(guidePath)) return;
      // Return to the running app, preserving its map, tools, and draft state.
      app.focus();
    } catch {
      // An opener that left this origin cannot be reused; follow the app link.
      return;
    }
    event.preventDefault();
    window.close();
  });
})();
