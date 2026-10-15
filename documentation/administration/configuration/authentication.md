# Login and credentials

The default login is user `mtl` with password `change-me`. **Change it before
other people or the internet can reach MTL Explorer.**

Your login is set in a file called `.env`, next to `docker-compose.yml`:

```text
mtl-explorer/
├── docker-compose.yml
├── .env                 create this file here
└── data/
```

Create it on the machine that runs Docker, not inside the container or `data/`.

1. Create the file and allow only your user to read it.

   On macOS or Linux:

   ```bash
   touch .env
   chmod 600 .env
   ```

   On Windows PowerShell:

   ```powershell
   if (-not (Test-Path .env)) { New-Item -ItemType File -Path .env }
   ```

2. Add your login:

   ```env
   MTL_USER_LOGIN=your-user
   MTL_USER_PASSWORD=change-this-password
   ```

3. Apply it:

   ```bash
   docker compose up -d
   ```

Do not share or commit `.env`. To keep it somewhere else, pass it on every start
and update:

```bash
docker compose --env-file "/path/to/mtl.env" up -d
```

MTL Explorer serves plain HTTP on port `18080`. For HTTPS, put a
[reverse proxy](reverse-proxy.md) in front of it.

## Login reference

**Settings → Session** shows a login reference (`ClientLoginRef`). If you report
a problem, include it so the matching log entries can be found. It is not a
password and cannot be used to sign in.
