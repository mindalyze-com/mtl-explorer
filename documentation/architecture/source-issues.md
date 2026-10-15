# Source issues and acknowledgements

GPS diagnosis and media ingest publish issues through `SourceIssueRepository`.
`source_issue` identifies a semantic defect by source, content revision, and
finding hash. Readable revisions use streamed SHA-256 of original contents.
Unreadable episodes persist one UUID until reading succeeds. File size,
modification time, message wording, retries, and rounded measurements are not
acknowledgement identities. Domain severity escalation changes the finding hash.

`REVIEW` issues require acceptance. `INFORMATION` includes empty-file skips and
the GPS assessment-limit exception. Incomplete assessment remains explicit and
does not resolve unchecked stages. Complete stages resolve absent findings;
readable changed contents resolve the older revision. Removal resolves history
while retaining `indexed_file`; hard deletion and database reset cascade through
issues and acknowledgements.

`source_issue_acknowledgement` stores one current row per issue and canonical
authenticated account name. Duplicate inserts preserve the timestamp; Reopen
deletes the row. Login case variants share a key. Account renaming starts a new
scope. Shared demo credentials share acknowledgements; browser cleanup does not
clear them. `track_review_decision` stores shared map/statistics choices and their
actor separately. Review later stores nothing. No legacy issue backfill is run.

The targets endpoint returns a complete immutable selection of issue IDs and
revisions in one read transaction. Its default state is `PENDING`;
`state=ACKNOWLEDGED` returns the complete selection for Reopen, including issues
beyond the visible page. `pendingFileCount` is zero for an acknowledged
selection. Per-file acknowledgement uses the issue
revisions displayed in Review. Bulk actions show the snapshot's file and issue
counts before submitting that selection once. One transaction validates active reviewable rows under existing
source guards and applies set-based writes in bounded batches. New, changed,
resolved, or excluded targets are not accepted by old requests. The result
includes changed/already-acknowledged/stale counts, exact `changedTargets`, and
account source summaries. Undo reopens only those newly acknowledged targets,
preserving acknowledgements another device saved before the submission.
A failure rolls back all batches. Writes are not automatically retried.

The client serializes issue actions and updates shared summaries only after a
confirmed response. Older in-flight status reads cannot restore superseded
pending counts. Feedback survives list refresh and navigation. Failed Undo keeps
its targets and a retry action. Navigation is disabled during submission;
per-file advance requires the current source to have no pending issues.
Reassessment refreshes diagnosis and its issue controls together.
Cross-device opposite actions follow database write order.
Map, Review, Processing, and overview summaries count distinct pending files,
including imported GPS quality findings. Raw processing totals and operational
failures remain separate. Acknowledgement cannot repair data or make timing
reliable. There is no browser acknowledgement token or freshness snooze.

Stable zero-byte GPS/media files finish as `SKIPPED`, distinct from configured
`EXCLUDED`. Normal content changes queue them again. Existing imported records
are retained; the current empty-source explanation identifies this. Content
failures remain reviewable; unexpected import/service failures retain operational
recovery. See [Processing](../administration/maintenance/processing.md).
