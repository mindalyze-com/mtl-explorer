# Garmin Export Flow Diagram

## Complete System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         MyTrailLog Application                          │
│                         (Java Spring Boot)                              │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐ │
│  │                    GarminExporter.java                           │ │
│  │                                                                  │ │
│  │  1. Writes exclude file (already imported activities)           │ │
│  │  2. Calls primary export tool                                   │ │
│  │  3. Parses output for HTTP 408 errors                          │ │
│  │  4. Triggers fallback for failed activities                    │ │
│  └──────────────────────────────────────────────────────────────────┘ │
│                         │                           │                   │
│                         │                           │                   │
│                         ▼                           ▼                   │
└─────────────────────────┼───────────────────────────┼───────────────────┘
                          │                           │
                          │                           │
         ┌────────────────┘                           └────────────────┐
         │                                                             │
         │                                                             │
         ▼                                                             ▼
┌─────────────────────────┐                          ┌──────────────────────────┐
│  PRIMARY EXPORT TOOL    │                          │  FALLBACK EXPORT TOOL    │
│  /app/garmin_export/    │                          │  /app/garmin_fit_export/ │
├─────────────────────────┤                          ├──────────────────────────┤
│                         │                          │                          │
│  run_export.sh          │                          │  run_fit_export.sh       │
│    │                    │                          │    │                     │
│    ├─> Uses:            │                          │    ├─> Uses:             │
│    │   venv_gcexport/   │                          │    │   venv_fit_export/  │
│    │   bin/python3      │                          │    │   bin/python3       │
│    │                    │                          │    │                     │
│    └─> Executes:        │                          │    └─> Executes:         │
│        gcexport.py      │                          │        garmin_fit_to_    │
│                         │                          │        gpx_export.py     │
│                         │                          │                          │
│  Downloads:             │                          │  Downloads:              │
│  ✓ All activities       │                          │  ✓ Single activity       │
│  ✓ As GPX files         │                          │  ✓ As FIT file           │
│  ✓ Bulk operation       │                          │  ✓ Converts to GPX       │
│                         │                          │                          │
│  Issues:                │                          │  Advantages:             │
│  ✗ Large GPX = HTTP 408 │                          │  ✓ Smaller FIT files     │
│  ✗ Timeout errors       │                          │  ✓ No timeouts           │
│                         │                          │  ✓ Full metrics          │
└─────────────────────────┘                          └──────────────────────────┘
         │                                                             │
         │                                                             │
         ▼                                                             ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                           Garmin Connect                                 │
│                        (External Service)                                │
│                                                                          │
│  API Endpoints:                                                          │
│  • /activity-service/activity/{id} - Activity metadata                  │
│  • /download-service/export/gpx/activity/{id} - GPX download (can 408)  │
│  • /download-service/files/activity/{id} - FIT download (smaller)       │
└──────────────────────────────────────────────────────────────────────────┘
         │                                                             │
         ▼                                                             ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                        Output Directory: /app/gpx/                       │
│                                                                          │
│  From Primary Tool:                    From Fallback Tool:              │
│  • activity_123.gpx                    • activity_456.fit               │
│  • activity_124.gpx                    • activity_456.gpx               │
│  • activity_125.gpx                    • activity_457.fit               │
│                                        • activity_457.gpx               │
└──────────────────────────────────────────────────────────────────────────┘
```

## Flow Sequence

### Normal Flow (No Errors)

```
1. Java calls: /app/garmin_export/run_export.sh <user> <pass> <exclude_file>
2. Script activates: venv_gcexport/bin/python3
3. Executes: gcexport.py --count all
4. Downloads all new activities as GPX
5. Copies to: /app/gpx/
6. Java logs: "Success"
```

### Fallback Flow (HTTP 408 Error)

```
1. Java calls: /app/garmin_export/run_export.sh <user> <pass> <exclude_file>
2. Script activates: venv_gcexport/bin/python3
3. Executes: gcexport.py --count all
4. Downloads activities...
5. Activity 20568140940: HTTP Error 408 (GPX too large)
6. gcexport.py fails and logs error
7. Java parses output: finds "HTTP Error 408.*activity/20568140940"
8. Java extracts activity ID: 20568140940
9. Java calls: /app/garmin_fit_export/run_fit_export.sh <user> <pass> 20568140940 /app/gpx
10. Script activates: venv_fit_export/bin/python3
11. Executes: garmin_fit_to_gpx_export.py --activity 20568140940
12. Downloads: activity_20568140940.fit (small, no timeout)
13. Converts: FIT → GPX with all metrics
14. Saves: activity_20568140940.gpx and .fit to /app/gpx/
15. Java logs: "✅ Successfully exported activity 20568140940 using FIT export tool"
```

## Virtual Environment Isolation

```
System Python (Debian)
    │
    ├── NOT USED (no system-wide pip installs)
    │
    ├─── /app/garmin_export/venv_gcexport/
    │    └── Python 3.9 + gcexport dependencies
    │        ├── requests
    │        ├── cloudscraper
    │        ├── garth
    │        └── python-dateutil
    │
    └─── /app/garmin_fit_export/venv_fit_export/
         └── Python 3.9 + FIT export dependencies
             ├── garth
             ├── fitparse
             └── gpxpy
```

## Error Detection Regex

```python
Pattern: "HTTP Error 408.*?/activity/(\\d+)"

Matches:
✓ "HTTP Error 408: Request Timeout"
✓ "Failed. Got an HTTP error 408 for https://connect.garmin.com/download-service/export/gpx/activity/20568140940"
✓ "urllib.error.HTTPError: HTTP Error 408: Request Timeout"

Extracts:
→ Activity ID: 20568140940
```

## GPX vs FIT Comparison

| Aspect              | GPX (Primary Tool)        | FIT (Fallback Tool)      |
|---------------------|---------------------------|--------------------------|
| **File Size**       | Large (10+ MB)            | Small (1-2 MB)           |
| **Format**          | XML (text)                | Binary                   |
| **Download Speed**  | Slow                      | Fast                     |
| **Timeout Risk**    | High (HTTP 408)           | Low                      |
| **Data Included**   | All metrics               | All metrics              |
| **Conversion**      | Direct download           | FIT → GPX conversion     |
| **Use Case**        | Normal activities         | Large/long activities    |

## Configuration Properties

```yaml
mtl:
  garmin-sync:
    enabled: false
    wrapper-program: /app/garmin_export/run_export.sh
    exclude_activities-file: /app/garmin_export/garmin_exclude_activities.json
    user-name: your-email@example.com
    user-password: your-password
    fit-export-wrapper-program: /app/garmin_fit_export/run_fit_export.sh
    fit-export-save-dir: /app/gpx
```

## Docker Build Process

```bash
docker build -t mytraillog .

Steps:
1. Install base: Debian + OpenJDK 21
2. Install: python3-pip python3-venv
3. Copy: docker/garmin_export/ → /app/garmin_export/
4. Run: setup_garmin_export.sh
   - Downloads gcexport v4.6.2
   - Creates venv_gcexport
   - Installs dependencies
5. Copy: docker/garmin_fit_export/ → /app/garmin_fit_export/
6. Run: setup_garmin_fit_export.sh
   - Creates venv_fit_export
   - Installs garth, fitparse, gpxpy
7. Copy: Spring Boot JAR
8. Start: Java application
```

## Success Indicators

### Primary Tool Success
```
Garmin Export process exited with code=0 in dtSeconds=45.23
```

### Fallback Triggered
```
Found 1 activities that failed with HTTP 408 timeout. Attempting to retry with FIT export...
Retrying activity 20568140940 using FIT export tool
```

### Fallback Success
```
[FIT-EXPORT] Login successful!
[FIT-EXPORT] Downloading FIT file for activity 20568140940...
[FIT-EXPORT] Successfully converted 12543 track points.
[FIT-EXPORT] ✅ GPX file saved to '/app/gpx/activity_20568140940.gpx'
✅ Successfully exported activity 20568140940 using FIT export tool
```
