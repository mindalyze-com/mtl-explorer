# Release updates

Starting with 0.9.0, MTL Explorer checks a small public JSON feed at
`https://mtl-explorer.com/releases/releases.json`. The server performs the check;
browsers only request the cached result from `/mtl/api/info/updates`.

## User flow

- One notice introduces an available release. Dismiss keeps the Settings badge.
- Desktop Settings and mobile More show the shared glowing dot. Expanded mobile Settings keeps the **Update** label.
- **View update** or **Settings → Updates** opens versions, the summary, release notes, and update instructions.
- **Skip this version** hides its notice and badge. The next release can notify again.
- Reminder preferences belong to the browser, signed-in username, and installation.
- The installation owner updates Docker Compose. Detection never installs or restarts anything.
- After a detected installation change, one completion notice links to the notes.
- A waiting browser service worker offers **Reload** or **Later**, including before login. Release offers remain signed-in only. Later keeps the navigation dot, a **Reload** label in expanded mobile Settings, and a reload action in Updates; it never interrupts work automatically.

## Identities

| Identity | Example | Purpose |
| --- | --- | --- |
| Public release | `0.9.0` | Display, release notes, semantic version comparison |
| Immutable image build | `1.520` | Match the running Docker image to the public catalog |
| Image digest | `sha256:…` | Verify the exact published image before offering it |
| Git revision | commit SHA | Private build traceability and diagnostics |

Images carry their immutable build identity; public versions are chosen when
publishing and bound to verified builds in the release catalog. No public version
is bundled in the application. Docker build tags keep their existing numbering;
Git history does not decide release order. The public feed contains no private
Git revision, tree, or build record.

About shows the image build and, when the cached catalog identifies the installed
component combination, its deployment release. Before a feed is available, the
semantic release remains unknown. Local frontend builds show their development
package version. Retain published mappings so older installations remain identifiable.

The feed maps releases to immutable builds and has separate channel pointers.
The same build promoted to a public release is already installed. An unregistered
custom or older build reports unknown rather than guessing. Earlier versions do
not acquire this checker retroactively: 0.9.0 is the first checking installation,
and its first normal offer will be a later release. Preview screenshots use an
isolated synthetic 0.8.1 → 0.9.0 mapping to illustrate the flow.

## Components and deployment releases

One notice and badge cover self-managed MTL components. Updates lists the server,
routing, location search, and maps with installed and target image builds.
Provider-managed services are informational; disabled services are ignored.
Unknown ownership or builds never imply an update or incompatibility.
Unknown ownership stays visible on the component row but does not block the
overall status or completion notice for tracked, self-managed components.
An unknown build in a self-managed component still prevents claiming it is current.

The feed's optional `components` map describes a tested deployment combination.
Each target contains `buildId`, `image`, and `digest`. Component IDs are `app`,
`brouter`, `location-search`, and `map-server`. The existing top-level image fields
must match `components.app`. Feeds without a component map retain application-only
behavior. Public release versions describe the combination; individual image
build tags remain independent. Git revisions are diagnostic only.

A component-only release may reuse the application image. Installed release
identity comes from the self-managed component combination; the completion notice
uses its component inventory, so updating search alone clears its offer.
Registered builds from later releases are never offered a downgrade. Custom
unregistered builds remain unknown. Each component build identity has one immutable
image/digest binding across all releases.

Optional `compatibleBuildIds` lists explicitly supported older builds; the target
build is always supported. Omit the list when compatibility is unconfirmed. An
explicit list excluding a known older build marks that component as requiring an
update for the target release. This does not affect application health or disable
features. Provider API rollouts must preserve compatibility with supported clients;
release discovery is not a service-health check.

Ownership is configured, never guessed from a URL. A remote service can still be
self-managed. Compose defaults routing/search to `SELF_MANAGED` and maps to
`PROVIDER_MANAGED`. For provider-managed routing/search, change the corresponding
environment values. For local maps, enable `local-maps` and set
`MTL_UPDATES_MAP_SERVER_MANAGEMENT=SELF_MANAGED`.

| Compose environment variable | Default |
| --- | --- |
| `MTL_UPDATES_BROUTER_MANAGEMENT` | `SELF_MANAGED` |
| `MTL_UPDATES_LOCATION_SEARCH_MANAGEMENT` | `SELF_MANAGED` |
| `MTL_UPDATES_MAP_SERVER_MANAGEMENT` | `PROVIDER_MANAGED` |

Allowed values: `SELF_MANAGED`, `PROVIDER_MANAGED`, `DISABLED`, `UNKNOWN`.
Outside Compose, ownership defaults to `UNKNOWN`. Spring configuration uses
`mtl.updates.components.<component-id>.management` and optional `.status-url`;
otherwise existing component status URLs are used. Environment property names
remove component hyphens, for example
`MTL_UPDATES_COMPONENTS_LOCATIONSEARCH_MANAGEMENT`.

Self-managed maps require a reachable status URL: enable `local-maps` for the
Compose service, or configure `mtl.updates.components.map-server.status-url`
for a remote service. An unavailable map service affects its own observation;
other components continue checking. Use `PROVIDER_MANAGED` or `DISABLED` when
that matches the installation; ownership is never inferred from reachability.

Only self-managed, enabled services are probed. Their status response supplies
`versionInfo.image.version`. Requests run in the update worker with a 3-second
whole-request deadline and the same size limit as the feed. Provider-managed and
unknown services receive no update-discovery requests. Snapshots never wait for
network activity. Successful observations refresh on demand at most once per
minute; failures retain the last build and back off for 15 minutes. Manual checks
can retry after one minute. The browser allows the feed plus three sequential
component probes to finish within its 35-second request deadline.

PostgreSQL/PostGIS maintenance remains an operator task, with separate major
upgrade instructions. Embedded libraries belong to their image release. Map and
routing dataset freshness remains separate from software updates. The checker
never reads the Docker socket, installs images, or restarts services.

## Network restrictions

Checks run in a separate background worker and never block startup, normal API
requests, imports, or navigation. Connection timeout is 3 seconds; the whole
request, including a stalled body, is bounded to 12 seconds and 256 KiB.
Refused connections, DNS/TLS errors, HTTP failures, invalid feeds, and component probe failures produce
a short INFO log; stack traces are DEBUG only. There is no global warning,
error dialog, or failed health status. Updates shows a quiet explanation.
The last valid result stays cached in memory if a refresh fails.
Unknown component IDs and channels are ignored. Invalid releases and their
channel pointers are skipped; conflicting build bindings exclude every affected
release. Broken feed structure or unsupported schemas still reject the response.
Additive changes must retain `schemaVersion: 1`. A breaking schema needs a new
endpoint while the existing URL continues serving schema 1 for older installations.
Publication dates are validated by the publisher, independently of the consuming
server's date and time zone.

An active browser checks the cached API hourly and on focus/reconnection.
The server fetches at most daily, with a 15-minute failure retry gate and a
one-minute manual-check gate. Concurrent browsers share the server result.
No installed identity, username, or archive data is sent to the feed.

To make no release-feed requests, set `MTL_UPDATES_ENABLED=false` in the Compose
environment and recreate the app container. `MTL_UPDATES_CHANNEL` defaults to
`stable`; align it with the image channel you run. Development checks are disabled
by default, as are demo checks. Offline installations can update manually.

For a private mirror or test feed, set `MTL_UPDATES_FEED_URL` in the Compose
environment or `.env`, then recreate the app container:

```dotenv
MTL_UPDATES_FEED_URL=https://updates.example.com/releases/releases.json
```

```sh
docker compose up -d --no-deps --force-recreate app
```

The default remains `https://mtl-explorer.com/releases/releases.json`. The URL must
serve the same release-feed JSON over HTTP or HTTPS and be reachable from the
server container. For a test server on the Mac host, use
`http://host.docker.internal:18769/releases/releases.json`; container `localhost`
refers to the container itself.

Outside Compose, set Spring's `mtl.updates.feed-url` or the environment variable
`MTL_UPDATES_FEEDURL`. Compose maps the readable `MTL_UPDATES_FEED_URL` name to this
Spring environment name. The browser continues to use the server's cached result.

## Release publication

1. Choose the public version and prepare its reviewed release notes.
   Keep `released_on: null` until publication.
2. Commit the application source and build an unused immutable Docker tag with
   `docker-build-deploy.py`, or select an existing verified build. Build records
   identify the image by build ID and digest, independently of the public version.
3. Publish/promote the image channel intended for users and verify it. Keep the
   generated image record in the private repository; never invent a digest or
   reuse a published build tag.
   For stable, promote the tested build to Docker `latest`:

   ```sh
   ./docker-build-deploy.py --app-only --tag-latest-only --app-tag BUILD
   ```

   Replace `BUILD` with the unused tag you actually built. The record is in
   `../mtl-explorer-private/docs-my/operations/releases/builds/mytraillog-BUILD.json`
   by default. Commit it in the private repository. See
   [container builds](containers.md) for output overrides.
4. Set the note's actual publication date. Run the normal publisher with the
   record, for example:

   ```sh
   python ../mtl-explorer-private/publish-to-github.py v.0.9.0 "MTL Explorer 0.9.0" \
     --image-record ../mtl-explorer-private/docs-my/operations/releases/builds/mytraillog-BUILD.json \
     --update-channel stable
   ```

5. The publisher checks the immutable image and the corresponding Docker channel
   (`stable` → `latest`) against the record's live registry digest before binding the release in
   `documentation/release-notes/artifacts.json`. It regenerates the homepage,
   notes, archive, and `docs/releases/releases.json` before copying the mirror.
   Commit the reviewed source catalog and generated output in the primary repo.
6. Publish through the existing confirmations, then check the public feed,
   notes, update guide, and a registered older installation.

Published bindings are immutable. Draft notes, unregistered notes, sync-only,
release-only, and GitHub draft/prerelease operations do not create update offers.
The public catalog starts empty; no historical image mappings are inferred.
The current website tooling uses numeric public release versions. Beta/alpha
pointers can select published numeric releases; prerelease note authoring is
outside this first implementation.
For example, `--update-channel beta` verifies Docker `:beta` and advances the
beta feed pointer for `v.0.9.0`. It does not require GitHub's `--prerelease` flag;
that flag intentionally excludes publication from the update feed.
Numeric beta/alpha releases appear in the website's regular release list. When
creating their GitHub release, the publisher also explicitly passes `--latest`;
the update channel does not isolate them from public release announcements.

## Isolated E2E preview

Use an existing development container for Java and a disposable database. Never
change production channels or the real public catalog to simulate an update.

```sh
python -m documentation.tools.website.prepare_update_preview \
  /Volumes/EXT_HD_1TB/mytraillog/update-preview
python -m http.server 18769 --bind 0.0.0.0 \
  --directory /Volumes/EXT_HD_1TB/mytraillog/update-preview/docs
```

Set the test server's `MTL_IMAGE_VERSION=1.508`, enable checks, and point
`MTL_UPDATES_FEED_URL` (Compose) or `mtl.updates.feed-url` (Spring) to the fixture
URL reachable from its container. Verify
notice, dismissal/reload, badge, details, skip, and restore on desktop and phone.
Restart only that test server with build `1.520`: the badge clears and one
completion notice appears. Point it to a refused or stalled endpoint and verify
login, map interaction, normal API calls, INFO logging, and Updates fallback.
For a browser-worker upgrade, serve two distinct production frontend builds at
one test origin: wait for Reload, choose Later, confirm work is retained, then
reload explicitly. A development Vite server does not register that worker.

Automated coverage: `ReleaseFeedClientTest`, `ReleaseUpdateServiceTest`,
`ReleaseComponentInventoryTest`, `releaseUpdatesStore.spec.ts`, `releaseUpdateDetails.spec.ts`, `test_release_feed.py`, and
`test_release_publication.py`. See [application verification](testing/README.md) and
[component verification](testing/README.md).

## Publishing a component bundle

Full Docker builds also write `mtl-bundle-BUILD.json`, named for the application
build ID, in the same private release-record directory. App-only builds record
only the app. Commit the manifest in the private repository before publication.
Use `--bundle-record PATH` instead of `--image-record PATH` with
`../mtl-explorer-private/publish-to-github.py`, for example:

```sh
python ../mtl-explorer-private/publish-to-github.py v.0.9.0 "MTL Explorer 0.9.0" \
  --bundle-record ../mtl-explorer-private/docs-my/operations/releases/builds/mtl-bundle-BUILD.json \
  --update-channel stable
```

The publisher verifies every listed immutable image digest and selected Docker
channel before registering the bundle. It never installs it.

For a component-only release, explicitly select the previous tested bundle and
replace the changed artifact with its published immutable image:

```sh
python -m documentation.tools.website.prepare_release_bundle \
  --base-record /path/to/previous-bundle.json \
  --component location-search=wauwau0977/mytraillog-location-search:BUILD \
  --output ../mtl-explorer-private/docs-my/operations/releases/builds/search-update-bundle.json
```

Replace `BUILD` with the actual immutable tag. The helper reads its registry
digest and retains unchanged components. Repeat `--component` for more changes;
use `--component app=IMAGE:BUILD` to retain sidecars after an app-only build.
If the base record contains only the app, explicitly add each sidecar to track
with another `--component` argument.
Changed artifacts do not inherit old compatibility claims. The helper neither
promotes channels nor publishes, and refuses to overwrite an existing record.
Test the resulting combination, promote its selected channels, and pass the
record to `../mtl-explorer-private/publish-to-github.py --bundle-record` with the chosen public version.
App-only build records remain app-only unless a base is explicitly selected.
Every bundle includes the unchanged application artifact. Existing component
build tags cannot be overwritten; full builds reject reused tags for every image they build. Tag-only
mode promotes existing artifacts. Keep private Git metadata out of the manifest.

For reproducible deployments, pin Compose overrides to the verified manifest
digests. Independent moving `latest` tags do not establish compatibility. Update
only affected services according to release notes; database major upgrades have
a separate operator procedure.

To preview a component-only offer with an unchanged server image, add
`--components` to `documentation.tools.website.prepare_update_preview`. The synthetic catalog
offers 0.9.1 to server build 1.508 and search build 2.1; search 2.2 clears it.
