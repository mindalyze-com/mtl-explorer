"""Prepare an explicit MTL Explorer component combination without publishing it."""

import argparse
from copy import deepcopy
import json
from pathlib import Path
import subprocess

from documentation.tools.website.release_feed import (
    BUILD_ID,
    COMPONENTS,
    artifact_from_record,
    validate_artifact_bindings,
)


def prepare_bundle(base_record, replacements):
    """Retain selected base artifacts and resolve changed immutable image tags."""
    base = artifact_from_record(base_record)
    components = deepcopy(base.get("components", {"app": base}))
    for component, image in replacements.items():
        if component not in COMPONENTS:
            raise ValueError(f"Unknown component: {component}")
        repository, separator, build = image.rpartition(":")
        if not separator or not repository or not BUILD_ID.fullmatch(build) or build in {"latest", "alpha", "beta"}:
            raise ValueError("Replacement images require an immutable build tag")
        result = subprocess.run(
            ["docker", "buildx", "imagetools", "inspect", image, "--format", "{{.Manifest.Digest}}"],
            check=True, capture_output=True, text=True,
        )
        components[component] = {"buildId": build, "image": image, "digest": result.stdout.strip()}
    record = {"components": components}
    bundle = artifact_from_record(record)
    validate_artifact_bindings({"releases": {"base": base, "prepared": bundle}})
    return record


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-record", type=Path, required=True, help="Previously tested image or bundle record.")
    parser.add_argument("--component", action="append", required=True, metavar="ID=IMAGE:BUILD",
                        help="Replace one component with a published immutable image; repeat for other changes.")
    parser.add_argument("--output", type=Path, required=True, help="New bundle record; existing files are not overwritten.")
    args = parser.parse_args(argv)
    replacements = {}
    for value in args.component:
        component, separator, image = value.partition("=")
        if not separator or component in replacements:
            parser.error("Each --component must name one distinct ID=IMAGE:BUILD")
        replacements[component] = image
    record = prepare_bundle(json.loads(args.base_record.read_text()), replacements)
    with args.output.open("x", encoding="utf-8") as output:
        output.write(json.dumps(record, indent=2) + "\n")
    print(f"Prepared {len(record['components'])} components in {args.output}. Test the combination before publication.")


if __name__ == "__main__":
    main()
