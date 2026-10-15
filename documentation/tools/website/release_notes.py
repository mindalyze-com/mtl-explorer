"""Read the small metadata convention shared by website and GitHub releases."""

from dataclasses import dataclass
from datetime import date
from pathlib import Path
import re


RELEASE_FILENAME = re.compile(r"v\.(\d+)\.(\d+)\.(\d+)\.md")
DATE_FIELD = re.compile(r"released_on: (null|\d{4}-\d{2}-\d{2})")
MAX_TEASER_LENGTH = 160
TEASER_MARKUP = re.compile(
    r"^(?:#{1,6}|[-*>]|\d+[.)])\s|[`*]|__|(?<!\w)_[^_]+_(?!\w)|\[[^\]]*\]\(|<[^>]+>"
)


@dataclass(frozen=True)
class ReleaseMetadata:
    released_on: date | None
    teaser: str | None


def split_metadata(text: str) -> tuple[ReleaseMetadata | None, str]:
    """Read the date and folded teaser; preserve custom notes without metadata."""
    text = text.removeprefix("\ufeff").replace("\r\n", "\n")
    if not text.startswith("---\n"):
        return None, text
    metadata, separator, body = text[4:].partition("\n---\n")
    lines = metadata.strip().splitlines()
    match = DATE_FIELD.fullmatch(lines[0]) if lines else None
    if not separator or not match:
        raise ValueError("Start front matter with released_on: null or YYYY-MM-DD")
    value = match.group(1)
    released_on = None if value == "null" else date.fromisoformat(value)
    if released_on and released_on > date.today():
        raise ValueError("A future publication date must remain released_on: null")
    teaser = None
    if len(lines) > 1:
        if lines[1] != "teaser: >-" or len(lines) < 3 or any(
            not line.startswith("  ") or not line.strip() for line in lines[2:]
        ):
            raise ValueError("Use teaser: >- followed by indented text; no other metadata fields")
        teaser = " ".join(" ".join(lines[2:]).split())
        if len(teaser) > MAX_TEASER_LENGTH:
            raise ValueError(f"Keep the teaser within {MAX_TEASER_LENGTH} characters")
        if TEASER_MARKUP.search(teaser):
            raise ValueError("Use plain text in the teaser, without Markdown or HTML markup")
    return ReleaseMetadata(released_on, teaser), body.lstrip("\n")


def insert_teaser(body: str, teaser: str) -> str:
    heading, separator, content = body.partition("\n\n")
    if not separator or not heading.startswith("# ") or "\n" in heading:
        raise ValueError("Expected an H1 followed by a blank line")
    return f"{heading}\n\n{teaser}\n\n{content}"


def github_notes_body(text: str) -> str:
    metadata, body = split_metadata(text)
    return insert_teaser(body, metadata.teaser) if metadata and metadata.teaser else body


@dataclass(frozen=True)
class ReleaseNote:
    path: Path
    version: tuple[int, int, int]
    released_on: date | None
    title: str
    teaser: str
    body: str
    content: str

    @property
    def number(self) -> str:
        return ".".join(str(part) for part in self.version)

    @property
    def tag(self) -> str:
        return f"v.{self.number}"

    @property
    def url(self) -> str:
        return f"/releases/{self.number}/"


def load_release_note(path: Path) -> ReleaseNote:
    match = RELEASE_FILENAME.fullmatch(path.name)
    if not match:
        raise ValueError(f"{path}: expected a filename such as v.0.9.0.md")
    text = path.read_text(encoding="utf-8")
    metadata, body = split_metadata(text)
    if metadata is None or metadata.teaser is None:
        raise ValueError(f"{path}: add released_on and teaser metadata before generating the website")
    body = insert_teaser(body, metadata.teaser)
    heading, _, content = body.partition("\n\n")
    return ReleaseNote(
        path, tuple(int(part) for part in match.groups()), metadata.released_on,
        heading[2:], metadata.teaser, body, content,
    )


def load_releases(directory: Path) -> list[ReleaseNote]:
    releases = [load_release_note(path) for path in sorted(directory.glob("v.*.md"))]
    if not releases:
        raise ValueError(f"{directory}: no versioned release notes found")
    versions = [release.version for release in releases]
    if len(versions) != len(set(versions)):
        raise ValueError("Duplicate release versions")
    return sorted(releases, key=lambda release: release.version, reverse=True)
