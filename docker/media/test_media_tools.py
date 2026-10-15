"""Check the packaged native tools used for MTL Explorer media previews."""

import ctypes
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from PIL import Image


MEDIA_PREFIX = Path("/opt/mtl/media")
PROCESS_TIMEOUT_SECONDS = 30
SOURCE_DURATION_SECONDS = "2"
VIDEO_THREADS = "1"
THUMBNAIL_SIZE = 160
STREAM_HEIGHT = 120
SEGMENT_SECONDS = "1"


def run_tool(*command: str) -> bytes:
    result = subprocess.run(
        command, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        timeout=PROCESS_TIMEOUT_SECONDS, check=False,
    )
    if result.returncode:
        raise AssertionError(
            f"{command[0]} exited {result.returncode}: "
            f"{result.stderr.decode('utf-8', errors='replace')[-4096:]}"
        )
    return result.stdout


class MediaToolsTest(unittest.TestCase):
    def test_packaged_libheif_version(self) -> None:
        metadata, = (MEDIA_PREFIX / "conda-meta").glob("libheif-*.json")
        expected = json.loads(metadata.read_text())["version"]
        library = ctypes.CDLL(str(MEDIA_PREFIX / "lib/libheif.so.1"))
        library.heif_get_version.restype = ctypes.c_char_p
        self.assertEqual(library.heif_get_version().decode(), expected)

    def test_hevc_thumbnail_and_h264_aac_stream(self) -> None:
        with tempfile.TemporaryDirectory(prefix="mtl-video-regression-") as directory:
            root = Path(directory)
            source = root / "synthetic.mov"
            # Synthetic phone-style HEVC input with audio; no external fixtures.
            run_tool(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin",
                "-f", "lavfi", "-i", "testsrc2=size=320x240:rate=6",
                "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000",
                "-t", SOURCE_DURATION_SECONDS, "-c:v", "libx265",
                "-x265-params", "pools=none:frame-threads=1:log-level=error",
                "-threads", VIDEO_THREADS, "-pix_fmt", "yuv420p",
                "-tag:v", "hvc1", "-c:a", "pcm_s16le", str(source),
            )
            # Match VideoThumbnailService's bounded, single-frame JPEG command.
            thumbnail = run_tool(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin",
                "-threads", VIDEO_THREADS, "-i", str(source), "-map", "0:v:0",
                "-frames:v", "1", "-filter_threads", VIDEO_THREADS,
                "-vf", f"scale=w='min({THUMBNAIL_SIZE},iw)':h='min({THUMBNAIL_SIZE},ih)'"
                ":force_original_aspect_ratio=decrease",
                "-an", "-q:v", "3", "-f", "image2pipe", "-threads", VIDEO_THREADS,
                "-vcodec", "mjpeg", "pipe:1",
            )
            with Image.open(io.BytesIO(thumbnail)) as image:
                image.load()
                self.assertEqual(image.format, "JPEG")
                self.assertEqual(image.size, (THUMBNAIL_SIZE, STREAM_HEIGHT))

            playlist = root / "index.m3u8"
            # Exercise the H.264/AAC fMP4 HLS path in VideoTranscodeSessionService.
            run_tool(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-y",
                "-threads", VIDEO_THREADS, "-i", str(source),
                "-map", "0:v:0", "-map", "0:a:0?",
                "-filter_threads", VIDEO_THREADS, "-filter_complex_threads", VIDEO_THREADS,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-vf", f"scale=w=-2:h='min({STREAM_HEIGHT},ih)'", "-pix_fmt", "yuv420p",
                "-force_key_frames", f"expr:gte(t,n_forced*{SEGMENT_SECONDS})",
                "-c:a", "aac", "-b:a", "96000", "-ac", "2", "-threads", VIDEO_THREADS,
                "-f", "hls", "-hls_time", SEGMENT_SECONDS, "-hls_playlist_type", "event",
                "-hls_segment_type", "fmp4", "-hls_fmp4_init_filename", "init.mp4",
                "-hls_segment_filename", str(root / "segment-%03d.m4s"),
                "-hls_flags", "independent_segments+temp_file", str(playlist),
            )
            self.assertIn("#EXT-X-ENDLIST", playlist.read_text())
            self.assertTrue((root / "init.mp4").is_file())
            self.assertGreaterEqual(len(list(root.glob("segment-*.m4s"))), 2)
            probe = json.loads(run_tool(
                "ffprobe", "-v", "error", "-show_streams", "-of", "json", str(playlist),
            ))
            streams = {stream["codec_type"]: stream for stream in probe["streams"]}
            self.assertEqual(streams["video"]["codec_name"], "h264")
            self.assertEqual(streams["video"]["height"], STREAM_HEIGHT)
            self.assertEqual(streams["audio"]["codec_name"], "aac")
            # Decode the completed stream so metadata alone cannot satisfy the check.
            run_tool(
                "ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-xerror",
                "-threads", VIDEO_THREADS, "-i", str(playlist), "-f", "null", "-",
            )


if __name__ == "__main__":
    unittest.main(verbosity=2)
