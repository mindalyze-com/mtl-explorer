# Current Helper Scripts

Backups copied from `/root` on the Hetzner demo server `188.245.192.50` on 2026-05-24.

| Script | Purpose |
|---|---|
| `deploy-hetzner.sh` | Creates or updates MTL Explorer demo stacks, shared sidecars, and Caddy routing on Hetzner. |
| `docker-stop-start.sh` | Pulls shared sidecars, then stops, pulls, and restarts the demo, demo-large, and demo-beta stacks. |
| `docker-delete-reset.sh` | Pulls shared sidecars, stops demo and demo-large, wipes demo data, then starts them again. |
| `docker-delete-reset-beta.sh` | Pulls shared sidecars, stops demo-beta, wipes demo data, pulls beta images, then starts it again. |

These scripts are server-side operational helpers. Do not run them from the local workspace.
