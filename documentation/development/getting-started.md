# Development setup

Use the existing checkout and Java development container. On this machine the running container is `mtl-java-dev`; start it on demand if stopped. Do not install Java or run Maven on the host.

1. Inspect `git status` and preserve unrelated changes.
2. In the development container, generate the [TypeScript API package](api-generation.md).
3. Build and run the [backend](backend.md) with its diagnostics module.
4. Start the [frontend](frontend.md) from `mtl-client/`.
5. Use the printed `/mtl/` URL in the Codex in-app browser. Obtain local GUI credentials only from `mtl-server/src/main/resources/application-dev.yml`.
6. Run [checks](testing/README.md) relevant to the change.

Keep trial datasets and raw evidence on the mounted external drive. Use public or fully synthetic data. Stop temporary instances after checks and retain downloaded map/routing data.

## Build script

Run [the build script](../tools/build.py) from the repository root:

```sh
# Client and server (default)
python3 documentation/tools/build.py package -- -DskipTests

# Client only
python3 documentation/tools/build.py --client-only package

# Server only
python3 documentation/tools/build.py --server-only package -- -DskipTests
```

The script builds a verified source snapshot in the development container and
keeps outputs, `build.log`, and `build.json` on the mounted external drive.
It accepts `compile`, `test`, `package`, and `verify`; put wrapper flags before
the action and Maven options after `--`.

By default, the script generates and installs the TypeScript API package from
the saved schema, packages the Vue client into the snapshot's server resources,
then runs the requested server goal. The API build supplies its configured
Node/npm toolchain. `--client-only` generates the API package and runs the
requested client Maven goal. `--server-only` skips the API and client steps.
Server-only packages contain no bundled UI; use the default build for a complete
application.
Maven options apply to the server when included, otherwise to the client.
Client Maven goals use the existing `build-only` task, which does not run
TypeScript checks or client tests. Source files in the checkout are not updated.

Snapshots are retained by default. For disposable builds, put `--keep N` before
the action to retain the newest N successful disposable snapshots per checkout
and selected component. Cleanup only considers snapshots created with `--keep`;
existing, failed, running, and other checkouts' snapshots are retained. Create a
`.keep` file inside a snapshot to pin it. Omit `--keep` for saved verification
evidence. Cleanup runs after a successful build.
