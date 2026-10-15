# Indexing and background work

**Settings → Processing** shows what MTL Explorer is working on: importing GPS
files and photos, and calculations such as duplicates, activity types, energy,
and exploration. Importing a file and finishing all its calculations can end at
different times.

## Read the progress

**Complete · 100%** means every file has a result. It does not mean every file
was imported: some may have failed or been skipped. Open **Technical details →
Show failed files** or **Show skipped files** to see which files and why.

When files need attention, a card at the top shows **N files need review**.
Click **Review** to check them. Fixing happens in **Review**, not in Processing;
see [Review](../../user/tracks/source-review.md).

A **Waiting** task shows what it needs before it can continue. Select the linked
task to inspect it. Automatic waits resume without your input. Open **Details**
to see which tasks it depends on.

In demos with synthetic health enabled, **Google Health (beta)** under
**Imports & sync** shows *Synthetic demo data* and its progress in days.

## Rescan files

MTL Explorer picks up new GPS files right away and rescans the GPS folder every
12 hours. Photos and videos are not watched: they are scanned when the server
starts and every seven days.

To pick up changes now, use **Settings → Maintenance → Rescan GPS** or
**Rescan Media**. A rescan also retries files that failed before. It does not
restore a database.

Advanced settings:

| Setting | Default |
| --- | --- |
| `mtl.indexer.gps.live-watch-enabled` | `true` |
| `mtl.indexer.gps.rescan-interval` | `PT12H` |
| `mtl.indexer.media.live-watch-enabled` | `false` |
| `mtl.indexer.media.rescan-interval` | `P7D` |
| `mtl.indexer.gps.conversion-timeout` | `PT10M`, time allowed to convert one non-GPX file |

## Keep your originals

MTL Explorer never changes or deletes your original files. Keep them in place:
they are needed to re-import tracks after an update. To reset the database, see
[Start with a fresh database](updates.md#start-with-a-fresh-database).

When the map shows a reload notice, open **Settings → Data status** or click
**Reload** to see the latest results. See
[GPS processing](../../architecture/gps/processing.md#indexer-queue-and-recovery)
for technical details.
