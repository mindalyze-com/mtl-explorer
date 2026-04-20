#!/usr/bin/env python3
# garmin_fit_to_gpx_export.py

import argparse
import getpass
import io
import logging
import os
import re
import xml.etree.ElementTree as ET
import zipfile
from datetime import timezone

import gpxpy
import gpxpy.gpx
from fitparse import FitFile
from garminconnect.client import Client as GarminClient
from gpxpy.gpx import GPXTrackPoint

# Monkey-patch garminconnect to skip broken mobile login strategies that cause 429 errors
GarminClient._mobile_login_cffi = lambda *args, **kwargs: (_ for _ in ()).throw(Exception("Skipping broken mobile login"))
GarminClient._mobile_login_requests = lambda *args, **kwargs: (_ for _ in ()).throw(Exception("Skipping broken mobile login"))

# Suppress garminconnect logging for the skipped strategies to avoid confusing output
logging.getLogger("garminconnect.client").setLevel(logging.ERROR)

_garmin_client: GarminClient | None = None

# --- Logging ---
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

# --- Constants ---
GARMIN_SEMICIRCLES_TO_DEGREES = 180 / 2**31

# --- Utilities ---

def extract_activity_id(activity_arg: str) -> str:
    """
    Accepts a numeric ID or a full Connect URL; returns the numeric ID as string.
    """
    m = re.search(r'(\d{5,})', activity_arg)
    if not m:
        raise ValueError(f"Could not parse activity ID from: {activity_arg}")
    return m.group(1)

def rewrite_root_minimal(xml_text: str, use_gpxtpx: bool, use_gpxx: bool, use_gpxpx: bool) -> str:
    """
    Replace the <gpx ...> start tag with a minimal, standards-based header:
      - default GPX 1.1 namespace + xsi
      - only Garmin namespaces actually used (gpxtpx, gpxx, gpxpx)
      - matching xsi:schemaLocation pairs
    """
    start = xml_text.find("<gpx")
    if start == -1:
        return xml_text
    end = xml_text.find(">", start)
    if end == -1:
        return xml_text

    ns_pairs = [
        ("http://www.topografix.com/GPX/1/1", "http://www.topografix.com/GPX/1/1/gpx.xsd")
    ]
    attrs = [
        'xmlns="http://www.topografix.com/GPX/1/1"',
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
    ]

    if use_gpxtpx:
        attrs.append('xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1"')
        ns_pairs.append((
            "http://www.garmin.com/xmlschemas/TrackPointExtension/v1",
            "http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd",
        ))
    if use_gpxx:
        attrs.append('xmlns:gpxx="http://www.garmin.com/xmlschemas/GpxExtensions/v3"')
        ns_pairs.append((
            "http://www.garmin.com/xmlschemas/GpxExtensions/v3",
            "http://www8.garmin.com/xmlschemas/GpxExtensionsv3.xsd",
        ))
    if use_gpxpx:
        attrs.append('xmlns:gpxpx="http://www.garmin.com/xmlschemas/PowerExtension/v1"')
        ns_pairs.append((
            "http://www.garmin.com/xmlschemas/PowerExtension/v1",
            "http://www.garmin.com/xmlschemas/PowerExtensionv1.xsd",
        ))

    schema_location = " ".join(" ".join(pair) for pair in ns_pairs)
    attrs.append('version="1.1"')
    attrs.append('creator="garmin_fit_to_gpx_export"')
    attrs.append(f'xsi:schemaLocation="{schema_location}"')

    new_root = "<gpx " + " ".join(attrs) + ">"
    return xml_text[:start] + new_root + xml_text[end+1:]

def ensure_dir(path: str) -> str:
    path = os.path.abspath(path)
    os.makedirs(path, exist_ok=True)
    return path

# --- Minimal type inference from FIT session (fallback only) ---

def infer_type_from_fit_session(fitfile: FitFile) -> str | None:
    """
    Return FIT session 'sport' (normalized to lowercase with underscores).
    If 'sport' is absent/empty, return normalized 'sub_sport'.
    Otherwise None.
    """
    try:
        for msg in fitfile.get_messages("session"):
            v = msg.get_values()
            sport = v.get("sport")
            subsport = v.get("sub_sport")
            sport_str = (str(sport).strip().lower().replace(" ", "_")) if sport is not None else ""
            subsport_str = (str(subsport).strip().lower().replace(" ", "_")) if subsport is not None else ""
            if sport_str:
                return sport_str
            if subsport_str:
                return subsport_str
        return None
    except Exception:
        return None


# --- FIT -> GPX conversion ---

def convert_fit_to_gpx(
    fit_filepath: str,
    gpx_filepath: str,
    track_name: str | None = None,
    track_description: str | None = None,
    track_type: str | None = None,  # NEW: pass-through <type>
) -> bool:
    """
    Converts a FIT file to a GPX file, adding name/description and per-point metrics.
    Writes:
      - gpxtpx: hr / cad / atemp (if present)
      - gpxx:   Speed (m/s) (if present)
      - gpxpx:  Power (W)   (if present)
      - <type>: from Connect typeKey or inferred from FIT session (if available)
    All under <extensions> with proper namespaces so BaseCamp accepts it.
    """
    logging.info(f"Starting conversion from '{fit_filepath}' to '{gpx_filepath}'...")

    try:
        fitfile = FitFile(fit_filepath)
    except Exception as e:
        logging.error(f"Error opening or parsing FIT file '{fit_filepath}': {e}")
        return False

    gpx = gpxpy.gpx.GPX()

    # Track container
    trk = gpxpy.gpx.GPXTrack()
    if track_name:
        trk.name = track_name
    if track_description:
        trk.description = track_description
        trk.comment = track_description
        gpx.description = track_description

    # Decide <type>: prefer Connect typeKey; otherwise infer from FIT session
    type_value = track_type or infer_type_from_fit_session(fitfile)
    if type_value:
        trk.type = type_value

    gpx.tracks.append(trk)

    seg = gpxpy.gpx.GPXTrackSegment()
    trk.segments.append(seg)

    point_count = 0
    used_gpxtpx = False
    used_gpxx = False
    used_gpxpx = False

    for record in fitfile.get_messages("record"):
        lat = record.get_value("position_lat")
        lon = record.get_value("position_long")
        if lat is None or lon is None:
            continue

        pt = GPXTrackPoint(
            latitude=lat * GARMIN_SEMICIRCLES_TO_DEGREES,
            longitude=lon * GARMIN_SEMICIRCLES_TO_DEGREES,
        )

        elevation = record.get_value("enhanced_altitude") or record.get_value("altitude")
        if elevation is not None:
            try:
                pt.elevation = float(elevation)
            except Exception:
                pass

        timestamp = record.get_value("timestamp")
        if timestamp is not None:
            # ensure TZ-aware UTC
            pt.time = timestamp.replace(tzinfo=timezone.utc)

        # Build namespaced extensions only
        ext_children: list[ET.Element] = []

        # Garmin TrackPoint extensions (hr/cad/temp)
        tpx = ET.Element('gpxtpx:TrackPointExtension')
        tpx_added = False

        hr = record.get_value("heart_rate")
        if hr is not None:
            n = ET.SubElement(tpx, 'gpxtpx:hr')
            n.text = str(int(hr))
            tpx_added = True

        cad = record.get_value("cadence") or record.get_value("cycling_cadence")
        if cad is not None:
            n = ET.SubElement(tpx, 'gpxtpx:cad')
            n.text = str(int(cad))
            tpx_added = True

        temp = record.get_value("temperature")
        if temp is not None:
            n = ET.SubElement(tpx, 'gpxtpx:atemp')
            n.text = str(int(temp))
            tpx_added = True

        if tpx_added:
            ext_children.append(tpx)
            used_gpxtpx = True

        # Garmin GPX extensions (per-point speed)
        spd = record.get_value("speed")
        if spd is not None:
            gxx = ET.Element('gpxx:TrackPointExtension')
            ET.SubElement(gxx, 'gpxx:Speed').text = f"{float(spd):.3f}"  # m/s
            ext_children.append(gxx)
            used_gpxx = True

        # Garmin Power extension (per-point power)
        pwr = record.get_value("power")
        if pwr is not None:
            pext = ET.Element('gpxpx:PowerExtension')
            ET.SubElement(pext, 'gpxpx:Power').text = str(int(pwr))
            ext_children.append(pext)
            used_gpxpx = True

        if ext_children:
            pt.extensions = ext_children

        seg.points.append(pt)
        point_count += 1

    if point_count == 0:
        logging.warning("No track points with GPS coordinates were found in the FIT file.")
        return False

    try:
        xml = gpx.to_xml(version="1.1")
        xml = rewrite_root_minimal(xml, use_gpxtpx=used_gpxtpx, use_gpxx=used_gpxx, use_gpxpx=used_gpxpx)
        with open(gpx_filepath, 'w', encoding='utf-8') as f:
            f.write(xml)
        logging.info(f"Successfully converted {point_count} track points.")
        logging.info(f"✅ GPX file saved to '{gpx_filepath}'")
        return True
    except Exception as e:
        logging.error(f"Error writing GPX file '{gpx_filepath}': {e}")
        return False

# --- Garmin Connect download ---

def fetch_activity_meta(activity_id: str) -> tuple[str | None, str | None, str | None]:
    """
    Query Connect for activity JSON and pull name/description/typeKey if present.
    Returns (name, description, type_key)
    """
    resp = _garmin_client.request("GET", "connectapi", f"/activity-service/activity/{activity_id}")
    details = resp.json()
    name = (
        details.get("activityName")
        or details.get("metadataDTO", {}).get("activityName")
    )
    desc = (
        details.get("description")
        or details.get("metadataDTO", {}).get("description")
    )
    type_key = (
        (details.get("activityType") or {}).get("typeKey")
        or (details.get("activityTypeDTO") or {}).get("typeKey")
    )
    return name, desc, type_key

def download_original_fit(activity_id: str, out_fit_path: str) -> None:
    """
    Download the 'original' file from Connect, which is typically a ZIP containing the FIT.
    Extracts the first .fit inside (or writes the raw FIT if not zipped).
    """
    blob = _garmin_client.download(f"download-service/files/activity/{activity_id}")

    # ZIP magic header?
    if blob[:2] == b"PK":
        with zipfile.ZipFile(io.BytesIO(blob)) as zf:
            fit_names = [n for n in zf.namelist() if n.lower().endswith(".fit")]
            if not fit_names:
                raise RuntimeError("Downloaded ZIP contains no .fit file.")
            data = zf.read(fit_names[0])
    else:
        data = blob

    with open(out_fit_path, "wb") as f:
        f.write(data)

# --- CLI / Main ---

def main():
    parser = argparse.ArgumentParser(
        description="Download a Garmin activity (original FIT) and convert to GPX with rich metadata.",
        epilog="Example: python garmin_fit_to_gpx_export.py --email you@domain.tld --activity 20036222429 --save-dir ./exports"
    )
    parser.add_argument("--activity", required=True, help="Activity ID or full Garmin Connect activity URL.")
    parser.add_argument("--email", required=True, help="Garmin Connect login email/username.")
    parser.add_argument("--save-dir", default=".", help="Directory to store the FIT and GPX (default: current directory).")
    parser.add_argument("--output", help="Custom GPX filename. If relative, it's created under --save-dir. Default: activity_<id>.gpx")

    args = parser.parse_args()

    # Resolve activity ID
    activity_id = extract_activity_id(args.activity)

    # Password: env or prompt
    email = args.email
    password = os.environ.get("GARMIN_PWD")
    if password:
        logging.info("Using password from GARMIN_PWD environment variable.")
    else:
        password = getpass.getpass(f"Enter password for {email}: ")

    global _garmin_client
    logging.info("Attempting to log in to Garmin Connect...")

    try:
        _garmin_client = GarminClient()
        # Suppress expected 429 fallthrough warnings from the login strategy cascade
        logging.getLogger("garminconnect.client").setLevel(logging.ERROR)
        _garmin_client.login(email, password)
        logging.getLogger("garminconnect.client").setLevel(logging.WARNING)
        logging.info("Login successful!")
    except Exception as e:
        logging.error(f"Login failed: {e}")
        return

    # Prepare output directory and filenames
    save_dir = ensure_dir(args.save_dir)
    logging.info(f"Saving files to: {save_dir}")

    fit_path = os.path.join(save_dir, f"activity_{activity_id}.fit")
    # If a custom output name is passed and it's relative, place it under save_dir
    if args.output:
        out_gpx = args.output
        if not os.path.isabs(out_gpx):
            out_gpx = os.path.join(save_dir, out_gpx)
    else:
        out_gpx = os.path.join(save_dir, f"activity_{activity_id}.gpx")

    # Fetch metadata (now also returns typeKey)
    name = None
    desc = None
    type_key = None
    try:
        logging.info(f"Fetching details for activity {activity_id}...")
        name, desc, type_key = fetch_activity_meta(activity_id)
    except Exception as e:
        logging.warning(f"Could not fetch activity details (name/description/type): {e}")

    # Download FIT (original) to chosen directory/name (keep FIT)
    try:
        logging.info(f"Downloading FIT file for activity {activity_id} to '{fit_path}'...")
        download_original_fit(activity_id, fit_path)
        logging.info(f"FIT file saved as '{fit_path}'")
    except Exception as e:
        logging.error(f"Failed to download FIT file: {e}")
        return

    # Convert -> GPX (keep the FIT file; do NOT delete)
    ok = convert_fit_to_gpx(
        fit_path,
        out_gpx,
        track_name=name,
        track_description=desc,
        track_type=type_key,   # <-- write <type> if Connect provides it
    )

    if ok:
        logging.info(f"✅ Done. GPX: {out_gpx}  |  FIT kept: {fit_path}")
    else:
        logging.error("Conversion failed. Original FIT has been kept.")

    logging.info("Process finished.")

if __name__ == "__main__":
    main()
