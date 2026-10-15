// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { runInNewContext } from "node:vm";

const source = readFileSync(
  new URL("../../../docs/site-analytics.js", import.meta.url),
  "utf8",
);

function loadPage({
  hostname = "mtl-explorer.com",
  readyState = "complete",
  windowDnt,
  navigatorDnt,
  legacyDnt,
} = {}) {
  const scripts = [];
  const storageAccess = [];
  const listeners = [];
  const window = {
    location: { hostname },
    doNotTrack: windowDnt,
    get localStorage() {
      storageAccess.push("local");
      throw new Error("Storage unavailable");
    },
    get sessionStorage() {
      storageAccess.push("session");
      throw new Error("Storage unavailable");
    },
    addEventListener(type, callback) {
      listeners.push(callback);
    },
  };
  const document = {
    readyState,
    createElement(tag) {
      assert.equal(tag, "script");
      return { dataset: {} };
    },
    head: {
      appendChild(script) {
        scripts.push(script);
      },
    },
  };
  runInNewContext(source, {
    window,
    navigator: { doNotTrack: navigatorDnt, msDoNotTrack: legacyDnt },
    document,
  });
  return {
    scripts,
    storageAccess,
    window,
    finishLoad() {
      listeners.splice(0).forEach((callback) => callback());
    },
  };
}

test("uses default Umami identification without reading or writing browser storage", () => {
  const result = loadPage();
  assert.equal(result.scripts.length, 1);
  assert.equal(result.scripts[0].dataset.distinctId, undefined);
  assert.deepEqual(result.storageAccess, []);
});

test("production and local previews keep their configured analytics destinations and privacy flags", () => {
  for (const hostname of [
    "mtl-explorer.com",
    "localhost",
    "127.0.0.1",
    "[::1]",
  ]) {
    const tracker = loadPage({ hostname }).scripts[0];
    const production = hostname === "mtl-explorer.com";
    assert.equal(
      tracker.src,
      production
        ? "https://stats.mtl-explorer.com/script.js"
        : "https://umami.me.mindalyze.com/script.js",
    );
    assert.equal(
      tracker.dataset.websiteId,
      production
        ? "eb0877dd-d24d-4a5d-8735-eaeed78f0f38"
        : "c221b96a-f9e6-4f6a-b272-2f6649371876",
    );
    assert.equal(tracker.async, true);
    assert.equal(tracker.dataset.doNotTrack, "true");
    assert.equal(tracker.dataset.excludeSearch, "true");
    assert.equal(tracker.dataset.excludeHash, "true");
    assert.equal(tracker.dataset.domains.includes(hostname), true);
    assert.equal(tracker.dataset.distinctId, undefined);
  }
});

test("disabled hosts do not load a tracker or access storage", () => {
  for (const hostname of ["example.com", "www.mtl-explorer.com", ""]) {
    const result = loadPage({ hostname });
    assert.equal(result.scripts.length, 0);
    assert.deepEqual(result.storageAccess, []);
  }
});

test("each Do Not Track source prevents tracker loading and storage access", () => {
  for (const key of ["windowDnt", "navigatorDnt", "legacyDnt"]) {
    for (const value of [1, "1", "yes"]) {
      const result = loadPage({ windowDnt: "0", [key]: value });
      assert.equal(result.scripts.length, 0);
      assert.deepEqual(result.storageAccess, []);
    }
  }
});

test("waits until page load before attaching the tracker", () => {
  const result = loadPage({ readyState: "loading" });
  assert.equal(result.scripts.length, 0);
  result.finishLoad();
  assert.equal(result.scripts.length, 1);
  result.finishLoad();
  assert.equal(result.scripts.length, 1);
});

test("rechecks Do Not Track when page load finishes", () => {
  const result = loadPage({ readyState: "loading" });
  result.window.doNotTrack = "1";
  result.finishLoad();
  assert.equal(result.scripts.length, 0);
  assert.deepEqual(result.storageAccess, []);
});
