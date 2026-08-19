---
name: mtl-deploy-demo
description: Build and publish selected MTL Explorer Docker release channels, update explicitly selected Hetzner demo instances through the approved server helpers, and verify every demo instance. Use for beta/latest demo deployments; do not use for production, ordinary local builds, or server provisioning.
---

# Deploy MTL Demo

Deploy the exact current worktree to the Hetzner demo host without assuming that channel names, Compose files, or helper behavior are unchanged.

Read [references/hetzner-demo-runbook.md](references/hetzner-demo-runbook.md) completely before planning or executing a deployment.

## Required User Decisions

Before building or publishing, determine all of the following. Ask the user for any missing choice:

- Moving channel tags: `beta`, `latest`, or both. Do not include `alpha` unless the user explicitly expands the scope.
- New app immutable version tag. For a full code build, increment the final numeric component of `APP_VERSION_TAG` in `docker-build-deploy.py` by at least one. Propose the smallest increment by default, for example `1.361` to `1.362`, and let the user request a larger value.
- Whether to increment maps, location-search, or BRouter. Keep their existing constants unchanged by default; change only the components the user explicitly selects.
- Whether the request is a full code build. Use a full build by default for code deployment; use tag-only mode only when the user explicitly asks to retag already-published immutable images.
- Target instance keys: one or more of `beta`, `large`, and `regulardemo`. Channel tags do not select instances.
- Remote action: data reset through `docker-delete-reset.sh` or pull/restart without data deletion through `docker-stop-start.sh`.
- Explicit approval for the final target list and, for a data reset, the PostGIS, GPX, media, and logs that will be wiped.

A channel choice never authorizes pulling, restarting, or wiping an instance. An instance choice never authorizes publishing an additional channel.

## Deployment Sequence

1. Inspect repository instructions, worktree state, the current `docker-build-deploy.py`, its `--help`, and the current immutable tag constants. Record the commit and disclose relevant uncommitted changes because the images are built from the current worktree.
2. For a full code build, agree on the app tag and any optional component bumps. Update the selected constants in `docker-build-deploy.py` before generating the dry run. The app constant must be strictly higher than its previous value; changing only the `--app-tag` command-line override is not sufficient. A tag-only operation does not change these constants.
3. Run the read-only local and remote preflight in the runbook. Capture the configured channel, running image digest, reported image version, state, and HTTP result for all demo instances.
4. Recompute server-helper hashes and compare them with the project copies. Inspect current Compose image references. Never rely only on the runbook's dated observation.
5. Keep channel publication and instance deployment independent. Confirm that each selected instance's configured channel is included in the requested publication set. An unselected instance may use the same channel and must remain untouched on its current digest.
6. Stop and alert the user if a selected instance's channel will not be published, a helper cannot target the exact approved keys, a selected stack would not be pulled, helper hashes differ, or remote state changed unexpectedly. Do not improvise manual Docker Compose or deletion commands.
7. Generate and run both dry runs: the exact `docker-build-deploy.py --dry-run` command and the selected remote helper with `--dry-run <keys...>`. Show the old and new version constants, resulting release plan, Docker Hub tags that will move, immutable tags, platforms, remote action, targets, data-wipe scope, and expected untouched instances.
8. Ask for final approval immediately before the first external mutation. The approval summary must explicitly state that Docker Hub tags will be published and named demo data will be erased.
9. Run the full build and push from the repository root. Do not reset the server if any build, push, or registry verification step fails.
10. Re-run the remote preflight immediately before deployment. If it still matches the approved plan, execute the approved helper once with exactly the approved instance keys, preserving its output and exit status.
11. Wait for startup and demo-data processing to settle. Verify the requested immutable app version and registry digest on every touched stack.
12. Run the all-instance sanity check even for instances that were not touched. Verify all demo URLs, configured refs, running digests, app-reported versions, container state, restart/error signals, and shared-service health.
13. Report PASS, PARTIAL, or FAIL with a before/after table for every demo instance. Never report success merely because the channel tag was published or a container is running.

## Safety And Failure Rules

- Treat the reset helpers as destructive. Never run them during reconnaissance, dry-run, or without exact target approval.
- Never infer targets from a Docker channel or domain name. Pass only user-approved instance keys to the helper.
- Use the server-side helpers documented in the repository. Do not hand-write remote wipes, edit remote Compose files, or run `docker compose down/up` manually to bypass a mismatch.
- Do not expose Docker credentials, SSH keys, login passwords, JWTs, cookies, or full environment dumps in logs or reports.
- Keep SSH inspection non-interactive with a connection timeout. Never weaken host-key verification to make a deployment proceed.
- Alert the user immediately when a build/push fails, SSH fails, helper drift appears, a reset fails, readiness stops progressing, a touched version is wrong, an untouched instance regresses, or the final sanity check fails. State the phase, expected state, observed state, and safe next action.
- If publishing succeeds but reset or verification fails, report the published tags and the exact remote state. Do not retry a destructive phase automatically.
- Do not update production or any domain outside the three approved demo instances unless the user explicitly expands scope.

## Evidence

Record concise evidence for:

- User-approved channels, old and new version constants, platforms, action, instance keys, and wipe targets.
- Worktree identity and the exact dry-run and actual build commands.
- Docker Hub publication and registry digest verification.
- Remote helper hashes and preflight topology.
- Helper execution, elapsed time, and exit status.
- Before/after state for all demo instances and shared services.
- HTTP readiness, app-reported version/build time, configured image, running digest, and relevant errors.

Leave deployment evidence uncommitted unless the user asks to keep it in the repository.
