# User documentation style

Applies to the README, the website, and the installation, administration, and
user guide pages, which also become the guide inside the app. It also applies to
text in the app itself: help panels, info icons, and status messages. Keep their
wording consistent with the guide, and use the same names as the menus.

## The test

Keep a sentence only if it helps the reader do something, understand a result,
or decide whether MTL Explorer is right for them. Drop details the reader
already sees on screen or never needs.

Background and story pages, such as
[Why I built MTL Explorer](../../project/background.md), are exempt: they may be
personal and narrative, but still in plain language.

## Write

- Write for someone installing or using MTL Explorer, not a developer.
- Address the reader as "you". Use active verbs and short sentences.
- Start with the goal, then the steps, then what the reader will see.
- Number steps. Bold UI labels exactly as shown: **Settings → Processing**.
- Use plain words: "folder", not "mount"; "service", not "sidecar".
  Explain a technical term the first time it appears.
- Put warnings first and in bold.
- Link to details instead of repeating them.

## Leave out

| Content | Where it belongs |
| --- | --- |
| Panel sizes, layout, animation, scrolling, focus | Nowhere; users see it |
| Edge cases, internal states, retries, timing | [Architecture](../../architecture/README.md) |
| Server internals: memory tuning, database, API fields | [Architecture](../../architecture/README.md) or [Development](../README.md) |
| What changed, was removed, or was renamed | [Release notes](../../release-notes/README.md) |
| Developer and test-only steps | [Development](../README.md) |

## Page shape

1. Title names the task: "Import GPS files".
2. One sentence on what it does for the reader.
3. Steps.
4. Optional: "If something goes wrong".

## Examples

Before: "Opening a photo or video keeps the GPS panel at its current size
underneath. Outside map exploration, the viewer keeps its own panel sizes…"
After: delete it; the user sees this.

Before: "Allow the images to download and the database to initialize. Startup
time depends on the host, network, and existing service data."
After: "The first start downloads the images and sets up the database. This
can take several minutes."
