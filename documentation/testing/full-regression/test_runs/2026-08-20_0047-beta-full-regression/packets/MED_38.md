# Packet: MED_38

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md)
- Coverage ID or run packet: MED_38
- In scope: MOV browser capability branch, original native playback or compatible-stream fallback, poster/original download, and 200/206/416 serving behavior.
- Out of scope: Location provenance covered by MED_39.

## Prerequisites

- Required previous coverage IDs or run packets: MED_36 generated-video fixture and MED_37 native MP4 comparison.
- Required app/data state: Indexed MOV media 400000 and unchanged manifest/source file.
- Required browser context: Authenticated desktop browser.

## Allowed Mutations

- Allowed: Ephemeral playback position, viewer state, HTTP downloads, and temporary response files.
- Not allowed: Source media, metadata, database, or manifest mutation.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| MED_38 | Opened the MOV, attempted the exact capability probe, exercised the actual native branch through full duration and pause/resume, attempted seek, then verified poster/download/checksum and authenticated 200/206/416 responses. | Record exact canPlayType; if native, original play/pause/seek/resume works; otherwise compatible conversion/HLS states work; poster, checksum, and serving contract pass either way. | Original MOV decoded natively, played through 2.000 s, paused/resumed, retained its poster/download, matched checksum, and returned correct 200/206/416 responses. The controlled browser does not expose canPlayType or the native shadow-DOM seek control, so those two required observations are blocked. | BLOCKED | [assets/MED_38-mov-playback.txt](../assets/MED_38-mov-playback.txt); [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) |

## Issues

No product finding. The terminal status is caused by controlled-browser capability constraints.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/MED_38-mov-playback.txt](../assets/MED_38-mov-playback.txt) | Actual native playback, capability/seek constraint, serving headers, checksum, and cleanup. |
| [assets/DAT_08-media-manifest.json](../assets/DAT_08-media-manifest.json) | Frozen MOV checksum, codec, duration, and branch expectation. |

## Screenshot Evidence

Live desktop screenshot inspection confirmed native MOV frames/controls, a paused frame, Details/location, poster, filmstrip, and Download original. ACC_04 prevents durable local screenshot saving.

## Timings

| Step | Timing |
|---|---:|
| Native MOV metadata availability | Immediate, readyState 4 |
| Full native playback | 2.000 s |
| Pause/resume sample | 0.631 -> 0.863 s |

## Handoff Notes

- Completed: Actual native MOV branch, full play, pause/resume, poster, download, 200/206/416, checksum, and temporary-file cleanup.
- Remaining unfinished coverage: None for MED_38.
- Blocked or not applicable: Exact canPlayType return and native seek control are inaccessible in the controlled browser; fallback/HLS branch is not applicable because original playback succeeds.
- State left for the next packet: MOV viewer open; eight-item fixture unchanged for MED_39 location-provenance checks.
