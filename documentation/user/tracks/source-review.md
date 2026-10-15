# Review

Some files import with problems, such as GPS jumps, missing timestamps, or
gaps. **Review** lists them so you can decide what to do.

Open **Review**, or click **Review source findings and map use** in a track's
**Quality** tab. Review has two views:

- **Issues**: files that need your attention, including files that failed to import.
- **GPS recordings**: all recordings and their review status.

## Check a file

1. In **Issues**, click **Open** on a file to see what's wrong.
2. Look at the findings. A red **!** marks findings you should check.
   Click the target beside **Center on map** or **Center point** to zoom in on
   the issue. For a jump or gap, you can center on either end or choose
   **Show transition** to see both. Use **−** to zoom out or **Show whole track**
   to return to the overview. **Inspect point** or **Inspect transition** shows
   the original GPS points around the problem. The original file is red and
   the processed track is blue.
3. If the file is fine, click **Acknowledge**. It won't be flagged again unless
   a new problem appears.

**Acknowledge & next** acknowledges the file and opens the next one.
**Acknowledge all** acknowledges every listed file after you confirm. **Undo**
appears briefly if you change your mind. **Review later** keeps the issues open.

Acknowledging only hides the notice. It does not fix the file or change how the
track counts.

Use the **Acknowledged** filter to see what you acknowledged, and **Reopen** to
review a file again. **Information** findings need no action. Acknowledgements
belong to your account and stay after you sign out.

## What Review reports

- files that can't be read or converted
- missing, repeated, or backward timestamps
- invalid positions, GPS jumps, and weak GPS signal
- recording gaps
- missing elevation
- large corrections made during import

A recording gap may be a pause or missing data; the file can't tell which.
Missing elevation is allowed, but elevation results stay empty. See
[review thresholds](../../architecture/gps/quality.md#review-thresholds) for the
exact rules.

## Choose where a track counts

You can decide whether a file appears on the map and in your statistics. Choose
for the whole file or for each numbered section:

| Choice | Map, heatmap, and animation | Statistics |
| --- | --- | --- |
| Map and statistics | Shown | Counted |
| Statistics only | Hidden | Counted |
| Map only | Shown | Not counted |
| Hidden | Hidden | Not counted |

Click **Save use settings**. This applies for everyone using this installation.
Hidden tracks are still available in Review and the track list.

Including a track does not make bad timestamps reliable: speed, duration, and
energy stay unavailable when the timing is wrong.

## Fix a file

MTL Explorer never changes your original files. To fix one, edit it yourself:

1. On the file page, open **How to fix this file**.
2. Click **Download GPX**, or **Download original source** to keep the original
   format. The page also shows where the file is stored on the server.
3. Fix the reported points, gaps, or timestamps in a GPS track editor.
4. Save the fixed file in the GPS folder:
   - For a GPX file, replace the original.
   - For other formats, move the original out of the GPS folder and save the
     fixed GPX there, so both versions aren't imported.
5. Wait for the import, then click **Reassess current file** and check the
   findings again.

Very large files (over 64 MiB) can't be fully checked. You can still download
the original.

See [source quality](../../architecture/gps/quality.md) for technical details.
