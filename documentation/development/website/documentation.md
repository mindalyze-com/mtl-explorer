# Documentation authoring

Write public documentation under `documentation/` and internal material in the separate private checkout under `../mtl-explorer-private/docs-my/`. Choose the audience first. Use named pages for small topics; group several related articles when a shared overview helps. Do not add a folder for one article.

Start with [the documentation index](../../README.md). Internal work starts at
`../mtl-explorer-private/docs-my/README.md`.

Every topic folder has a short `README.md` with reading order and child links. Each page has one title and short, plain language. User pages describe actions, results, limits, and recovery. Technical pages describe current contracts and link to source.

Keep one authoritative source per subject. Link to detailed reference pages.

Keep README, markup, and agent notes concise. Keep the root `README.md`
GitHub-friendly, using tables, badges, and collapsibles where helpful.

When moving a page, update its references and remove the old file. Do not leave
redirect placeholders.

Keep proposals and dated verification out of current architecture. Update user guides only when a change affects what the user does. OpenAPI remains the source for API types.

Private proposals, operations, research, and dated evidence belong in the
separate private repository.

For major user-facing features, update [the feature catalog](../../features.md)
when warranted. When the database schema changes, update [database architecture](../../architecture/database.md)
and the Mermaid sources and rendered diagrams in [database assets](../../../docs/assets/database/README.md),
then regenerate public documentation.

For each behavior change, check the owning user task (when it changes a user action), administration settings,
architecture contract, feature catalog, and current regression checklist.
Compare UI labels with components, defaults with shipped configuration, and
API fields with the live schema. Update current-status notes in affected
proposals; retain dated outcomes and released guides. Include a guide-content
review in release preparation, then regenerate and run the checks below.

Generate the website and installed guide from the same sources. Edit Markdown, not generated output. Check target paths, fragments, navigation, private links, and freshness before publication. The documentation generator's CLI and version rules are described in the [tooling notes](../../tools/website/README.md).

Keep release scope visible in the generated documentation.

Keep compact WebP screenshots near their owning topic. Raw captures and bulky datasets go to the mounted external drive. Use public or synthetic data only.

The current guide version is `CURRENT_GUIDE_VERSION` in the generator. Its release
note's publication date supplies the Released/Development label. When starting a
new version with changed workflows, preserve the previous released guide under
`versions/<version>/` using the public release snapshot, then update that constant.
Keep the installed guide generated from the same current user/admin sources.

Topic rules apply to source folders. Generated `index.html` folders provide
website routes; they are not extra topic layers.
