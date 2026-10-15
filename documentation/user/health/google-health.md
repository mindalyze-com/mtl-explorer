# Google Health (beta)

MTL Explorer can keep a read-only copy of your Google Health data on your own
server: exercise, sleep, activity, measurements, and heart rate. Exercise routes
are added to your tracks. This is optional; everything else works without it.

This feature is still in development, so errors and sync interruptions may
occur. Setup needs your own Google Cloud project with Google Health API access, and
Google may limit new projects. Check the
[setup requirements](../../administration/google-health.md) first.
Your Google Account must be linked to an active Google Health profile.

## Connect

Open **Settings → Import & sync → Google Health → Start setup** and follow the
[setup guide](../../administration/google-health.md). **Manage** opens the
connection settings.

## Watch the first import

Recent data arrives first. Older history follows over time, which can take a
while. Until it is done, some dates or track heart rates may still be missing.

**Settings → Processing** shows the progress, split into **Recent**,
**History**, and **Routes**. **Details** shows each data type and anything that
is waiting to be retried. Failed parts are retried automatically.

## Reconnect every seven days

In Google's Testing mode, access expires after
[seven days](https://support.google.com/cloud/answer/15549945?hl=en). Health
and Processing then show **Reauthorize Google Health**. Click it, approve access
with the same Google account, and paste the new address. Your stored data stays.

## Reload, disconnect, or delete

In **Manage → Advanced**:

- **Reload history** fetches all your history again.
- **Reload a date range** fetches only some days. The end day is not included.
- **Retry failed ranges** retries parts that kept failing.

Reloading keeps your data until new data replaces it.

**Disconnect Google** stops syncing and keeps your data. To delete the local copy,
disconnect first, then choose **Delete local mirror** and type `DELETE`. This
removes Google Health data and its routes; your other tracks and plans stay.

## How syncing works

By default, the last 24 hours are checked every 30 minutes, and the last seven
days every 24 hours. Older days are imported once. If Google changes older
data later, use **Reload history** or **Reload a date range**.

Exercise routes stay even if Google no longer has them; they are marked
**not currently mirrored**.

See [charts](charts.md), [records](records.md), and [heart rate on tracks](track-matching.md).
For technical details, see [synchronization](../../architecture/health/synchronization.md).
