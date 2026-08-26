# MTL Explorer Hetzner Demo Deployment Runbook

## Scope

This runbook covers only these demo instances:

| Key | Instance | Domain | Compose project |
|---|---|---|---|
| `regulardemo` | Demo | `mtl-demo.mindalyze.com` | `mtl-demo-mindalyze-com` |
| `large` | Demo Large | `mtl-demo-large.mindalyze.com` | `mtl-demo-large-mindalyze-com` |
| `beta` | Demo Beta | `mtl-demo-beta.mindalyze.com` | `mtl-demo-beta-mindalyze-com` |

Read the current SSH target from `docs-my/hetzner/server-notes.md`. Do not copy credentials into the skill, evidence, or command output.

## Sources Of Truth

- Local release builder: `docker-build-deploy.py`
- Deployment policy: `docs-my/hetzner/deploy-hetzner.md`
- Local copies of server helpers: `docs-my/hetzner/current_helper_scripts/`
- Remote helpers: `/root/deploy-hetzner.sh`, `/root/docker-stop-start.sh`, `/root/docker-delete-reset.sh`, and `/root/docker-delete-reset-beta.sh`
- Live Compose files: `/opt/mtl/<domain>/docker-compose.yml`
- Shared Compose file: `/opt/mtl/shared/docker-compose.yml`

Always inspect these files again. Their current content overrides this dated observation.

## Dated Live Observation

Read-only inspection on 2026-08-19 found:

| Instance | Configured app channel | Reported image version |
|---|---:|---:|
| Demo | `latest` | `1.340` |
| Demo Large | `beta` | `1.340` |
| Demo Beta | `beta` | `1.361` |

The channel is not the deployment target. Demo Large may consume `beta` while remaining untouched during a deployment to the `beta` instance. An unselected instance keeps running its current digest until it is explicitly selected for a later pull/restart or reset.

The helper update on 2026-08-19 made `docker-stop-start.sh` and `docker-delete-reset.sh` accept any combination of the three instance keys. Both helpers now pull every selected stack before starting it. `docker-delete-reset-beta.sh` remains only as a compatibility wrapper for `docker-delete-reset.sh beta`.

Treat these as dated observations. Recompute helper checksums and inspect current behavior before every deployment.

## Build And Publish Plan

`docker-build-deploy.py` builds and pushes app, location-search, maps, and BRouter images. A full build always publishes the four immutable version tags. Channel selection adds moving channel tags.

For every full code build, update `APP_VERSION_TAG` in `docker-build-deploy.py` before the dry run. Increment its final numeric component by at least one; use the smallest increment unless the user requests a larger value. For example, `1.361` becomes `1.362`. Persist the new constant in the file rather than relying only on `--app-tag`.

Keep `MAP_VERSION_TAG`, `LOCATION_SEARCH_VERSION_TAG`, and `BROUTER_VERSION_TAG` unchanged by default. Increment only the component tags the user explicitly selects. Tag-only mode retags existing images and does not change version constants.

Inspect current defaults and flags first:

```bash
./docker-build-deploy.py --help
```

For a normal demo code deployment, construct one deterministic command from this base:

```bash
./docker-build-deploy.py \
  --yes \
  --full-build \
  --multi-platform \
  --no-alpha \
  --app-tag <app-version> \
  --map-tag <map-version> \
  --location-search-tag <location-search-version> \
  --brouter-tag <brouter-version> \
  <channel-flags>
```

Channel flags:

| User choice | Flags |
|---|---|
| Beta only | `--include-beta` |
| Latest only | `--include-latest` |
| Beta and latest | `--include-beta --include-latest` |

Append `--dry-run` for the mandatory preview. Remove only `--dry-run` after final approval. Keep `--no-alpha`; alpha is enabled by default in the release builder.

Do not call `--tag-beta-only` or `--tag-latest-only` for a requested code build. Those compatibility options retag existing immutable images and do not build the current worktree.

## Read-Only Local Preflight

1. Read `AGENTS.md` and preserve unrelated worktree changes.
2. Record `git status --short`, commit identity, and relevant diff summary.
3. Inspect current builder constants and `--help` rather than reusing remembered flags.
4. For a full code build, propose the next app tag and ask whether any other component tags should change. Record the old and proposed values.
5. After the version choice is confirmed, edit the selected constants and verify the diff. Require the new app tag to be strictly higher than the previous constant.
6. Verify the Docker daemon, active context, Buildx builder, and Docker Hub credentials without printing secrets.
7. Confirm sufficient local disk space for a multi-platform build.
8. Prepare the dry-run command with all four immutable tags and the exact requested channel set.

## Read-Only Remote Preflight

Connect in batch mode with a bounded timeout. Do not start an interactive shell when one read-only command is sufficient.

1. Verify SSH connectivity and host identity.
2. Recompute SHA-256 for all four `/root` helpers and compare with the project copies.
3. Run `/root/deploy-hetzner.sh --list`.
4. For all three instances, inspect the effective Compose app image, running container configured image, image ID/digest, state, start time, and latest application build-version log.
5. Inspect the shared Compose services, configured images, state, and health.
6. Probe `https://<domain>/mtl/` for every demo instance. Record the status and response time. An authenticated API returning `401` without a session is not an outage.
7. Run the chosen helper with `--dry-run` and the exact selected instance keys.
8. Save a before table before publishing or resetting anything.

The app-reported version is logged as:

```text
mtl-server started. Build version info:
```

Use the latest matching startup line from each app container. Capture the image release and image build time without recording the server ID.

## Independent Channel And Instance Selection

Release channels and deployment targets are separate user choices:

1. Extract the app image reference for all three instances.
2. Record which channel each instance currently consumes.
3. Ask the user which moving channels to publish.
4. Separately ask which instance keys to update: `regulardemo`, `large`, `beta`, or an explicit combination.
5. Require each selected instance's configured channel to be included in the publication set for a code deployment. Otherwise the pull cannot fetch the newly built app.
6. Do not add other consumers of the same channel to the target list. Record them as untouched and require their running digest, version, and health to remain stable.
7. Choose one action for the selected keys: pull/restart or destructive data reset.

Examples:

- Publish `beta`; reset `beta`: Demo Beta updates. Demo Large may also reference `beta`, but remains on its old running digest.
- Publish `beta`; reset `large`: Demo Large updates. Demo Beta remains untouched.
- Publish `latest`; reset `regulardemo`: only regular Demo updates.
- Publish `beta` and `latest`; reset `beta large regulardemo`: all three update in one explicit reset.

Stop and ask the user if a selected instance consumes a channel that is not being published or any target/action choice is missing.

## Current Helper Semantics

Revalidate these behaviors by reading the current scripts before each deployment:

| Helper | Current effect |
|---|---|
| `/root/docker-delete-reset.sh [--dry-run] [--app-memory-limit LIMIT] <keys...>` | Optionally persists and verifies an existing app Compose memory override, then stops and wipes only the selected keys, refreshes shared services, pulls every selected stack, starts them, and prunes unused images. It keeps a timestamped override backup. At least one key is required. |
| `/root/docker-stop-start.sh [--dry-run] [--app-memory-limit LIMIT \| --standard-memory-profile] <keys...>` | Optionally persists and verifies an existing app Compose memory override or the validated standard app/database memory profile, refreshes shared services, then pulls and restarts only the selected keys and prunes unused images. It keeps a timestamped override backup. At least one key is required. It does not wipe demo data. |
| `/root/docker-delete-reset-beta.sh` | Compatibility wrapper that invokes `docker-delete-reset.sh beta`. Do not use it for new automation. |
| `/root/deploy-hetzner.sh` | Creates or updates a domain and performs broader host setup. It is not a routine reset substitute. |

Run exactly one main helper with the approved keys. Use its `--dry-run` before approval.

## Final Approval Summary

Immediately before the first mutation, show:

- Current worktree and commit.
- Full build versus tag-only mode.
- Old and new version constants, including the mandatory app bump for a full code build.
- Four immutable tags.
- Moving channels and full Docker Hub references.
- Multi-platform setting and cache choice.
- Configured channel for every live instance.
- Exact action and instance keys.
- Exact helper command to execute.
- Exact data directories that will be wiped, or a clear statement that no data will be wiped.
- Unselected instances, including those that consume a published channel and must remain on their current running digest.
- Before versions and digests for all three instances.
- Known blockers or mismatches.

Proceed only after explicit approval of this exact plan.

## Remote Execution

1. Run the approved local builder command and keep its complete step summary.
2. Verify every selected immutable and moving tag in the registry. Capture digests.
3. If any local build, push, or verification step fails, stop before SSH mutation.
4. Re-run the remote topology, helper-hash, version, and selected-helper dry-run preflight. Stop on drift.
5. Execute the approved main helper once by its absolute `/root` path with exactly the approved keys.
6. Preserve exit status and concise output. Do not automatically retry a restart, wipe, or reset.

## Deployment Verification

Poll with timestamps and a reasonable bound while forward progress is visible. For each touched instance require:

1. Expected Compose channel reference.
2. Running container state without a restart loop.
3. Running image ID/digest matching the registry digest for the requested channel.
4. Latest startup log reporting the requested immutable app version and build time.
5. Successful HTTPS response for `/mtl/`.
6. No blocking startup, database, demo-generation, or sidecar errors.

Then verify all three demo instances, including untouched ones:

| Check | Required evidence |
|---|---|
| Configured image | Compose reference |
| Running image | Configured container reference and image ID/digest |
| Application build | Reported image version and build time |
| Runtime | State, uptime, restart count, relevant errors |
| HTTP | Status and response time for `/mtl/` |
| Change audit | Before/after digest and version |

For every unselected instance, compare against the before snapshot. It is valid for its channel tag in Docker Hub to move while its running digest stays unchanged. Treat an unapproved restart, digest change, or data reset as a failure.

Also verify Caddy and all currently configured shared services. Do not fail only because an unconfigured historical service name is absent; compare live shared Compose configuration with running services.

## Result Rules

- **PASS:** build/publish succeeds, approved resets succeed, every touched instance runs the expected digest and immutable version, and all demo sanity checks pass.
- **PARTIAL:** publication succeeds but an approved remote phase or required evidence is incomplete. State the published tags and current remote state.
- **FAIL:** wrong target, wrong version/digest, reset failure, restart loop, HTTP failure, helper drift, data-scope violation, or regression on any demo instance.
- **BLOCKED:** preflight reveals helper drift, a selected instance whose configured channel is not being published, or missing target/action authorization before mutation.

Report a before/after table for all three instances and a separate shared-service summary. Never collapse a wrong touched version into an overall PASS.
