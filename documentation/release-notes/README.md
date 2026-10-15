# MTL Explorer release notes

[v.0.9.0](v.0.9.0.md) contains the reviewed notes for the upcoming release.

For later releases, start an `unreleased.md` draft. Order chapters by user impact,
with major features first and fixes and upgrade details afterward. Describe
changes for users and link to the relevant guides.

## Website generation

Versioned files are the source for homepage news, the release archive, full
website notes, and GitHub release notes. Use this convention:

```markdown
---
released_on: null
teaser: >-
  Improved segment comparison and replay, route planning with saved places,
  and optional health data.
---

# MTL Explorer Release v.0.9.0

## Features and improvements
...
```

Write one short, plain-text teaser about the main user benefits, usually 80–140
characters (160 maximum). Pick two or three highlights; keep small fixes and
detailed lists in the chapters. For a broad release, lead with changes useful
to most users and describe integrations as optional. Leave the version and
publication status out of the teaser; the website displays them separately.
Use ordinary text without Markdown formatting, links, or HTML.

The two metadata fields use the order and indentation shown above. `>-` joins
the indented teaser lines into one paragraph. Every versioned note requires an
explicit teaser; the generator does not infer one from the introduction or
first chapter. Older releases in this directory follow the same convention.

The version comes from `v.<major>.<minor>.<patch>.md`. Use `null` for upcoming
releases. At publication, set `released_on` to the actual `YYYY-MM-DD` date.
Historical dates were checked against GitHub's published releases.

The same teaser supplies the homepage banner, news, archive summaries, and
release-page descriptions. It is also inserted below the H1 in full website
and GitHub notes. Write it once in metadata; the Markdown body holds the
detailed introduction, chapters, and upgrade instructions.

Run `python documentation/tools/website/generate_releases.py`, then add `--check` to verify
the output is current. See [website tooling](../tools/website/README.md) for
dependencies and local preview commands. Edit the Markdown, not generated HTML.
The generator updates marked homepage/sitemap regions and `docs/releases/`.
Relative documentation links are resolved to the public GitHub documentation;
links between release files point to the website release pages.

Review the teaser after writing the detailed notes. Regenerate and preview the
homepage banner, news, archive, and full notes together. At publication, set the
date and regenerate again before running the publisher.

`../mtl-explorer-private/publish-to-github.py` regenerates the site before a full mirror sync or a
`--sync-path docs` sync. GitHub release notes receive the Markdown body without
metadata, with the teaser inserted as the opening paragraph. Generation and
preview do not publish anything.

Before release, compare the current files with both the latest GitHub release
tag and GitHub `main`. The public mirror has separate history; compare snapshots
without merging it into the development repository. Describe improvements to
existing features without presenting them as new additions.

When the version is chosen, save the reviewed notes as `<release-tag>.md` and
use that file with `../mtl-explorer-private/publish-to-github.py`. The publisher already defaults to
this directory. Publishing requires a separate request. Keep comparison evidence
and private development history outside public release notes.

## Published releases

From 0.9.0, publication also binds a verified immutable Docker image to the
public update feed. See the [release update flow](../development/releases.md).

- [v.0.8.1](v.0.8.1.md)
- [v.0.8.0](v.0.8.0.md)
- [v.0.7.0](v.0.7.0.md)
- [v.0.6.1](v.0.6.1.md)
- [v.0.6.0](v.0.6.0.md)
