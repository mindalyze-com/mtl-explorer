"""Bind public release notes to immutable Docker artifacts, without private Git metadata."""

import json
from pathlib import Path
import re

SCHEMA_VERSION = 1
COMPONENTS = {"app", "brouter", "location-search", "map-server"}
CHANNELS = {"stable", "beta", "alpha"}
DIGEST = re.compile(r"sha256:[a-f0-9]{64}")
BUILD_ID = re.compile(r"[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")
REGISTRY_PATH = Path("documentation/release-notes/artifacts.json")


def load_registry(repository):
    path = repository / REGISTRY_PATH
    registry = json.loads(path.read_text()) if path.exists() else {"schemaVersion": 1, "channels": {}, "releases": {}}
    if registry.get("schemaVersion") != SCHEMA_VERSION or not isinstance(registry.get("channels"), dict) or not isinstance(registry.get("releases"), dict):
        raise ValueError("Unsupported release artifact registry")
    if set(registry["channels"]) - CHANNELS:
        raise ValueError("Unknown update channel")
    for version in registry["channels"].values():
        if version not in registry["releases"]:
            raise ValueError("Channel target has no registered artifact")
    for artifact in registry["releases"].values():
        validate_bundle(artifact)
    validate_artifact_bindings(registry)
    return registry


def validate_artifact(artifact):
    if not isinstance(artifact, dict) or not {"buildId", "image", "digest"} <= set(artifact) or set(artifact) - {"buildId", "image", "digest", "compatibleBuildIds"}:
        raise ValueError("Invalid public component artifact fields")
    build = artifact.get("buildId", "")
    if not isinstance(build, str) or not BUILD_ID.fullmatch(build) or not isinstance(artifact.get("digest"), str) or not DIGEST.fullmatch(artifact["digest"]):
        raise ValueError("Artifact requires a build ID and SHA-256 digest")
    if not isinstance(artifact.get("image"), str) or not artifact["image"].endswith(":" + build):
        raise ValueError("Artifact image must use its immutable build tag")


def validate_bundle(bundle):
    if not isinstance(bundle, dict) or set(bundle) - {"buildId", "image", "digest", "components"}:
        raise ValueError("Invalid public release bundle fields")
    validate_artifact({key: bundle.get(key) for key in ("buildId", "image", "digest")})
    components = bundle.get("components")
    if components is None:
        return
    if not isinstance(components, dict) or "app" not in components or set(components) - COMPONENTS:
        raise ValueError("Release bundle requires app and only supported MTL components")
    for artifact in components.values():
        validate_artifact(artifact)
        compatible = artifact.get("compatibleBuildIds")
        if compatible is not None and (not isinstance(compatible, list) or any(not isinstance(build, str) or not BUILD_ID.fullmatch(build) for build in compatible)):
            raise ValueError("Compatibility requires explicit image build IDs")
    if any(bundle[key] != components["app"][key] for key in ("buildId", "image", "digest")):
        raise ValueError("Application artifact does not match bundle")


def validate_artifact_bindings(registry):
    identities = {}
    for bundle in registry["releases"].values():
        for component, artifact in bundle.get("components", {"app": bundle}).items():
            key = (component, artifact["buildId"])
            binding = (artifact["image"], artifact["digest"])
            if key in identities and identities[key] != binding:
                raise ValueError("A component build identity cannot refer to different images or digests")
            identities[key] = binding


def artifact_from_record(record):
    if "components" in record:
        components = record["components"]
        if not isinstance(components, dict) or not isinstance(components.get("app"), dict):
            raise ValueError("Bundle record requires an application artifact")
        artifact = {**{key: components["app"].get(key) for key in ("buildId", "image", "digest")}, "components": components}
    else:
        image = record.get("image", "")
        artifact = {"buildId": record.get("buildId", image.rsplit(":", 1)[-1]),
                    "image": image, "digest": record.get("digest", "")}
    validate_bundle(artifact)
    return artifact


def release_feed(repository, releases, origin):
    registry = load_registry(repository)
    notes = {release.number: release for release in releases}
    entries = {}
    for version, artifact in registry["releases"].items():
        if version not in notes:
            raise ValueError(f"Artifact {version} has no versioned release notes")
        note = notes[version]
        # Draft notes remain visible on the website, but never become update offers.
        if note.released_on is None:
            continue
        entries[version] = {
            **artifact,
            "summary": note.teaser,
            "releaseNotesUrl": origin + note.url,
            "updateGuideUrl": origin + "/docker-install/#updates",
            "publishedOn": note.released_on.isoformat(),
        }
    channels = {channel: version for channel, version in registry["channels"].items() if version in entries}
    if "-" in channels.get("stable", ""):
        raise ValueError("Stable channel cannot target a prerelease")
    return {"schemaVersion": SCHEMA_VERSION, "channels": channels, "releases": entries}


def register_artifact(repository, version, record, channel="stable"):
    if channel not in CHANNELS:
        raise ValueError("Unknown update channel")
    artifact = artifact_from_record(record)
    registry = load_registry(repository)
    previous = registry["releases"].get(version)
    if previous is not None and previous != artifact:
        raise ValueError("Published release artifact is immutable; choose a new release version")
    registry["releases"][version] = artifact
    registry["channels"][channel] = version
    validate_artifact_bindings(registry)
    path = repository / REGISTRY_PATH
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(registry, indent=2) + "\n")
    return artifact
