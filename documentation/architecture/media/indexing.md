# Media indexing

Media indexing reads file metadata and retains capture time, embedded location, source identity, and technical properties. The original file remains the source for downloads and content conversion.

Original contents are hashed as a stream for [common source issues](../source-issues.md).
Content failures keep account acknowledgements across unchanged retries. Empty
files finish as informational skips and retain any earlier imported records.
Import and metadata changes remain transactional; unexpected service failures
keep operational recovery separately from source acceptance.

GPS and media have separate scan/watch policies; their shipped defaults and
manual rescan actions are in [background processing](../../administration/maintenance/processing.md).
The [format registry](../../../mtl-server/src/main/java/com/x8ing/mtl/server/mtlserver/jobs/media/indexer/SupportedMediaFormat.java)
defines recognized image and video extensions. Metadata extraction and content
decoding still depend on the format and packaged tools.

Index changes enqueue correlation work. Resized previews and video streams are requested separately from metadata indexing. Content freshness and source revisions prevent obsolete content from becoming current results.

The shared GPS/media file indexer commits each directory discovery in an
independent transaction. Failed writes remain for the next scan.
Watcher events coalesce on a separate discovery thread.
Every source is committed before import dispatch. `SCHEDULED` is the durable discovered/unprocessed queue;
`PROCESSING` is committed before the consumer starts. Discovery does not wait
for imports. Progress counts include the full committed backlog, and the UI
shows discovery separately while its total is still growing.

Directory scans compare at most 256 source identities at a time using narrow
database projections. Full index entities are loaded only for state changes.
Scans retain counts rather than complete changed-file or missing-file lists.
After discovery commits, pending work is read by increasing file ID in batches
of 128; each batch finishes before the next is dispatched. The database retains
the full backlog while scan memory stays bounded. Missing sources are checked
in a separate paged pass after a complete directory walk.

Work is serialized per path through completion persistence; a stored generation
rejects superseded callbacks. Discovering another revision during active work
persists `RESCHEDULED` without replacing that consumer's source snapshot.
The start callback preserves this status; the older completion returns it to
`SCHEDULED` for the next revision instead of marking the file completed.
`lastSuccessfulGeneration` is a processing attempt number, not a timestamp;
it allows an unchanged restoration to cancel removal before cleanup starts.
File attributes are refreshed when work starts. Consumers check the source
revision before committing and roll back and queue the latest revision when it
changes. Successful completion follows the domain-data commit; independent
completion writes retry failures, and restart/rescan safely replays orphaned
work. Committed discovery survives a failed import or a lost dispatch notification.

Missing-source cleanup requires an accessible source root and confirmed absence.
Incomplete walks do not dispatch removals. Failed cleanup remains visible as
failed work; `REMOVED` records successful cleanup. Original files are read only.

Location projection, time correction, and activity correlation are distinct stored concerns. A manually assigned location does not rewrite EXIF.

See [correlation](correlation.md), [selection](filtering.md), and [delivery](delivery.md). The [media runtime](../../../docker/media/README.md) owns native packages and their checks.
