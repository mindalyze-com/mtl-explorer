(() => {
  const ENVIRONMENTS = [
    {
      hostnames: ["mtl-explorer.com"],
      trackerUrl: "https://stats.mtl-explorer.com/script.js",
      websiteId: "eb0877dd-d24d-4a5d-8735-eaeed78f0f38",
    },
    {
      hostnames: ["localhost", "127.0.0.1", "[::1]"],
      trackerUrl: "https://umami.me.mindalyze.com/script.js",
      websiteId: "c221b96a-f9e6-4f6a-b272-2f6649371876",
    },
  ];
  const environment = ENVIRONMENTS.find(({ hostnames }) => hostnames.includes(window.location.hostname));
  const doNotTrack = window.doNotTrack || navigator.doNotTrack || navigator.msDoNotTrack;

  if (!environment || [1, "1", "yes"].includes(doNotTrack)) {
    return;
  }

  function loadAnalytics() {
    const tracker = document.createElement("script");
    tracker.async = true;
    tracker.src = environment.trackerUrl;
    tracker.dataset.websiteId = environment.websiteId;
    tracker.dataset.domains = environment.hostnames.join(",");
    tracker.dataset.doNotTrack = "true";
    tracker.dataset.excludeSearch = "true";
    tracker.dataset.excludeHash = "true";
    document.head.appendChild(tracker);
  }

  // Analytics starts after page load. Site navigation and controls never wait for it.
  if (document.readyState === "complete") {
    loadAnalytics();
  } else {
    window.addEventListener("load", loadAnalytics, { once: true });
  }
})();
