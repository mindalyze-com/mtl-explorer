#!/usr/bin/env python3
"""MTL Explorer native image previews; exit 64 means no native format loader."""

import argparse
import ctypes as c
import os
from pathlib import Path
import sys


UNSUPPORTED_FORMAT_EXIT = 64  # Shared with ImagePreviewService.
VIPS_SIZE_DOWN = 2
VIPS_FAIL_ON_ERROR = 2
JPEG_QUALITY = 92
MEDIA_LIBRARY_DIRECTORY = Path("/opt/mtl/media/lib")


def convert(path: Path, max_size: int, output_format: str) -> int:
    # Use the installed C ABI directly: no extra Python binding or child process.
    # The server can terminate this single process and bound its lifetime.
    vips = c.CDLL(str(MEDIA_LIBRARY_DIRECTORY / "libvips.so.42"))
    gobject = c.CDLL(str(MEDIA_LIBRARY_DIRECTORY / "libgobject-2.0.so.0"))
    signatures = {
        "vips_init": ([c.c_char_p], c.c_int),
        "vips_shutdown": ([], None),
        "vips_error_buffer": ([], c.c_char_p),
        "vips_concurrency_set": ([c.c_int], None),
        "vips_cache_set_max": ([c.c_int], None),
        "vips_operation_block_set": ([c.c_char_p, c.c_int], None),
        "vips_foreign_find_load": ([c.c_char_p], c.c_char_p),
        "vips_image_new_from_file": ([c.c_char_p], c.c_void_p),
        "vips_image_get_width": ([c.c_void_p], c.c_int),
        "vips_image_get_height": ([c.c_void_p], c.c_int),
        "vips_thumbnail": ([c.c_char_p, c.POINTER(c.c_void_p), c.c_int], c.c_int),
        "vips_target_new_to_descriptor": ([c.c_int], c.c_void_p),
        "vips_jpegsave_target": ([c.c_void_p, c.c_void_p], c.c_int),
        "vips_pngsave_target": ([c.c_void_p, c.c_void_p], c.c_int),
    }
    for name, (arguments, result) in signatures.items():
        function = getattr(vips, name)
        function.argtypes, function.restype = arguments, result
    gobject.g_object_unref.argtypes = [c.c_void_p]
    gobject.g_object_unref.restype = None

    def failed() -> RuntimeError:
        message = vips.vips_error_buffer().decode("utf-8", errors="replace").strip()
        return RuntimeError(message or "libvips conversion failed")

    if vips.vips_init(b"mtl-vips-preview") != 0:
        raise failed()
    image = c.c_void_p()
    target = None
    try:
        vips.vips_concurrency_set(1)
        vips.vips_cache_set_max(0)
        # Never allow libvips' optional ImageMagick bridge to hide a fallback.
        vips.vips_operation_block_set(b"VipsForeignLoadMagick", 1)
        vips.vips_operation_block_set(b"VipsForeignLoadMagick7", 1)
        filename = os.fsencode(path.resolve(strict=True))
        loader = vips.vips_foreign_find_load(filename)
        if not loader or loader.startswith(b"VipsForeignLoadMagick"):
            print("unsupported-format: no native libvips loader", file=sys.stderr)
            return UNSUPPORTED_FORMAT_EXIT
        if max_size <= 0:
            header = vips.vips_image_new_from_file(filename, None)
            if not header:
                raise failed()
            try:
                max_size = max(vips.vips_image_get_width(header), vips.vips_image_get_height(header))
            finally:
                gobject.g_object_unref(header)
        if vips.vips_thumbnail(filename, c.byref(image), max_size,
                               b"height", c.c_int(max_size),
                               b"size", c.c_int(VIPS_SIZE_DOWN),
                               b"fail_on", c.c_int(VIPS_FAIL_ON_ERROR), None) != 0:
            raise failed()
        target = vips.vips_target_new_to_descriptor(sys.stdout.fileno())
        if not target:
            raise failed()
        if output_format == "jpeg":
            result = vips.vips_jpegsave_target(image, target, b"Q", c.c_int(JPEG_QUALITY), None)
        else:
            result = vips.vips_pngsave_target(image, target, None)
        if result != 0:
            raise failed()
        return 0
    finally:
        if target:
            gobject.g_object_unref(target)
        if image:
            gobject.g_object_unref(image)
        vips.vips_shutdown()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    parser.add_argument("max_size", type=int, help="Maximum dimension; zero preserves original size")
    parser.add_argument("format", choices=("jpeg", "png"))
    args = parser.parse_args()
    try:
        return convert(args.path, args.max_size, args.format)
    except (OSError, RuntimeError) as error:
        print(f"libvips preview failed: {type(error).__name__}: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
