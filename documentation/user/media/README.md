# Photos and videos

1. Copy your photos and videos into the media folder, normally `data/media/`
   next to `docker-compose.yml`.
2. Run **Settings → Maintenance → Rescan Media**. Otherwise new media is only
   found at the next scheduled scan, which can take up to seven days.
3. Wait for **Settings → Processing** to finish.

Photos with GPS data appear at their location. Others are placed along a track
recorded at the same time.

Supported formats include JPEG, PNG, HEIC/HEIF, MP4, and MOV. Some files may
lack a date or location, and some videos may need conversion to play in your
browser. Keep your originals in the media folder; see
[data folders](../../administration/configuration/storage.md).

- [Browse photos and videos](browsing.md)
- [Correct camera time](capture-time.md)
- [Set a photo location](locations.md)
- [Play video](video.md)
