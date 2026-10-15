"""Exercise the installed native libvips/HEIC decoder with a public iOS 18 fixture."""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest

from PIL import Image


FIXTURE_PATH = Path(__file__).resolve().parent / "fixtures" / "iphone-ios18-auxiliary.heic"
FIXTURE_SHA256 = "845daac2606330ec418f02713f3fbd563a9ea9216b00af21265c11fc79411699"
VIPS_COMMAND = os.environ.get("MTL_TEST_VIPS_COMMAND", "mtl-vips-preview")
# Match ImagePreviewService's command and GeneratedImageOutput's timeout.
CONVERSION_TIMEOUT_SECONDS = 30
MAX_DIAGNOSTIC_CHARACTERS = 4096
# The original is a portrait image, 4284 x 5712 pixels.
PREVIEW_CASES = ((192, (144, 192)), (2048, (1536, 2048)), (4096, (3072, 4096)))
JPEG_START = b"\xff\xd8\xff"
JPEG_END = b"\xff\xd9"


class HeicDecoderRegressionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        # Re-encoding this fixture can remove the auxiliary references that broke
        # libheif 1.12. Keep the original bytes so this remains a regression test.
        actual_hash = hashlib.sha256(FIXTURE_PATH.read_bytes()).hexdigest()
        if actual_hash != FIXTURE_SHA256:
            raise AssertionError(f"HEIC regression fixture checksum mismatch: {actual_hash}")

    def test_ios18_auxiliary_images_decode_at_all_preview_sizes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="mtl-heic-regression-") as directory:
            for max_size, expected_dimensions in PREVIEW_CASES:
                with self.subTest(max_size=max_size):
                    output = Path(directory) / f"preview-{max_size}.jpg"
                    command = [
                        VIPS_COMMAND,
                        str(FIXTURE_PATH),
                        str(max_size),
                        "jpeg",
                    ]
                    # Use the real converter and spool stdout as the server does.
                    started = time.monotonic()
                    with output.open("wb") as stream:
                        result = subprocess.run(
                            command,
                            stdout=stream,
                            stderr=subprocess.PIPE,
                            timeout=CONVERSION_TIMEOUT_SECONDS,
                            check=False,
                        )
                    elapsed = time.monotonic() - started
                    diagnostic = result.stderr.decode("utf-8", errors="replace")
                    self.assertEqual(
                        result.returncode,
                        0,
                        f"HEIC conversion failed: {diagnostic[-MAX_DIAGNOSTIC_CHARACTERS:]}",
                    )
                    payload = output.read_bytes()
                    self.assertTrue(payload.startswith(JPEG_START), "Missing JPEG header")
                    self.assertTrue(payload.endswith(JPEG_END), "Missing JPEG end marker")
                    # An independent decoder must read all pixels, not just headers.
                    with Image.open(output) as preview:
                        self.assertEqual(preview.format, "JPEG")
                        preview.load()
                        self.assertEqual(preview.size, expected_dimensions)
                        extrema = preview.convert("RGB").getextrema()
                        self.assertTrue(
                            any(high > low for low, high in extrema),
                            "HEIC preview must not be a blank image",
                        )
                    print(
                        f"PASS: iOS 18 HEIC at {max_size}px -> "
                        f"{expected_dimensions[0]}x{expected_dimensions[1]} JPEG, "
                        f"{len(payload)} bytes in {elapsed:.3f}s via libvips",
                        flush=True,
                    )


if __name__ == "__main__":
    unittest.main(verbosity=2)
