# Import GPS files

There are two ways to add GPS files:

- **Upload** them in **Settings → Import & sync**.
- **Copy** them into the GPS folder on the server. In a standard installation,
  this is `data/gpx/` next to `docker-compose.yml`. New and changed files are
  picked up automatically, even if the server was off when you copied them.

Then:

1. Start with a few files.
2. Watch **Settings → Processing** for progress and errors.
3. If files need attention, open **Review**.
4. When the map asks you to reload, click **Reload**.

Some calculations, like energy or exploration, can finish a bit later than the
import itself.

Keep your original files in the GPS folder. MTL Explorer needs them to re-import
tracks and for downloads. To fix a broken file, see [Review](../tracks/source-review.md).
