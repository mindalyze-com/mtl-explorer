# Environment settings

MTL Explorer reads its settings from `.env`, next to `docker-compose.yml`. Keep
this file private. If you keep it somewhere else, start and update with
`docker compose --env-file /path/to/mtl.env ...`.

| Setting | Purpose |
| --- | --- |
| `MTL_USER_LOGIN`, `MTL_USER_PASSWORD` | Your login |
| `MTL_POSTGRES_PASSWORD` | Database password |
| `MTL_APP_IMAGE` | Use a different app image |
| `MTL_BROUTER_IMAGE`, `MTL_LOCATION_SEARCH_IMAGE`, `MTL_MAP_IMAGE` | Use different service images |
| `MTL_IMAGE_PULL_POLICY` | When Docker downloads new images |
| `MTL_UPDATES_ENABLED`, `MTL_UPDATES_FEED_URL` | Optional check for new releases |
| `MTL_GOOGLE_HEALTH_CLIENT_ID`, `MTL_GOOGLE_HEALTH_CLIENT_SECRET` | [Google Health](../google-health.md) connection |

After changing `.env`, run `docker compose up -d` to apply it.

The [Compose file](../../../docker-compose.yml) lists all settings and their
defaults. Advanced server settings are in the
[server configuration](../../../mtl-server/src/main/resources/application.yml).
Do not post your `.env` or full configuration publicly; it contains passwords.
