# Google Health setup

Google Health is an optional **beta feature**. It keeps a read-only copy of your
Google Health data (exercise, sleep, activity, measurements, and heart rate) on
your own server, and imports exercise routes as tracks. Errors and sync
interruptions may occur. To use it, see the
[Health guide](../user/health/google-health.md). This page covers setup.

Before you start:

- You need your own Google Cloud project with access to the Google Health API.
  Google decides who gets access, and its rules can change. Check
  [Google's requirements](https://developers.google.com/health/setup) first.
- Your Google Account must be linked to an active Google Health profile. If
  needed, create one or move your Fitbit account in the Google Health mobile
  app; see [Google's account requirements](https://developers.google.com/health/migration).
- In Google's Testing mode, you must reconnect every seven days. Your stored
  data stays.
- One Google account per MTL Explorer installation.

Health data and route locations are sensitive. Protect the database, backups,
login, and reverse proxy. Health data is stored in the database, and routes
in `./data/gpx/google-health/`. Back up `./data/postgis` and `./data/gpx`
together.

Without Google credentials, the feature simply stays unconfigured. To turn it
off completely, set `MTL_GOOGLE_HEALTH_ENABLED=false` in `.env`.

Each installation connects to Google directly, so your health data never passes
through a third-party service. That is why every installation needs its own
Google setup.

### One-time installation setup

In MTL Explorer, open **Settings → Import & sync → Google Health → Start setup**.
The setup assistant shows the scopes, an `.env` template, server checks, and
links to each Google page: [Cloud Console](https://console.cloud.google.com/),
[API Library](https://console.cloud.google.com/apis/library/health.googleapis.com),
[Auth Platform](https://console.cloud.google.com/auth/overview),
[Audience](https://console.cloud.google.com/auth/audience),
[OAuth Clients](https://console.cloud.google.com/auth/clients), and
[Data Access](https://console.cloud.google.com/auth/scopes).

1. Select a Google Cloud project and enable **Google Health API**.
2. Open **Google Auth Platform**. If Google shows **Get started**, fill in:
   - **App name:** enter `MTL Explorer`.
   - **User support email:** select your email, then select **Next**.
   - **Audience:** select **External** unless all users belong to the same Google
     Workspace organization, then select **Next**.
   - **Contact Information:** enter your email, then select **Next**.
   - **Finish:** accept the user-data policy, then select **Continue** and
     **Create**.
3. Open **Audience**. Check that the publishing status is **Testing** and the
   user type is **External**. Under **Test users**, select **+ Add users**, enter
   the Google account that owns the health data, and select **Save**. Check
   that the account appears in the list.

   Google will warn that the app is unverified. This is expected. Keep the app
   in **Testing**: this is the supported setup. You don't need to publish the
   app or have it verified by Google. The only drawback is reconnecting once a week.

4. Open **Clients** and create an OAuth client:
   - **Application type:** select **Desktop app**.
   - **Name:** enter `MTL Explorer`.
   - Select **Create**, then **Download JSON** and save the file.
   - **Before closing Google's dialog, copy the Client ID and Client secret.
     You need both in step 6.**

5. Open **Data Access**, select **Add or remove scopes**, and add the four
   read-only scopes:

   ```text
   https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly
   https://www.googleapis.com/auth/googlehealth.health_metrics_and_measurements.readonly
   https://www.googleapis.com/auth/googlehealth.sleep.readonly
   https://www.googleapis.com/auth/googlehealth.location.readonly
   ```

   **Tip:** Select **Copy all four scopes** in the assistant, paste them into
   **Manually add scopes**, then select **Add to table**, **Update**, and **Save**.
   The first three are required. Location is optional and only needed for
   exercise routes.

6. Add the client ID and secret to `.env`, next to `docker-compose.yml`. Create
   `.env` first if it is missing:

   ```env
   MTL_GOOGLE_HEALTH_CLIENT_ID=your-client-id.apps.googleusercontent.com
   MTL_GOOGLE_HEALTH_CLIENT_SECRET=your-client-secret
   ```

   **Keep this secret.** If you change it later, you must reconnect Google
   Health. Your stored health data stays.

7. Apply the change:

   ```bash
   docker compose up -d
   ```

   Do not use `docker compose restart`; it does not load the new settings. Wait
   until MTL Explorer is back, sign in, return to the assistant, and select
   **Check again**.

If you keep `.env` elsewhere, use `docker compose --env-file "/path/to/mtl.env" up -d`.
On a NAS, add both settings to the stack environment and recreate the app container.

**Advanced:** if MTL Explorer runs behind HTTPS and you prefer an automatic
callback, set `MTL_GOOGLE_HEALTH_REDIRECT_URI` to its public HTTPS callback
address and use a **Web application** OAuth client with exactly the same
address. Leave it unset for the normal setup.

See [Google Health authorization](../architecture/health/authorization.md) for
why the setup works this way.

### Connect the Google account

1. Open **Settings → Import & sync → Google Health**.
2. In the setup assistant, select **Open Google consent**.
3. On Google's **Select what this app can access** screen, select **Select all**
   and **Continue**. You can leave Location unchecked if you don't need
   exercise routes.
4. Google opens `http://127.0.0.1:1/mtl-explorer-oauth` and the browser shows an
   error. This is expected. Copy the complete address from the address bar.
5. Return to MTL Explorer, paste the address, and select **Complete connection**
   within ten minutes.
6. Select **Open Health**, or open **Manage** to review access. Follow the sync
   link to **Processing** to watch the first import.

The last 24 hours appear first; the rest of the week and older history follow
over time. Temporary errors are retried automatically. **Settings → Processing**
shows what failed and when it is retried next.

### Reconnect

When access is about to expire or has expired, Settings shows a message. Select
**Reauthorize**, open Google consent, copy the new address, and paste it into
MTL Explorer. Your Google setup and stored data stay unchanged.

### Diagnose Google Health

To see recent Google Health activity in the server log:

```bash
docker compose logs --since 30m app | grep 'Google Health'
```

The log never contains tokens, health data, or locations. When reporting a
problem, include the complete error and stack trace around the failing job.

To stop syncing, select **Disconnect Google**; your data stays. To delete the
local copy, disconnect first, then select **Delete local mirror** and type
`DELETE`. This removes only Google Health records and routes.

### Synchronization settings

Most installations don't need to change these. Add them under
`mtl.google-health` in the server configuration.

| Setting | Default | Meaning |
| --- | --- | --- |
| `history-start-date` | `2010-01-01` | Oldest day to import |
| `recent-window` | `P7D` | Days that are checked again for late changes |
| `recent-fast-window` | `PT24H` | Range checked on every sync |
| `recent-full-interval` | `PT24H` | How often the whole recent window is checked |
| `sync-interval` | `PT30M` | Time between syncs |
| `cycle-deadline-margin` | `PT1M` | Time kept free before the next sync |
| `history-budget-fraction` | `0.5` | Share of sync time used for older history |
| `history-stream-quantum` | `PT1M` | Time needed to start another page of history |
| `database-transaction-timeout` | `PT5M` | Time limit for saving one batch |
| `work-failure-limit` | `4` | Attempts before a failing range is skipped |
| `work-retry-max-delay` | `PT6H` | Longest wait between retries |
| `max-chunk-rows` / `max-chunk-bytes` | `100000` / `67108864` | Size limit per batch |

See [synchronization](../architecture/health/synchronization.md) for how syncing
works and which values are allowed.
