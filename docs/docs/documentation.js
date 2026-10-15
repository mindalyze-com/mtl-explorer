/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors. */
(() => {
  const menu = document.getElementById('doc-menu');
  const query = document.getElementById('doc-query');
  const results = document.getElementById('doc-results');
  const MAX_RESULTS = 8;
  let indexPromise;
  let generation = 0;
  const titleMatches = (page, words) => words.every((word) =>
    page.title.toLowerCase().includes(word) || page.title.toLowerCase().replace(/[\s-]+/g, '').includes(word)
  );
  if (menu) {
    const desktop = window.matchMedia('(min-width: 960px)');
    const setNavigation = (isDesktop) => {
      menu.open = isDesktop;
      document.querySelectorAll('.doc-contents').forEach((contents) => { contents.open = isDesktop; });
    };
    setNavigation(desktop.matches);
    desktop.addEventListener('change', (event) => { setNavigation(event.matches); });
    menu.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && !desktop.matches && menu.open) {
        menu.open = false;
        menu.querySelector('summary').focus();
      }
    });
  }
  if (!query || !results) return;

  query.closest('form').addEventListener('submit', (event) => event.preventDefault());
  query.addEventListener('input', async () => {
    const request = ++generation;
    const words = query.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
    results.replaceChildren();
    results.hidden = words.length === 0;
    if (!words.length) return;
    try {
      indexPromise ??= fetch(query.dataset.index).then((response) => {
        if (!response.ok) throw new Error(`Search index returned ${response.status}`);
        return response.json();
      });
      const index = await indexPromise;
      if (generation !== request) return;
      const matches = index
        .filter((page) => titleMatches(page, words) || words.every((word) => `${page.title} ${page.text} ${page.url}`.toLowerCase().includes(word)))
        .sort((left, right) => Number(titleMatches(right, words)) - Number(titleMatches(left, words)))
        .slice(0, MAX_RESULTS);
      if (!matches.length) results.textContent = 'No matching pages. Try another word or browse the topics.';
      for (const page of matches) {
        const link = document.createElement('a');
        link.href = page.url;
        link.textContent = page.title;
        const scope = document.createElement('small');
        scope.textContent = page.scope;
        link.append(scope);
        results.append(link);
      }
    } catch (error) {
      console.warn('[documentation] search index could not load', error);
      indexPromise = undefined;
      if (generation === request) results.textContent = 'Search is unavailable. Browse the topics below.';
    }
  });
})();
