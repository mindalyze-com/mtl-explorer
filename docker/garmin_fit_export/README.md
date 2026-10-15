# Garmin FIT to GPX export helper

MTL Explorer uses this helper to retry selected failed Garmin GPX exports.
It downloads the original activity FIT file and creates a GPX file. Both files
are retained. It can also be invoked for a known activity ID.

## Files and environment

- `garmin_fit_to_gpx_export.py`: authentication, FIT download, and conversion.
- `install_fit_export.sh`: installs the selected packages into
  `venv_fit_<profile>`; an existing profile is reused.
- `run_fit_export.sh`: selects that environment and invokes the Python tool.

The [Dockerfile](../../Dockerfile) supplies the default profile and packages.
Dependencies are `garth`, `fitparse`, and `gpxpy`. For the default profile,
the runtime interpreter is `/app/garmin_fit_export/venv_fit_default/bin/python3`.

## Usage inside the app container

MTL Explorer normally invokes the wrapper itself:

```text
run_fit_export.sh <username> <password> <activity_id> <save_dir> [profile]
```

For a direct call, use the installed interpreter and provide `GARMIN_PWD` in
the process environment, or allow the Python tool to prompt for the password:

```sh
/app/garmin_fit_export/venv_fit_default/bin/python3 \
  /app/garmin_fit_export/garmin_fit_to_gpx_export.py \
  --email your-email@example.com \
  --activity 123456789 \
  --save-dir /app/mtl-data/gpx
```

The output names are `activity_123456789.fit` and `activity_123456789.gpx`.
The standard Compose configuration points the automatic fallback at
`/app/mtl-data/gpx`. Standalone callers choose their own destination.

## Conversion limits

The converter emits records with GPS coordinates, available elevation and time,
activity metadata, and supported heart-rate, cadence, temperature, speed, and
power extensions. Records without coordinates are skipped. It is not a
lossless conversion of every FIT message or device field; retain the FIT file.

Those sensor extensions are preserved in the exported GPX but are not imported
as measurement series by MTL Explorer's track parser. Track power and energy
are estimated; time-correlated heart rate comes from the optional
[Google Health connection](../../documentation/user/health/google-health.md).

Smaller FIT downloads can help with GPX export failures, but authentication,
network, download, and parsing errors can still occur. The server captures
wrapper output for **Settings → Import & sync → Garmin**. See the
[export flow](../../documentation/architecture/garmin-export.md) for the actual retry selection rule.
