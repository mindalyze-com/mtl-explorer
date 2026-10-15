#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors.
"""Check public source links, topic indexes, hierarchy, and publication boundaries."""

import argparse
from collections import Counter
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit

if __package__:
    from .generate_documentation import AREAS, MARKDOWN, REPOSITORY, load_documents, ordered_children, plain, slug
else:
    from generate_documentation import AREAS, MARKDOWN, REPOSITORY, load_documents, ordered_children, plain, slug


def anchors(path):
    tokens = MARKDOWN.parse(path.read_text())
    counts, result = Counter(), set()
    for index, token in enumerate(tokens):
        if token.type == "heading_open":
            identifier = slug(plain(tokens[index + 1].content))
            counts[identifier] += 1
            result.add(identifier if counts[identifier] == 1 else f"{identifier}-{counts[identifier]}")
    return result


def check(repository=REPOSITORY):
    repository = repository.resolve()
    documents = load_documents(repository)
    failures, checked = [], 0
    linked = {(repository / "documentation/README.md").resolve()}
    for document in documents.values():
        path = document.path
        for token in MARKDOWN.parse(document.text):
            for child in token.children or []:
                for attribute in ("href", "src"):
                    href = child.attrGet(attribute)
                    if not href:
                        continue
                    value = urlsplit(href)
                    if value.scheme or value.netloc:
                        if "/docs-my/" in value.path:
                            failures.append(f"{path}: external URL points into internal documentation: {href}")
                        continue
                    if value.path.startswith("/"):
                        failures.append(f"{path}: use a relative source link: {href}")
                        continue
                    checked += 1
                    target = (path.parent / unquote(value.path)).resolve() if value.path else path.resolve()
                    if not target.is_relative_to(repository):
                        failures.append(f"{path}: reference escapes repository: {href}")
                        continue
                    if target.is_relative_to(repository / "docs-my"):
                        failures.append(f"{path}: public link into internal material: {href}")
                    if not target.exists():
                        failures.append(f"{path}: missing target: {href}")
                        continue
                    fragment = value.fragment
                    if fragment and target.suffix == ".md" and fragment not in anchors(target):
                        failures.append(f"{path}: missing fragment: {href}")
        if path.name == "README.md":
            linked.update(ordered_children(document, documents))
    for path in documents:
        if path not in linked:
            failures.append(f"Orphan public page: {path.relative_to(repository)}")
    for area in AREAS:
        root = repository / "documentation" / area
        for folder in [root, *(path for path in root.rglob("*") if path.is_dir())]:
            pages = [path for path in folder.glob("*.md") if path.name != "README.md"]
            children = [path for path in folder.iterdir() if path.is_dir() and any(path.rglob("*.md"))] if folder.exists() else []
            if (pages or children) and not (folder / "README.md").is_file():
                failures.append(f"Missing topic index: {folder.relative_to(repository)}")
            if len(pages) == 1 and not children:
                failures.append(f"Thin topic folder: {folder.relative_to(repository)}; keep the article in its parent")
    print(f"Checked {len(documents)} public pages and {checked} source references")
    for failure in failures:
        print("FAIL:", failure)
    if not failures:
        print("PASS")
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=REPOSITORY)
    args = parser.parse_args()
    try:
        failures = check(args.repository)
    except (OSError, ValueError) as error:
        parser.exit(1, f"Documentation check failed: {error}\n")
    raise SystemExit(1 if failures else 0)


if __name__ == "__main__":
    main()
