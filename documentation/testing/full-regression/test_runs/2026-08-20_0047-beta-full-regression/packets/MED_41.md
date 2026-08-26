# Packet: MED_41

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_41
- In scope: Disposable archival-codec/PCM MOV, the conditional naturally unsupported-source harness, full 480p/720p compatible flow, active/retained reuse, quality switching, original preservation, and bounded retained state.
- Out of scope: Explicit caps, expiry, disconnect grace, runtime failure, and startup orphan cleanup covered by MED_42.

## Prerequisites

- Required previous coverage IDs or run packets: MED_36-40.
- Required app/data state: Eight-item baseline plus two temporary indexed video sources; empty work queues.
- Required browser context: Authenticated production UI and temporary local production-player harness permitted by `video-transcoding/README.md` when the browser plays the archival source natively.

## Allowed Mutations

- Allowed: Generate/index disposable non-private video fixtures, create compatible sessions, use a temporary untracked local harness without simulated media errors, reload/reopen, and switch quality.
- Not allowed: Commit fixtures/harness, alter the frozen queue, mutate original video bytes, or bypass the production player/session API.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_41 | Generated and indexed an 8 s FFV1/PCM MOV, confirmed native playback, followed the documented conditional harness path with a naturally unsupported AVI, exercised 480p and 720p creation/reuse/reopen/playback, switched quality, and checked retained files/processes/checksums. | Both 480p and 720p compatible streams play; reopening same revision/quality reuses active or retained work; switching quality starts only the selected profile; originals remain intact and retained work is bounded. | Fixed locally: exact server P480/P720 encodes are valid H.264/AAC. P720 played at 1280x720 with readyState 4 and no MediaError on desktop; mobile reused the retained session and also advanced with no error. | FIXED | [original](../assets/MED_41-compatible-flow.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt); [desktop](../assets/MTL-FR-019-fix-local-desktop.webp); [mobile](../assets/MTL-FR-019-fix-local-mobile.webp) |

## Issues

| ID | Severity | Summary | Reproduction | Expected | Actual | Evidence | Finding status | Release impact |
|---|---|---|---|---|---|---|---|---|
| MTL-FR-019 | P2 | Generated 720p compatible stream reaches ready metadata but fails when playback starts | On a naturally unsupported source, create 720p from indexed archival media, wait for completion, then press the visible Play control; repeat from a second/reopened view using the retained session. | The 1280x720 H.264/AAC HLS stream plays through like the 480p profile. | Fixed locally: hls.js owns attached media errors and receives two bounded recoveries; natural desktop/mobile P720 playback advanced without a MediaError. | [original](../assets/MED_41-compatible-flow.txt); [local retest](../assets/MTL-FR-005-021-fix-local.txt) | FIXED | Resolved in the local worktree; remote beta still needs a later build. |

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_41-compatible-flow.txt](../assets/MED_41-compatible-flow.txt) | Fixture identity, natural fallback path, 480p playback, 720p reuse/failure, retained bytes/processes, and original checksums. |

## Screenshot Evidence

![Desktop compatible P720 playback](../assets/MTL-FR-019-fix-local-desktop.webp)

![Mobile compatible P720 playback](../assets/MTL-FR-019-fix-local-mobile.webp)

## Fix Record

- Root cause: the native video error handler destroyed active hls.js before its media-error recovery ran; the encode profile was valid.
- Implementation: hls.js owns errors while attached and gets two bounded media recovery attempts.
- Full client suite 757/757, full server suite 516/516, 12/12 transcode service tests, and natural desktop/mobile P720 playback pass. See [local evidence](../assets/MTL-FR-005-021-fix-local.txt).

## Timings

| Step | Timing |
|---|---:|
| P480 transcode | 1.136 s |
| P720 transcode | 1.533 s |
| Compatible playback duration | 8.083332 s |

## Handoff Notes

- Completed: Disposable archival and unsupported sources, conditional production-player harness, 480p/720p creation, active/retained reuse, quality switch, playback, retained-state/process checks, and checksums.
- Remaining unfinished coverage: None for MED_41.
- Blocked or not applicable: Durable screenshots remain blocked by ACC_04.
- State left for the next packet: One retained P720 session (2,476,475 bytes), zero ffmpeg processes, and both temporary indexed sources remain available for MED_42 limits/cleanup testing.
