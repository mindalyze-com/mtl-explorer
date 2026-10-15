/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors. */
(() => {
  const STORAGE_KEY = 'mtl.color-scheme';
  const applyTheme = () => {
    let scheme = 'light';
    try {
      if (localStorage.getItem(STORAGE_KEY) === 'dark') scheme = 'dark';
    } catch {
      // Match the app's light default when browser storage is unavailable.
    }
    document.documentElement.setAttribute('data-theme', scheme);
  };

  // Loaded before styles so opening a dark guide does not flash a light page.
  applyTheme();
  window.addEventListener('storage', (event) => {
    if (event.key === STORAGE_KEY || event.key === null) applyTheme();
  });
  window.addEventListener('pageshow', applyTheme);
  window.addEventListener('focus', applyTheme);
})();
