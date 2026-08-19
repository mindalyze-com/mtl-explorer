> **RESULT: PASS - the large demo workload completed locally without OOM, and live/offline indexer recovery passed.**

# Large demo memory investigation

Date: 2026-08-19

## Scope

Investigate repeated OOM kills of the large MTL Explorer demo, identify retained and transient memory consumers, implement fixes, and replay the full public demo workload locally.

The validation used the repository's public Porto taxi archive only. No private GPX tracks were copied or used. The archive SHA-256 was `29f80ec3e2e0153a9df20b92479aaf9bb6c9ba05e05afe660787823635c2671b`.

## Incident evidence

The large demo container had a 4 GiB cgroup limit, no swap, `DEMO_PHOTO_COUNT=20000`, and 15,000 GPX files in its mounted archive. Docker recorded at least three OOM kills with exit code 137.

The old JVM command used `-XX:MaxRAMPercentage=60.0`, giving ZGC a 2.4 GiB maximum heap. The first import reported its initial scan complete after 39 seconds even though domain-level GPX processing was still running. Photo generation and scheduled classifier, duplicate, and exploration work could therefore overlap the GPX import.

Immediately before an OOM:

- Java cgroup usage was already about 3.2 GiB.
- The Python photo coordinator used about 285 MiB RSS after reading 15,000 GPX files.
- `ProcessPoolExecutor` forked four workers. Each worker reached roughly 350-500 MiB RSS.
- Cgroup usage rose from about 3.20 GiB to 3.89, 4.07, and 4.26 GiB in about 17 seconds, then the kernel killed the container.
- Each worker had its own LRU cache of full 1920x1440 gradient images. Eighteen RGB palettes alone represented about 142 MiB per process before render intermediates and the inherited Python object graph.

This explains why increasing the container memory did not solve the failure: it also allowed the Java heap to expand, while the forked helpers independently multiplied native memory.

## JFR findings

A 69-second JFR was dumped from the large demo before its next OOM.

- Post-GC live heap stayed between 224 and 446 MiB; the last sample was 428 MiB.
- Direct buffers peaked below 0.4 MiB.
- The sampled container usage reached 3,102 MiB before the Python worker peak.
- Weighted Java allocations were about 10.3 GiB over 69 seconds, but most objects were reclaimed.
- The highest allocation threads were the activity classifier and four index workers.
- Dominant allocation sites were Hibernate mutation/binding state, geometry arrays, GeographicLib calculations, PostgreSQL parameters, and activity-classification matrices.

This is not evidence of a retained Java heap leak. It is high transient allocation combined with overlapping Java and Python work inside one cgroup.

## Fixes

### Demo photo generator

- Removed the four-process renderer and rendered sequentially in bounded batches.
- Replaced cached full-resolution gradients with cached one-pixel-wide strips that are expanded for each render.
- Kept atomic JPEG publication and progress/resume behavior.

### Startup coordination

- The entrypoint now waits for both GPS and media initial scans/live watchers.
- Initial-scan completion now waits for observer/domain processing, not only database scheduling.
- A marker covers the complete demo-photo generation window.
- Classifier, duplicate, and exploration schedules do not start while that marker exists.
- GPS/media pending status also prevents dependent classifier, duplicate, and media-correlation work from starting early.
- High-allocation scheduled jobs share a non-blocking guard so they do not run concurrently.

### JVM and indexer bounds

- Reduced the default maximum Java heap from 60% to 50% of the container.
- A post-replay JVM follow-up removed the 25% initial-heap floor, enabled generational ZGC on Java 21, changed the uncommit delay from 10 to 30 seconds, and enabled exit on Java heap OOM. In a 2 GiB verification container, the resulting initial and maximum heaps were 32 MiB and 1 GiB.
- Made `mtl.indexer.worker-threads` effective. The default is two workers per index instead of an implicit minimum of four.
- Moved rescan coordination to a separate single-thread executor so one configured worker cannot deadlock waiting for itself.
- Settled live files no longer wait for a redundant two-second stability observation after the existing eight-second quiet period.
- Completed per-path debounce state is removed instead of being retained indefinitely.

### Diagnostics

- Updated the container profiling notes to use bundled `jattach`, dump JFR before sending signals, and read cgroup memory/events rather than ZGC process RSS alone.

## Photo-only before/after measurement

Both runs generated the exact 20,000-photo workload under a 1 GiB container limit. Three sampled JPEGs (indices 1, 10000, and 20000) were byte-identical before and after the change.

| Implementation | Time | Peak cgroup memory | Result |
| --- | ---: | ---: | --- |
| Previous four-process renderer | 102 s | 1,070,972,928 bytes (1,021 MiB) | Completed just below the limit |
| Sequential compact-gradient renderer | 351 s | 533,827,584 bytes (509 MiB) | Completed |

The one-time bootstrap is about 3.4 times slower, but its peak cgroup memory is half the previous value. Anonymous memory fell by about 70%.

## Full local replay

Environment:

- Image: `mtl-memory-fix:verified-final` (`sha256:7be274c21aeb029b46391c7f57dfa442f47e84d09f3b5d12ce844c9854c4c71b`)
- App limit: 1,536 MiB, four CPUs
- JVM maximum heap: 768 MiB (50%)
- PostgreSQL: separate disposable container
- GPX files: 15,000
- Generated photos: 20,000

The full first pass produced these results:

- GPX import completed all 15,000 domain callbacks in 215.446 seconds (69.6 files/s).
- Photo generation started only after the GPX callback barrier completed.
- All 20,000 photos were generated in about 349 seconds.
- Classifier and duplicate detection started only after photo generation completed and the marker was removed.
- Docker reported no OOM kill and no restart.
- Peak cgroup memory reached the 1,536 MiB test limit; there were 186 `memory.events:max` events but no OOM event.

The 623-second local JFR contained 262 garbage collections:

- Post-GC live heap: 64-300 MiB; last sample 160 MiB.
- Direct buffers: maximum 0.58 MiB.
- Sampled container usage: maximum 1,476 MiB.
- The heap repeatedly returned to a small live set, again rejecting a retained-heap leak.

On the updated-image recovery start, the indexer recovered 19,994 photos left unprocessed by the earlier live backlog in 17.457 seconds (1,145.3 files/s). The database settled at 15,000 GPS tracks, 20,000 media rows, zero pending files, and zero failed files. The app again had no OOM or restart. It reached the 1,536 MiB limit and recorded 323 `memory.events:max` events, so 1.5 GiB is a stress-test limit rather than a recommended production allocation.

## Two-pass indexer regression

GUI behavior was outside this memory/indexer scope. Evidence came from logs, filesystem state, cgroup counters, and direct database status/domain counts.

### Pass 1: live watch

After the updated image settled, one copied public GPX and one copied generated JPEG were created inside the running container.

- Both `CREATE` events were observed.
- Both domain imports completed after the debounce period.
- Counts changed to 15,001 GPS tracks and 20,001 media rows.
- Pending and failed indexed-file counts were zero.

Host-side file creation did not produce inotify events through the local OrbStack bind mount, so the live mutation was executed inside the container. This is a local virtualization limitation, not an application failure.

### Pass 2: offline catch-up

The app was stopped cleanly. The two live probe files were removed and different public/synthetic probe files were added while it was offline. After restart:

- GPS scan found one new and one missing file.
- Media scan found one new and one missing file.
- Both removed indexed-file rows reached `REMOVED`, and both old domain rows were deleted.
- Both added indexed-file rows reached `COMPLETED_WITH_SUCCESS`, and both domain rows were created.
- Final counts were 15,001 GPS tracks and 20,001 media rows, with zero pending and zero failed files.
- Startup catch-up completed in 1.070 seconds for GPS and 0.867 seconds for media.
- Peak cgroup memory was 1,311 MiB with zero `memory.events:max` and zero OOM events.

## Automated checks

- Focused Java regression: 13 tests passed, zero failures/errors.
- Full server suite against fresh disposable PostgreSQL: 501 tests passed, zero failures/errors, one skipped.
- Application context: passed.
- Python demo-photo tests: six passed.
- Production Docker reactor build: passed.
- Follow-up JVM-settings image build: passed (`sha256:e385688b0d218669704a4435eda16ca46897c6359ba824b0522cc8b9aa45d0cd`); all configured JVM flags were active at runtime.
- Shell syntax, Python compilation, and `git diff --check`: passed.

An earlier full-suite attempt used the already-mutated replay database and failed one fixture-count assertion (`expected 1`, `was 2`). The same suite passed on a fresh database; this was test-state contamination, not a product regression.

## Capacity conclusion

One GiB is not a safe limit for this exact combined workload. The fixed photo generator alone fits in about 509 MiB, but Java import allocation, ZGC native mappings, metaspace, threads, PostgreSQL/native buffers, and the Python coordinator share the same cgroup. The full replay survived 1.5 GiB but touched that limit.

Use at least 2 GiB for the large demo after these fixes. The existing 4 GiB allocation provides additional headroom and should no longer be consumed by four replicated render processes. Recheck cgroup `memory.current`, `memory.peak`, and `memory.events` after deployment; process RSS is not authoritative with ZGC.

The fixes were verified locally only. This investigation did not deploy or change the remote demo instance.
