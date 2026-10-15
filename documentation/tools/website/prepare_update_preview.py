#!/usr/bin/env python3
"""Prepare an isolated, synthetic 0.8.1 → 0.9.0 update website. Never publishes."""

import argparse
from datetime import date
from pathlib import Path
import shutil

from documentation.tools.website import generate_releases
from documentation.tools.website.release_feed import register_artifact

REPOSITORY = Path(__file__).resolve().parents[3]


def prepare(output, components=False):
    output = output.resolve()
    if output.is_relative_to(REPOSITORY):
        raise ValueError("Use an isolated output directory outside the source repository")
    output.mkdir(parents=True, exist_ok=True)
    shutil.copytree(REPOSITORY / "docs", output / "docs", dirs_exist_ok=True)
    notes = output / "documentation/release-notes"
    notes.mkdir(parents=True, exist_ok=True)
    for version in ("0.8.1", "0.9.0"):
        text = (REPOSITORY / "documentation/release-notes" / f"v.{version}.md").read_text()
        text = text.replace("released_on: null", f"released_on: {date.today().isoformat()}")
        (notes / f"v.{version}.md").write_text(text)
    # Synthetic artifact identities are confined to this preview directory.
    for version, build, digest in (("0.8.1", "1.508", "a"), ("0.9.0", "1.520", "b")):
        register_artifact(output, version, {"buildId": build, "image": f"example/mtl:{build}",
                                           "digest": "sha256:" + digest * 64})
    if components:
        # A search-only release keeps the application image unchanged.
        (notes / "artifacts.json").unlink()
        for version, search in (("0.9.0", "2.1"), ("0.9.1", "2.2")):
            text = (notes / "v.0.9.0.md").read_text().replace("MTL Explorer 0.9.0", f"MTL Explorer {version}")
            (notes / f"v.{version}.md").write_text(text)
            targets = {"app": ("1.508", "a"), "brouter": ("1.1", "c"),
                       "location-search": (search, "d" if search == "2.1" else "e"), "map-server": ("1.1", "f")}
            artifacts = {component: {"buildId": build, "image": f"example/{component}:{build}", "digest": "sha256:" + digest * 64}
                         for component, (build, digest) in targets.items()}
            artifacts["location-search"]["compatibleBuildIds"] = ["2.1"]
            register_artifact(output, version, {"components": artifacts})
    generate_releases.generate(output)
    print(f"Serve {output / 'docs'} on a free port; synthetic fixtures only.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--components", action="store_true", help="Preview a search-only bundle update with an unchanged server image.")
    args = parser.parse_args()
    prepare(args.output, args.components)
