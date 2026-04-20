# Contributing to MyTrailLog

Thanks for your interest in improving MyTrailLog! Please read this document
carefully before opening a pull request — a few of the rules below are
**required** for us to be able to accept your contribution.

## 1. License of your contribution

MyTrailLog is distributed under the [GNU AGPL-3.0-or-later](LICENSE) and is
also offered under a separate commercial license (see
[COMMERCIAL-LICENSE.md](COMMERCIAL-LICENSE.md)).

To keep this dual-licensing model working, **every contributor must agree to
the Contributor License Agreement** ([CLA.md](CLA.md)) before their code can
be merged. In short, you grant Patrick Heusser (the project maintainer) the
right to relicense your contribution under both AGPL-3.0 and future
commercial licenses, while **you keep the copyright to your own code**.

### How to sign the CLA

- **Individuals:** the first time you open a pull request, a bot (or a
  maintainer) will ask you to confirm the CLA by commenting the following on
  the PR:

  ```
  I have read and agree to the MyTrailLog CLA (CLA.md).
  ```

- **On behalf of a company:** please email **hey.lueg@gmail.com** before
  opening the PR so we can arrange a corporate CLA.

Pull requests without a signed CLA cannot be merged.

## 2. Developer Certificate of Origin (DCO)

In addition to the CLA, every commit must be signed off according to the
[Developer Certificate of Origin 1.1](https://developercertificate.org/).
Use `git commit -s` to add a `Signed-off-by:` trailer.

## 3. Copyright headers in source files

New source files must start with the SPDX short identifier:

```
// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2020-2026 Patrick Heusser and MyTrailLog contributors.
```

Use `#` for Python / shell / YAML, `<!--` for HTML/XML/Vue templates, `/*`
for Java / TypeScript / JavaScript / CSS. Do **not** modify the copyright
line of existing files when adding your changes — just add your name to
`AUTHORS` if you wish.

## 4. Third-party code and assets

Do **not** paste code from AGPL-incompatible sources (including "public
forum" snippets without a clear license). If you want to import an external
library:

- It must be compatible with AGPL-3.0-or-later (Apache-2.0, MIT, BSD, MPL-2.0,
  LGPL-3.0 and GPL-3.0 are fine; GPL-2.0-only, CC-BY-NC, "source-available"
  and most proprietary licenses are **not**).
- Add it to the appropriate dependency manifest (`pom.xml`, `package.json`,
  `requirements.txt`, Dockerfile) — never vendor source files directly
  unless strictly necessary.
- Update `THIRD_PARTY_LICENSES.md` with the library name, version and license.

Map tiles, sample datasets and photos each have their own upstream licenses
and must not be relicensed.

## 5. Coding / workflow conventions

- Frontend: Vue 3 + TypeScript (`mtl-client`) — `npm run lint`, `npm run type-check`, `npm run test`.
- Backend: Spring Boot + Maven (`mtl-server`) — `./mvnw verify`.
- Keep PRs focused; split refactors from behavior changes.
- Follow the existing architecture notes in `mtl-server/doc/` and repository
  memory documents.

## 6. Reporting security issues

**Do not** open public issues for security vulnerabilities.
See [SECURITY.md](SECURITY.md).

## 7. Code of Conduct

By participating, you agree to uphold the project [Code of Conduct](CODE_OF_CONDUCT.md).
