/* SPDX-License-Identifier: AGPL-3.0-or-later
 * Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors. */
(() => {
  const CONFIG_TIMEOUT_MS = 5000;
  const TRACKER_ELEMENT_ID = "mtl-umami-analytics";
  const script = document.currentScript;
  if (!script) return;
  const configUrl = new URL("../api/analytics/umami-config", script.src);

  function isDoNotTrackEnabled() {
    return [
      window.doNotTrack,
      navigator.doNotTrack,
      navigator.msDoNotTrack,
    ].some((value) => [1, "1", "yes"].includes(value));
  }

  async function loadAnalytics() {
    if (isDoNotTrackEnabled()) return;
    try {
      const response = await fetch(configUrl.href, {
        cache: "no-store",
        credentials: "omit",
        signal: AbortSignal.timeout(CONFIG_TIMEOUT_MS),
      });
      if (!response.ok) return;
      const config = await response.json();
      if (config?.enabled !== true || !config.scriptUrl || !config.websiteId)
        return;
      const trackerUrl = new URL(config.scriptUrl);
      if (
        trackerUrl.protocol !== "https:" ||
        trackerUrl.username ||
        trackerUrl.password ||
        trackerUrl.search ||
        trackerUrl.hash
      )
        return;
      if (isDoNotTrackEnabled() || document.getElementById(TRACKER_ELEMENT_ID))
        return;

      const tracker = document.createElement("script");
      tracker.id = TRACKER_ELEMENT_ID;
      tracker.async = true;
      tracker.src = trackerUrl.href;
      tracker.dataset.websiteId = config.websiteId;
      tracker.dataset.domains = window.location.hostname;
      tracker.dataset.doNotTrack = "true";
      tracker.dataset.excludeSearch = "true";
      tracker.dataset.excludeHash = "true";
      document.head.appendChild(tracker);
    } catch {
      // Missing settings, offline use or blocked analytics must not interrupt the guide.
    }
  }

  if (document.readyState === "complete") {
    void loadAnalytics();
  } else {
    window.addEventListener("load", () => void loadAnalytics(), { once: true });
  }
})();
