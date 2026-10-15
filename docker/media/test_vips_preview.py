"""Native preview behavior and the explicit unsupported-format boundary."""

import io
from pathlib import Path
import subprocess
import tempfile
import unittest

from PIL import Image

from test_heic_regression import CONVERSION_TIMEOUT_SECONDS, FIXTURE_PATH, VIPS_COMMAND


class VipsPreviewTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = tempfile.TemporaryDirectory(prefix="mtl-vips-test-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)

    def convert(self, source: Path, size: int = 192, output_format: str = "jpeg"):
        return subprocess.run([VIPS_COMMAND, str(source), str(size), output_format],
                              capture_output=True, timeout=CONVERSION_TIMEOUT_SECONDS, check=False)

    def assert_preview(self, result, dimensions, format="JPEG") -> Image.Image:
        self.assertEqual(result.returncode, 0, result.stderr.decode(errors="replace"))
        with Image.open(io.BytesIO(result.stdout)) as preview:
            preview.load()
            self.assertEqual(preview.format, format)
            self.assertEqual(preview.size, dimensions)
            return preview.copy()

    def test_rotates_jpeg_upright_without_enlarging(self) -> None:
        source = self.root / "with spaces.jpg"
        exif = Image.Exif()
        exif[274] = 6  # EXIF orientation: rotate 90 degrees clockwise.
        Image.new("RGB", (40, 20), (230, 50, 10)).save(source, exif=exif)
        for size in (0, 192, 10):
            with self.subTest(size=size):
                dimensions = (5, 10) if size == 10 else (20, 40)
                self.assert_preview(self.convert(source, size), dimensions)

    def test_png_preserves_alpha(self) -> None:
        source = self.root / "transparent.png"
        Image.new("RGBA", (40, 20), (230, 50, 10, 80)).save(source)
        preview = self.assert_preview(self.convert(source, 10, "png"), (10, 5), "PNG")
        self.assertEqual(preview.mode, "RGBA")
        self.assertEqual(preview.getextrema()[3], (80, 80))

    def test_magick_only_format_is_signaled_without_hidden_conversion(self) -> None:
        source = self.root / "fallback.bmp"
        Image.new("RGB", (40, 20), (230, 50, 10)).save(source)
        result = self.convert(source, 10)
        self.assertEqual(result.returncode, 64)
        self.assertEqual(result.stdout, b"")
        self.assertIn(b"unsupported-format", result.stderr)
        fallback = subprocess.run(["magick", str(source), "-auto-orient", "-resize", "10x10>",
                                   "-quality", "92", "jpeg:-"], capture_output=True,
                                  timeout=CONVERSION_TIMEOUT_SECONDS, check=False)
        self.assert_preview(fallback, (10, 5))

    def test_damaged_heic_is_a_decoder_failure_not_unsupported_format(self) -> None:
        source = self.root / "damaged.heic"
        source.write_bytes(FIXTURE_PATH.read_bytes()[:4096])
        result = self.convert(source)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stdout, b"")
        self.assertIn(b"libvips preview failed", result.stderr)

    def test_missing_file_is_not_unsupported_format(self) -> None:
        result = self.convert(self.root / "missing.jpg")
        self.assertEqual(result.returncode, 1)
        self.assertEqual(result.stdout, b"")
        self.assertIn(b"FileNotFoundError", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
