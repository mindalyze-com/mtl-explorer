import csv
import json
import math
import random
import sys
from datetime import datetime, timedelta
from pathlib import Path

# ==========================================
# CONFIGURATION
# ==========================================
# The folder containing your raw dataset(s) (e.g., train.csv)
INPUT_FOLDER = '/Users/pheusser/Downloads/taxi+service+trajectory+prediction+challenge+ecml+pkdd+2015/'
TRACK_LIMIT = 5000

# Two-bucket stratified sampling:
# FAR_RATIO  = fraction of output tracks that come from the outer/peripheral pool
# FAR_CUTOFF = percentile threshold — tracks whose centroid distance from the
#              auto-detected center is at or above this percentile go into the
#              "far" bucket.  Everything below goes into the "near" bucket.
FAR_RATIO  = 0.20   # 20% of output tracks will be from peripheral regions
FAR_CUTOFF = 0.80   # top 20% most-distant tracks form the "far" pool

# Calculate the time window (from 5 years ago up to exactly right now)
END_DATE = datetime.now()
START_DATE = END_DATE - timedelta(days=5 * 365)


# ==========================================
# HELPER FUNCTIONS
# ==========================================
def track_centroid(coordinates):
    """Return (lat, lon) centroid of a list of [lon, lat] pairs."""
    lats = [c[1] for c in coordinates]
    lons = [c[0] for c in coordinates]
    return sum(lats) / len(lats), sum(lons) / len(lons)


def haversine_km(lat1, lon1, lat2, lon2):
    """Great-circle distance in km between two (lat, lon) points."""
    R = 6371.0
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def median_center(centroids):
    """Return the (lat, lon) geometric median (coordinate-wise median)."""
    lats = sorted(c[0] for c in centroids)
    lons = sorted(c[1] for c in centroids)
    mid = len(lats) // 2
    if len(lats) % 2 == 1:
        return lats[mid], lons[mid]
    return (lats[mid - 1] + lats[mid]) / 2, (lons[mid - 1] + lons[mid]) / 2


def get_realistic_start_time(start_date, end_date):
    """
    Generates a highly realistic timestamp combining app growth trends,
    daily commute waves (with weekend adjustments), and micro-randomness.
    """
    # 1. LONG-TERM TREND: purely cyclical waves so the chart has real peaks/troughs
    #    with no monotonic growth bias.
    #
    #    We bucket time into weeks and assign each week an additive weight:
    #      - baseline: flat 1.0 (no growth ramp)
    #      - primary:  ~2-3 full activity cycles over 5 years (major swings)
    #      - seasonal: ~annual rhythm layered on top (smaller swings)
    #
    #    Adjust the phase/frequency constants below to taste:
    #      primary_cycles  – how many major peaks over the whole period
    #      seasonal_cycles – how many minor (seasonal) oscillations
    total_days = (end_date - start_date).days
    total_weeks = total_days // 7 + 1
    total_years = total_days / 365.25

    primary_cycles  = total_years / 1.8   # ~1 big wave every 1.8 years → ~2-3 major peaks in 5 years
    seasonal_cycles = total_years         # Exactly 1 seasonal wave per year automatically scaled
    primary_phase   = 0.3   # shifts the starting phase of the primary wave
    seasonal_phase  = 1.2   # shifts the seasonal rhythm

    week_weights = []
    for w in range(total_weeks):
        t = w / total_weeks                                         # 0 → 1
        # Additive combination: no growth ramp, just waves with a flat baseline
        primary_val  = 0.8 * math.sin(2 * math.pi * t * primary_cycles  + primary_phase)
        seasonal_val = 0.3 * math.cos(2 * math.pi * t * seasonal_cycles + seasonal_phase)
        week_weights.append(max(0.05, 1.0 + primary_val + seasonal_val))

    chosen_week      = random.choices(range(total_weeks), weights=week_weights, k=1)[0]
    day_in_week      = random.randint(0, 6)
    random_day_offset = min(chosen_week * 7 + day_in_week, total_days - 1)
    chosen_date = start_date + timedelta(days=random_day_offset)

    # 2. SHORT-TERM WAVES: Daily Commute Cycle (Weighted Distribution)
    is_weekend = chosen_date.weekday() >= 5  # 5 is Saturday, 6 is Sunday

    if is_weekend:
        # Weekend behavior: Lazy mornings, peak activity in early afternoon
        hour_weights = [
            3,  2,  1,  1,  1,  1,   # 00:00 - 05:00 (Late night stragglers)
            2,  5,  10, 20, 30, 40,  # 06:00 - 11:00 (Slow morning buildup)
            45, 45, 40, 35, 30, 30,  # 12:00 - 17:00 (Afternoon activity)
            25, 25, 20, 15, 10, 5    # 18:00 - 23:00 (Winding down)
        ]
    else:
        # Weekday behavior: Standard dual-wave commute
        hour_weights = [
            1,  1,  1,  1,  2,  5,   # 00:00 - 05:00 (Dead of night)
            15, 30, 45, 30, 20, 20,  # 06:00 - 11:00 (Morning commute wave)
            25, 25, 30, 40, 50, 60,  # 12:00 - 17:00 (Afternoon buildup & Evening peak)
            45, 30, 20, 15, 10, 5    # 18:00 - 23:00 (Winding down)
        ]

    chosen_hour = random.choices(range(24), weights=hour_weights, k=1)[0]

    # 3. MICRO-RANDOMNESS: Exact minute and second
    chosen_minute = random.randint(0, 59)
    chosen_second = random.randint(0, 59)

    return chosen_date.replace(hour=chosen_hour, minute=chosen_minute, second=chosen_second)


def print_progress_bar(iteration, total, prefix='', suffix='', length=40, fill='█'):
    """Call in a loop to create a terminal progress bar."""
    percent = ("{0:.1f}").format(100 * (iteration / float(total)))
    filled_length = int(length * iteration // total)
    bar = fill * filled_length + '-' * (length - filled_length)
    sys.stdout.write(f'\r{prefix} |{bar}| {percent}% {suffix}')
    sys.stdout.flush()
    if iteration == total:
        print()


# ==========================================
# MAIN EXECUTION
# ==========================================
def main():
    input_dir = Path(INPUT_FOLDER)

    # Ensure input folder exists
    if not input_dir.exists() or not input_dir.is_dir():
        print(f"Error: The input folder '{INPUT_FOLDER}' does not exist.")
        print("Please create it and place your CSV dataset(s) inside.")
        return

    # Create the output subfolder with a "now" timestamp
    timestamp_str = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    output_dir = input_dir / f"extracted_tracks_{timestamp_str}"
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"📂 Input directory: {input_dir.resolve()}")
    print(f"📁 Output directory: {output_dir.resolve()}")
    print("-" * 50)

    # Find all CSV files in the input folder
    csv_files = list(input_dir.glob('*.csv'))
    if not csv_files:
        print(f"No .csv files found in '{INPUT_FOLDER}'.")
        return

    # 1. PASS 1: SCAN AND COLLECT LIGHTWEIGHT METADATA ONLY
    print("⏳ Phase 1: Pass 1 - Scanning dataset and computing track centroids...")

    track_metadata = []  # Lightweight tuples only: (TRIP_ID, centroid_lat, centroid_lon)

    for csv_file in csv_files:
        print(f"   Reading: {csv_file.name}")
        with open(csv_file, mode='r', encoding='utf-8') as file:
            csv_reader = csv.DictReader(file)

            for index, row in enumerate(csv_reader):
                polyline_str = row.get('POLYLINE', '')

                if not polyline_str or polyline_str == "[]":
                    continue

                try:
                    coordinates = json.loads(polyline_str)
                except (json.JSONDecodeError, ValueError):
                    continue

                if len(coordinates) < 2:
                    continue

                centroid = track_centroid(coordinates)
                track_metadata.append((row['TRIP_ID'], centroid[0], centroid[1]))

                if index % 50000 == 0:
                    sys.stdout.write(f"\r   ... Scanned {index:,} valid lines ...")
                    sys.stdout.flush()

    print(f"\r   ... Scan complete! Found {len(track_metadata):,} valid tracks.       \n")

    if not track_metadata:
        print("No valid tracks were found.")
        return

    # 2. STRATIFY INTO NEAR / FAR BUCKETS
    print("⏳ Phase 2: Auto-detecting city center and stratifying tracks...")

    centroids = [(m[1], m[2]) for m in track_metadata]
    center_lat, center_lon = median_center(centroids)
    print(f"   Auto-detected center: lat={center_lat:.5f}, lon={center_lon:.5f}")

    distances = [(m[0], haversine_km(center_lat, center_lon, m[1], m[2])) for m in track_metadata]

    sorted_distances = sorted(d[1] for d in distances)
    cutoff_index = int(len(sorted_distances) * FAR_CUTOFF)
    distance_threshold = sorted_distances[cutoff_index]
    print(f"   Distance threshold (P{int(FAR_CUTOFF*100)}): {distance_threshold:.2f} km")

    near_pool = [d[0] for d in distances if d[1] <  distance_threshold]
    far_pool  = [d[0] for d in distances if d[1] >= distance_threshold]

    print(f"   Near pool: {len(near_pool):,} tracks  |  Far pool: {len(far_pool):,} tracks")

    # 3. SAMPLE TARGET IDs — pools are ID-only lists, use built-in random.sample
    print("⏳ Phase 3: Sampling target IDs...")

    far_count  = int(TRACK_LIMIT * FAR_RATIO)
    near_count = TRACK_LIMIT - far_count

    near_sample = random.sample(near_pool, min(near_count, len(near_pool)))
    far_sample  = random.sample(far_pool,  min(far_count,  len(far_pool)))

    target_ids = set(near_sample + far_sample)  # set for O(1) lookup in Pass 2

    print(f"   Sampled {len(near_sample)} near + {len(far_sample)} far = {len(target_ids)} total tracks\n")

    # 4. PASS 2: STREAM CSV AGAIN AND GENERATE GPX ONLY FOR SELECTED IDs
    print("⏳ Phase 4: Pass 2 - Extracting selected geometries and generating GPX...")

    total_tracks = len(target_ids)
    files_created = 0
    print_progress_bar(0, total_tracks, prefix='   Progress:', suffix='Complete', length=40)

    for csv_file in csv_files:
        with open(csv_file, mode='r', encoding='utf-8') as file:
            csv_reader = csv.DictReader(file)

            for row in csv_reader:
                trip_id = row['TRIP_ID']

                if trip_id not in target_ids:
                    continue

                polyline_str = row.get('POLYLINE', '')
                if not polyline_str or polyline_str == "[]":
                    continue
                coordinates = json.loads(polyline_str)
                simulated_start_time = get_realistic_start_time(START_DATE, END_DATE)

                gpx_xml = '<?xml version="1.0" encoding="UTF-8"?>\n'
                gpx_xml += '<gpx version="1.1" creator="PortoTaxiDemo">\n'
                gpx_xml += '  <metadata>\n'
                gpx_xml += f'    <desc>Extracted from dataset: {csv_file.name}</desc>\n'
                gpx_xml += f'    <time>{datetime.now().strftime("%Y-%m-%dT%H:%M:%SZ")}</time>\n'
                gpx_xml += '  </metadata>\n'
                gpx_xml += '  <trk>\n'
                gpx_xml += f'    <name>Demo Trip {trip_id}</name>\n'
                gpx_xml += f'    <cmt>Original Trip ID: {trip_id} | Source: {csv_file.name}</cmt>\n'
                gpx_xml += '    <trkseg>\n'

                for step, (lon, lat) in enumerate(coordinates):
                    current_time = simulated_start_time + timedelta(seconds=15 * step)
                    time_str = current_time.strftime('%Y-%m-%dT%H:%M:%SZ')
                    gpx_xml += f'      <trkpt lat="{lat}" lon="{lon}">\n'
                    gpx_xml += f'        <time>{time_str}</time>\n'
                    gpx_xml += '      </trkpt>\n'

                gpx_xml += '    </trkseg>\n'
                gpx_xml += '  </trk>\n'
                gpx_xml += '</gpx>'

                short_id = str(trip_id)[-10:]
                files_created += 1
                output_filename = f"porto_taxi_{files_created:04d}_trip_{short_id}.gpx"

                with open(output_dir / output_filename, "w", encoding='utf-8') as out_file:
                    out_file.write(gpx_xml)

                print_progress_bar(files_created, total_tracks, prefix='   Progress:', suffix='Complete', length=40)

                if files_created == total_tracks:
                    break

        if files_created == total_tracks:
            break

    print("-" * 50)
    print(f"✅ Success! Generated {files_created} tracks inside:\n   {output_dir.resolve()}")


if __name__ == "__main__":
    main()