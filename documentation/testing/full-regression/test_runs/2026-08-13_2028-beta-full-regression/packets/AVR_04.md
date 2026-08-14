# Packet: AVR_04

## Scope

- Coverage source: [coverage-plan.md](../coverage-plan.md).
- Coverage ID or run packet: AVR_04.
- In scope: virtual-race marker/trail geographic sanity.
- Out of scope: A-B extraction metrics, already recorded under MCT_05.

## Prerequisites

- Required previous coverage IDs or run packets: AVR_03.
- Required app/data state: source GPX and NMEA racing on measured A-A segment.
- Required browser context: desktop race map.

## Allowed Mutations

- Allowed: inspect racers during playback and finish.
- Not allowed: change zones or track data.

## Actions And Results

| Coverage ID | Action | Expected result | Actual result | Status | Evidence |
|---|---|---|---|---|---|
| AVR_04 | Inspected both racer markers and trails during sampled progress and at finish. | Racers stay on actual segment geometry with no world-scale, `[0,0]`, or off-continent regression. | At 30 m scale both markers and colored trails stayed within the same Lannion neighborhood throughout; no global jump or remote geometry appeared. | PASS | [local race](../assets/AVR_04-local-race.webp), [bounds observations](../assets/AVR_04-local-race.txt) |

## Issues

No issue found.

## Evidence Files

| File | Purpose |
|---|---|
| [assets/AVR_04-local-race.webp](../assets/AVR_04-local-race.webp) | Two markers and trails within the local 30 m-scale race map. |
| [assets/AVR_04-local-race.txt](../assets/AVR_04-local-race.txt) | Local landmarks and excluded global failure modes. |

## Screenshot Evidence

![Local virtual-race geometry](../assets/AVR_04-local-race.webp)

## Timings

| Step | Timing |
|---|---:|
| Race observation | Full 12 s configured playback |

## Handoff Notes

- Completed: AVR_04 is terminal `PASS`.
- Remaining unfinished coverage: MED_01 onward.
- Blocked or not applicable: none in this packet.
- State left for the next packet: normal map; Filter sheet may still be present and can be closed before media work.
