# Indexer regression cases

Use this checklist for changes to file discovery, indexing, removal, restart recovery, or indexer progress. Run it against disposable GPS and media roots with a fresh database and fully synthetic files. Keep GPS and media results separate; shared indexer behavior can still differ at the watcher, importer, or domain-data layers.

For the core cases, test live changes while the server runs and offline changes followed by startup catch-up. Use a separate database and roots for each mode. Do not use a manual rescan to turn a missed automatic event into a pass.

## Core cases

| ID | Case | Expected result |
| --- | --- | --- |
| IDX-01 | Add files while the server is running. | Each file is durably scheduled, processed, and represented once in domain data. |
| IDX-02 | Remove indexed files while the server is running. | Derived records are cleaned up and the index record reaches its removed state. |
| IDX-03 | Change an indexed file while the server is running. | The latest revision is imported; stale derived data and duplicate records are absent. |
| IDX-04 | Add a batch, then forcibly stop the server with discovered work pending. Restart with the same database and roots. Repeat with a stop during processing. | Pending work resumes and settles once, without lost or duplicate domain data. Record the stop point and persisted state. |
| IDX-05 | Finish indexing, stop the server, change files, and restart. | Startup discovery catches each offline add, removal, and modification without a manual rescan. |

Run IDX-01 through IDX-05 for both GPS and media. Keep the offline mutations in separate stop/change/restart phases so later changes cannot hide an earlier miss.

## Additional cases

| ID | Case | Expected result |
| --- | --- | --- |
| IDX-06 | Rescan an unchanged tree. | No unnecessary reprocessing, duplicate records, or status churn. |
| IDX-07 | Add a burst of files in nested folders, including equal names in different folders and paths with spaces or Unicode. | Every included path is handled once and remains distinct. |
| IDX-08 | Rename or move files into the watched tree; atomically replace an existing path with a temporary file. | New paths are indexed and old or replaced content is cleaned up without duplicates. |
| IDX-09 | Change a file repeatedly while its first import is active. | The latest revision is processed after the active operation, with no stale completion overwriting it. |
| IDX-10 | Remove a file while it is being indexed. | No stale import remains after cleanup settles. |
| IDX-11 | Remove and recreate a file at the same path before cleanup completes. | An older delete cannot remove data for the restored source. |
| IDX-12 | Copy a file slowly so discovery may happen before the copy finishes. | Partial content is not accepted as the final indexed revision; processing recovers when the copy completes. |
| IDX-13 | Make the source root unavailable or unreadable, then restore it and rescan. | An incomplete scan does not infer mass deletion; indexing recovers when the root is available. |
| IDX-14 | Mutate a mounted folder from the host and from inside the server's filesystem environment. | Record which operations deliver watcher events for that mount. A manual rescan is a separate fallback result, not a live-watch pass. |
| IDX-15 | Rewrite a file in place with the same size and restore its original modification time. | Record whether the current source-revision contract detects it; document any limitation rather than counting it as ordinary modification coverage. |
| IDX-16 | Add malformed, empty, unsupported, or temporarily unreadable files, then correct a failed file. | Failures are visible, do not block unrelated files, and corrected files can be retried. |
| IDX-17 | Exercise excluded paths, excluded extensions, and symlinks if they occur in supported deployments. | Excluded or unsupported paths do not create unintended imports or recursive scans. |
| IDX-18 | Cause temporary discovery or completion persistence failures; stop between discovery, import, and completion persistence where practical. | Work is not dispatched before discovery is durable; committed work remains recoverable and visible. |
| IDX-19 | Build a large backlog behind a slow import and inspect status through the admin UI. | Counts include the full backlog, discovery and processing are distinguishable, and progress settles correctly. |
| IDX-20 | Verify the client after new data is indexed or removed. | Freshness notification and reload/reconnect show the settled server state. |

## Evidence

For every case, record the index (GPS or media), detection mode, source paths, mutation and settlement times, index status, invocation or generation information when available, domain-data counts, and relevant logs. Capture the admin UI when status or freshness behavior is part of the assertion. Use synthetic files only; keep generated files, database state, and other bulky run data outside the repository.

For an end-to-end run, follow the repository's `mtl-test-indexer` skill. Keep dated results and screenshots in private verification evidence; this page is the reusable case catalog.
