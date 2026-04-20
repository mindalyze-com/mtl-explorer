# Garmin FIT to GPX Export Tool

This directory contains a Python tool that downloads Garmin activity files in FIT format and converts them to GPX with full metadata and metrics.

## Purpose

This tool serves as a **fallback** for the primary Garmin export tool (`gcexport.py`). When large GPX files cause HTTP 408 timeout errors during download, this tool:
1. Downloads the smaller FIT file instead
2. Converts it to GPX with all track points and metrics preserved
3. Saves both the FIT and GPX files

## Files

- **`garmin_fit_to_gpx_export.py`**: Main Python script
- **`setup_garmin_fit_export.sh`**: Setup script that creates a virtual environment and installs dependencies
- **`run_fit_export.sh`**: Wrapper script to run the tool for a specific activity
- **`venv_fit_export/`**: Python virtual environment (created during Docker build)

## Virtual Environment

This tool uses its own isolated Python virtual environment to avoid conflicts with other Python tools.

### Setup (during Docker build)
```bash
python3 -m venv venv_fit_export
source venv_fit_export/bin/activate
pip install garth fitparse gpxpy
```

### Runtime
The `run_fit_export.sh` script automatically uses the virtual environment:
```bash
/app/garmin_fit_export/venv_fit_export/bin/python3 garmin_fit_to_gpx_export.py ...
```

## Dependencies

Installed in the virtual environment:
- **garth**: Garmin Connect authentication and API client
- **fitparse**: FIT file parser
- **gpxpy**: GPX file creation and manipulation

## Usage

### Via Wrapper Script
```bash
./run_fit_export.sh <username> <password> <activity_id> <save_dir>
```

Example:
```bash
./run_fit_export.sh "user@example.com" "password123" "20568140940" "/app/gpx"
```

### Direct Python Call
```bash
source venv_fit_export/bin/activate
python3 garmin_fit_to_gpx_export.py \
  --email "user@example.com" \
  --activity "20568140940" \
  --save-dir "/app/gpx"
```

## Features

The tool downloads FIT files and converts them to GPX with:
- ✅ Full GPS track points (lat/lon/elevation/time)
- ✅ Heart rate data (gpxtpx:hr)
- ✅ Cadence data (gpxtpx:cad)
- ✅ Temperature (gpxtpx:atemp)
- ✅ Speed (gpxx:Speed)
- ✅ Power (gpxpx:Power)
- ✅ Activity name and description
- ✅ Activity type (running, cycling, etc.)
- ✅ Standards-compliant GPX 1.1 format
- ✅ Compatible with Garmin BaseCamp

## Integration

This tool is automatically invoked by the Java application (`GarminExporter.java`) when:
1. The primary gcexport tool encounters HTTP 408 errors
2. The error log contains activity IDs that failed to download
3. The Java code parses these IDs and calls this tool as a fallback

## Output

For activity `20568140940`, the tool creates:
- `/app/gpx/activity_20568140940.fit` (original FIT file, kept for reference)
- `/app/gpx/activity_20568140940.gpx` (converted GPX file)

Both files are preserved for debugging and backup purposes.

## Advantages Over GPX Direct Download

1. **Smaller files**: FIT files are binary and much smaller than GPX
2. **No timeouts**: Smaller files don't trigger HTTP 408 errors
3. **Full data**: FIT files contain all metrics and track points
4. **Reliable**: Direct from Garmin's original activity data

## Logging

The script provides detailed logging:
- Login status
- Download progress
- Conversion metrics (number of track points)
- Success/failure indicators with emojis
- All output is captured by the Java application

## Error Handling

- Connection errors: Logged and reported to Java
- Authentication failures: Logged with clear messages
- FIT parsing errors: Gracefully handled with error messages
- Missing GPS data: Warned but continues processing other points
