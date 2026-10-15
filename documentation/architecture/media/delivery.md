# Media delivery

The browser requests bounded previews and original content separately from metadata. Server content reads preserve source freshness, cache identity, and HTTP Range support for video.

Image previews use libvips first and a logged ImageMagick fallback when the native loader cannot decode a format. Video posters and optional H.264/AAC HLS conversion use server-side media tools. Conversion work is bounded and cancellable; the original remains available.

Map collections and activity timelines load pages. The filmstrip retains a bounded thumbnail window and fetches another page at navigation boundaries. The viewer owns display state; it does not calculate activity associations.

Thumbnail buttons preserve native lazy loading and browser caching. After a native
load error, they recover through the same image fetch helper as viewer previews.
The helper allows one preview and one thumbnail request at a time, including
response-body reading. A running thumbnail does not block a cached preview.
Queued thumbnails wait until all previews finish, including HTTP 503 retry waits,
so they do not compete for the server's converter between preview attempts.
Each admitted request has a 90-second timeout covering headers and body transfer.
Queue time does not consume that timeout or the retry budget. Thumbnails allow
30 busy retries; previews allow 90, enough to outlast a thumbnail conversion.
Retry delays are between one and two seconds. A retry releases its request slot
so another preview can proceed.
Abort removes queued work and releases active requests on navigation.
Thumbnail removal, source changes, and hiding grids
while a viewer is open cancel recovery and revoke recovered blob URLs.

Exhausted or undecodable thumbnails show “No preview”. Their button still opens
media and also restarts thumbnail recovery unless the click was prevented.
Filmstrip drag suppression prevents both selection and recovery.

The [media build notes](../../../docker/media/README.md) define packaged dependencies and regression checks. See [video playback](../../user/media/video.md) for the user flow.
