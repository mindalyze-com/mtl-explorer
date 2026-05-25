# Detect Breaks Better and remove the jitters in a break for downstream processing

On top of below approach, we should remove the points within a detected break! As they generate wrong
max speeds and false energy calc.

Aim is: To detect short term stop (e.g 30 secs) as well as long term stops (5min) with high variance. 

## Problem statement

We have a sequence of timestamped GPS points:

```text
P = [p1, p2, p3, ...]
where each point has:
time, latitude, longitude
```

The user may be stationary, for example sitting in a restaurant, but the GPS signal can drift, jump, and bounce around. The raw track then looks like fake walking or fake movement. This creates downstream problems such as:

```text
- wrong distance
- wrong speed
- missed breaks
- noisy route geometry
- fake movement during stationary periods
- bad statistics after the GPS track is processed further
```

We have no hardware metadata:

```text
no HDOP
no VDOP
no satellite count
no accelerometer
no phone activity state
```

So the algorithm must use only:

```text
timestamp + GPS position
```

Also, your program already keeps a true raw backup. Therefore, the cleaned working track is allowed to **delete bad/drift points** instead of merely hiding them.

The desired result is not just:

```text
is_break = true / false
```

The desired result is:

```text
1. a cleaned movement track with drift points removed
2. a list of stop/break events
3. stop categories such as micro-stop, short stop, break, long break
4. protection against false stops when the data is too sparse
5. protection against one bad GPS jump ending or creating a stop
```

The central challenge is this:

> We need to distinguish real movement from GPS drift using only spatial-temporal consistency.

---

# Core idea

Do **not** decide stops from point-to-point speed.

Do **not** decide stops from total accumulated distance.

Do **not** rely on the maximum distance inside a window.

Instead, detect stops by asking:

> Over a time window, do enough GPS points form one compact, dense, stable cluster?

And also:

> Do we have enough samples to trust that conclusion?

So the algorithm should be built around four principles:

```text
1. Dense cluster over time = possible stop
2. Sparse data = unknown, not stopped
3. One far jump = possible GPS error, not movement
4. Stop points are deleted/collapsed in the cleaned track
```

---

# Recommended output model

I would separate the raw data from the cleaned data.

## Raw track

Already backed up by your program.

```text
raw_points:
    all original GPS points, untouched
```

## Cleaned movement track

This is what downstream systems should use.

```text
cleaned_points:
    raw moving points that survived filtering
    plus optional synthetic stop anchor points
```

## Stop events

Separate metadata table/list:

```text
StopEvent:
    start_time
    end_time
    duration
    category
    center_lat
    center_lon
    radius_estimate
    confidence
    deleted_point_ids
    reason
```

This is important. Do not try to preserve restaurant drift as route geometry. Preserve it as a **stop event**.

---

# Recommended deletion policy

Since you keep raw backup, I would delete stationary drift points from the cleaned track.

For every confirmed stop:

```text
delete all raw GPS points inside the stop interval from the cleaned track
```

Then optionally insert synthetic center anchors:

```text
STOP_START_ANCHOR at stop_start_time, located at robust stop center
STOP_END_ANCHOR   at stop_end_time,   located at robust stop center
```

This gives downstream code a clean result:

```text
before movement → stop center → stop center → after movement
```

The distance during the stop becomes zero, and the GPS scribble disappears.

Example:

```text
Raw:
    p100, p101, p102, p103, ..., p180
    where p110–p170 are restaurant GPS drift

Cleaned:
    p100, p101,
    synthetic_stop_start,
    synthetic_stop_end,
    p171, p172, ...

StopEvent:
    start = time(p110)
    end   = time(p170)
    category = BREAK
    deleted_points = [p110 ... p170]
```

This avoids downstream issues much better than keeping the noisy points.

---

# Stop categories

Yes: use the **same algorithm** with different parameter profiles.

The detector logic stays the same. Only the thresholds change.

Recommended categories:

| Category      | Typical duration | Meaning                                |
| ------------- | ---------------: | -------------------------------------- |
| `MICRO_STOP`  |    20–60 seconds | traffic light, quick pause, short wait |
| `SHORT_STOP`  |      1–3 minutes | brief halt, pickup, small interruption |
| `BREAK`       |     3–10 minutes | café, restaurant start, rest           |
| `LONG_BREAK`  |      10+ minutes | meal, visit, parked/stationary period  |
| `UNKNOWN_GAP` |              any | not enough samples to decide           |

The important part is `UNKNOWN_GAP`.

A sparse region must not be forced into either moving or stopped.

---

# Why sparse data must be guarded

This situation is not enough to prove a 30-second stop:

```text
t = 00s    point near restaurant
t = 30s    point near restaurant
```

The user could have moved away and come back between samples.

This is much better:

```text
t = 00s
t = 05s
t = 10s
t = 15s
t = 20s
t = 25s
t = 30s
```

Now the algorithm has evidence that the user stayed in the same area.

So the rule should be:

> A lack of movement evidence is not evidence of stopping.

For short stops, require dense sampling.
For long breaks, allow somewhat sparser sampling, but require points distributed across time.

---

# Parameter profiles

These are good starting values.

| Parameter                   |  `MICRO_STOP` |  `SHORT_STOP` |       `BREAK` |   `LONG_BREAK` |
| --------------------------- | ------------: | ------------: | ------------: | -------------: |
| Minimum duration            |          30 s |      60–120 s |         180 s |          600 s |
| Rolling window              |       45–60 s |         120 s |         300 s |      600–900 s |
| Minimum effective points    |             6 |             8 |         10–15 |            10+ |
| Max median time gap         |         6–8 s |       10–15 s |       20–30 s |           60 s |
| Max single time gap         |       10–12 s |       20–30 s |       45–60 s |      120–180 s |
| Required time coverage      |           70% |        60–70% |        50–60% |         40–50% |
| Cluster radius / DBSCAN eps |       35–50 m |       40–60 m |       50–75 m |       60–100 m |
| Required inlier ratio       |        75–85% |        70–80% |        65–75% |         60–70% |
| Compactness check           | r80 ≤ 35–50 m | r80 ≤ 40–60 m | r80 ≤ 50–75 m | r80 ≤ 75–100 m |
| Exit radius                 |       60–80 m |      80–100 m |     100–130 m |      120–160 m |
| Exit confirmation           |       10–20 s |       20–40 s |       45–90 s |       60–120 s |

For your restaurant example, I would start with:

```text
BREAK:
    window = 300 s
    minimum duration = 180 s
    eps = 50–60 m
    r80 threshold = 50–60 m
    inlier ratio = 70%
    exit radius = 100–120 m
```

For 30-second stops:

```text
MICRO_STOP:
    minimum duration = 30 s
    min effective points = 6
    max gap = 10–12 s
    r80 threshold = 35–50 m
    inlier ratio = 75–85%
```

If you only have 2–3 points in 30 seconds, classify it as:

```text
UNKNOWN_GAP or LOW_CONFIDENCE_POSSIBLE_STOP
```

not as a confirmed stop.

---

# Overall approach

The full approach has five stages.

## Stage 1: Preprocess

```text
- sort points by timestamp
- remove impossible timestamps
- remove duplicate timestamps
- convert lat/lon to local meter coordinates
- calculate time gaps, distances, implied speeds
```

Do all spatial logic in meters, not degrees.

---

## Stage 2: Mark obvious GPS-only errors

Because there is no HDOP or hardware info, use behavioral checks.

The most important pattern is:

```text
A → B → C
```

where:

```text
A and C are close together
B is far away
```

This is usually a GPS jump-out-and-return error.

Example:

```text
A = restaurant
B = 120 m away
C = restaurant again
```

Delete or mark `B` as a spike.

Rule:

```text
if distance(A, B) > 50 m
and distance(B, C) > 50 m
and distance(A, C) < 25 m
then B is an isolated GPS spike
```

Use slightly larger thresholds indoors or in dense cities.

---

## Stage 3: Detect stop candidates using one shared detector

For each stop profile:

```text
MICRO_STOP
SHORT_STOP
BREAK
LONG_BREAK
```

run the same evaluation:

```text
1. Get rolling time window
2. Check sample sufficiency
3. Find largest dense cluster
4. Check cluster compactness
5. Check cluster duration
6. Return stop candidate if valid
```

The only difference between categories is the parameter set.

---

## Stage 4: Resolve, merge, and classify stops

Candidate windows will overlap. Merge them.

Example:

```text
MICRO_STOP candidate from 12:00:00 to 12:00:40
SHORT_STOP candidate from 12:00:00 to 12:01:30
BREAK candidate from 12:00:00 to 12:04:00
```

This should become one stop event:

```text
start = 12:00:00
end = 12:04:00
category = BREAK
```

A stop can upgrade over time:

```text
POSSIBLE_STOP
→ MICRO_STOP
→ SHORT_STOP
→ BREAK
→ LONG_BREAK
```

---

## Stage 5: Delete/collapse stop points

For every validated stop:

```text
- delete all raw GPS points inside the stop interval from cleaned_points
- create a StopEvent
- optionally insert synthetic stop start/end anchor points
```

Also delete isolated GPS spikes outside stops.

For large data gaps, do not invent a stop. Create an `UNKNOWN_GAP` event or split the track.

---

# Pseudocode

Below is the full structure.

## Data structures

```pseudo
Point:
    id
    time
    lat
    lon
    x
    y
    flags:
        INVALID
        ISOLATED_SPIKE
        UNKNOWN_GAP_ENDPOINT
        DELETED

StopProfile:
    name

    min_duration_s
    window_s

    min_effective_points
    max_median_gap_s
    max_single_gap_s

    coverage_bin_s
    min_time_coverage_ratio

    eps_m
    min_cluster_points

    r80_threshold_m
    r90_threshold_m
    min_inlier_ratio

    exit_radius_m
    exit_confirmation_s

    merge_gap_s
    merge_radius_m

StopCandidate:
    start_time
    end_time
    center_x
    center_y
    profile_name
    score
    cluster_point_ids
    inlier_ratio
    r80
    r90

StopEvent:
    start_time
    end_time
    duration_s
    category
    center_x
    center_y
    confidence
    radius_r80
    radius_r90
    deleted_point_ids
```

---

## Main function

```pseudo
function clean_gps_track(raw_points):

    # Stage 1: preprocess
    points = sort_by_time(raw_points)
    points = remove_invalid_or_duplicate_timestamps(points)
    points = project_lat_lon_to_local_meters(points)

    # Stage 2: mark obvious GPS-only errors
    mark_isolated_spikes(points)
    gap_events = mark_unknown_gaps(points)

    # Stage 3: generate stop candidates
    candidates = []

    for i from 0 to len(points)-1:

        current_time = points[i].time

        for profile in STOP_PROFILES:

            window = get_points_in_time_range(
                points,
                current_time - profile.window_s,
                current_time
            )

            candidate = evaluate_stop_window(window, profile)

            if candidate is not null:
                candidates.append(candidate)

    # Stage 4: resolve overlapping candidates into final stop events
    stop_events = resolve_stop_candidates(candidates, points)

    # Stage 5: classify and validate final events
    stop_events = classify_and_validate_stop_events(stop_events, points)

    # Stage 6: delete/collapse points for cleaned output
    cleaned_points, deleted_points = build_cleaned_track(
        points,
        stop_events,
        gap_events
    )

    return cleaned_points, stop_events, deleted_points, gap_events
```

---

# Stage 2 pseudocode: GPS-only spike detection

```pseudo
function mark_isolated_spikes(points):

    for i from 1 to len(points)-2:

        A = points[i-1]
        B = points[i]
        C = points[i+1]

        if A.INVALID or B.INVALID or C.INVALID:
            continue

        dtAB = B.time - A.time
        dtBC = C.time - B.time

        if dtAB <= 0 or dtBC <= 0:
            B.flags.add(INVALID)
            continue

        dAB = distance_m(A, B)
        dBC = distance_m(B, C)
        dAC = distance_m(A, C)

        # Jump away and immediately return
        if dAB > 50m
           and dBC > 50m
           and dAC < 25m
           and dtAB < 60s
           and dtBC < 60s:

            B.flags.add(ISOLATED_SPIKE)
```

For noisy urban/indoor data, use:

```text
jump threshold: 50–100 m
return radius: 20–40 m
```

This rule should be conservative. It catches obvious spikes without deleting real movement too aggressively.

---

# Stage 2 pseudocode: unknown gaps

```pseudo
function mark_unknown_gaps(points):

    gap_events = []

    for i from 1 to len(points)-1:

        prev = points[i-1]
        curr = points[i]

        dt = curr.time - prev.time

        if dt > GLOBAL_MAX_UNKNOWN_GAP:

            gap_events.append({
                start_time: prev.time,
                end_time: curr.time,
                reason: "too few samples to infer movement or stop"
            })

            prev.flags.add(UNKNOWN_GAP_ENDPOINT)
            curr.flags.add(UNKNOWN_GAP_ENDPOINT)

    return gap_events
```

Example:

```text
GLOBAL_MAX_UNKNOWN_GAP = 120–300 seconds
```

depending on how your logger behaves.

Important:

```text
point at 12:00 near restaurant
point at 12:05 near restaurant
```

should not automatically mean:

```text
5-minute break
```

It should usually mean:

```text
UNKNOWN_GAP or low-confidence possible stop
```

unless there are enough points between those times.

---

# Stage 3 pseudocode: stop-window evaluation

```pseudo
function evaluate_stop_window(window_points, profile):

    effective_points = []

    for p in window_points:
        if p.INVALID:
            continue

        if p.ISOLATED_SPIKE:
            continue

        effective_points.append(p)

    if not sample_quality_ok(effective_points, profile):
        return null

    cluster = find_largest_compact_cluster(effective_points, profile)

    if cluster is null:
        return null

    center = robust_center(cluster.points)

    distances = []
    for p in cluster.points:
        distances.append(distance_m(p, center))

    r80 = percentile(distances, 80)
    r90 = percentile(distances, 90)

    inlier_ratio = count(cluster.points) / count(effective_points)

    cluster_duration = max_time(cluster.points) - min_time(cluster.points)

    if cluster_duration < profile.min_duration_s:
        return null

    if inlier_ratio < profile.min_inlier_ratio:
        return null

    if r80 > profile.r80_threshold_m:
        return null

    if r90 > profile.r90_threshold_m:
        return null

    score = score_stop_candidate(
        cluster_duration,
        inlier_ratio,
        r80,
        r90,
        profile
    )

    return StopCandidate(
        start_time = min_time(cluster.points),
        end_time = max_time(cluster.points),
        center_x = center.x,
        center_y = center.y,
        profile_name = profile.name,
        score = score,
        cluster_point_ids = ids(cluster.points),
        inlier_ratio = inlier_ratio,
        r80 = r80,
        r90 = r90
    )
```

---

# Sample quality guard

This is one of the most important pieces.

```pseudo
function sample_quality_ok(points, profile):

    if count(points) < profile.min_effective_points:
        return false

    gaps = []

    for i from 1 to count(points)-1:
        gaps.append(points[i].time - points[i-1].time)

    if median(gaps) > profile.max_median_gap_s:
        return false

    if max(gaps) > profile.max_single_gap_s:
        return false

    coverage = time_coverage_ratio(
        points,
        profile.coverage_bin_s
    )

    if coverage < profile.min_time_coverage_ratio:
        return false

    return true
```

The `time_coverage_ratio` prevents this:

```text
many points at the beginning
nothing in the middle
many points at the end
```

from being treated as a solid stop.

```pseudo
function time_coverage_ratio(points, bin_size_s):

    start = min_time(points)
    end = max_time(points)

    duration = end - start

    if duration <= 0:
        return 0

    bin_count = ceil(duration / bin_size_s)

    occupied_bins = empty_set()

    for p in points:
        bin_index = floor((p.time - start) / bin_size_s)
        occupied_bins.add(bin_index)

    return count(occupied_bins) / bin_count
```

Example:

```text
30-second micro-stop
bin size = 5 or 10 seconds
required coverage = 70%
```

So a 30-second stop needs points spread across the interval.

---

# Cluster detection

You can use DBSCAN or a simpler robust-radius method.

## Option A: DBSCAN-based

This is stronger.

```pseudo
function find_largest_compact_cluster(points, profile):

    clusters = DBSCAN(
        points = points,
        eps = profile.eps_m,
        min_samples = profile.min_cluster_points
    )

    best_cluster = null
    best_score = -infinity

    for cluster in clusters:

        if count(cluster.points) < profile.min_cluster_points:
            continue

        center = robust_center(cluster.points)

        distances = []
        for p in cluster.points:
            distances.append(distance_m(p, center))

        r80 = percentile(distances, 80)
        r90 = percentile(distances, 90)

        inlier_ratio = count(cluster.points) / count(points)

        duration = max_time(cluster.points) - min_time(cluster.points)

        if duration < profile.min_duration_s:
            continue

        if inlier_ratio < profile.min_inlier_ratio:
            continue

        if r80 > profile.r80_threshold_m:
            continue

        if r90 > profile.r90_threshold_m:
            continue

        score = score_stop_candidate(
            duration,
            inlier_ratio,
            r80,
            r90,
            profile
        )

        if score > best_score:
            best_score = score
            best_cluster = cluster

    return best_cluster
```

The compactness check after DBSCAN is important.

DBSCAN alone can sometimes create a stretched chain of points. The `r80` and `r90` checks prevent that.

---

## Option B: simpler robust-center method

This is cheaper and often enough.

```pseudo
function find_largest_compact_cluster(points, profile):

    center = robust_center(points)

    inliers = []

    for p in points:
        d = distance_m(p, center)

        if d <= profile.eps_m:
            inliers.append(p)

    if count(inliers) < profile.min_cluster_points:
        return null

    return Cluster(points = inliers)
```

This works well when the stop cloud is one dominant blob.

DBSCAN is better when there are many outliers or multiple nearby blobs.

---

# Robust center

Do not use the plain average if there are outliers.

Use a medoid or geometric median.

Simple medoid:

```pseudo
function robust_center(points):

    best_point = null
    best_sum_distance = infinity

    for candidate in points:

        total = 0

        for p in points:
            total += distance_m(candidate, p)

        if total < best_sum_distance:
            best_sum_distance = total
            best_point = candidate

    return PointLike(
        x = best_point.x,
        y = best_point.y
    )
```

This is slower than a mean, but robust and simple. For typical rolling GPS windows, it is usually fine.

---

# Candidate scoring

The score is not strictly required, but it helps choose between overlapping candidates.

```pseudo
function score_stop_candidate(duration, inlier_ratio, r80, r90, profile):

    score = 0

    # More duration is better
    duration_score = clamp(
        duration / profile.min_duration_s,
        0,
        2
    ) * 20

    # More inliers is better
    inlier_score = clamp(
        (inlier_ratio - profile.min_inlier_ratio) /
        (1 - profile.min_inlier_ratio),
        0,
        1
    ) * 30

    # Smaller radius is better
    compact_score = clamp(
        1 - (r80 / profile.r80_threshold_m),
        0,
        1
    ) * 30

    # Penalize large r90
    tail_score = clamp(
        1 - (r90 / profile.r90_threshold_m),
        0,
        1
    ) * 20

    score = duration_score + inlier_score + compact_score + tail_score

    return score
```

Use the score for ranking, not as the only decision rule.

The hard checks should still exist:

```text
enough samples
enough duration
enough inlier ratio
compact enough
```

---

# Resolving overlapping candidates

Rolling windows will create many overlapping detections. Merge them.

```pseudo
function resolve_stop_candidates(candidates, points):

    if candidates is empty:
        return []

    sort candidates by start_time, then end_time

    merged = []

    current = candidates[0]

    for cand in candidates[1:]:

        same_place = distance_xy(
            current.center_x,
            current.center_y,
            cand.center_x,
            cand.center_y
        ) <= merge_radius_for(current, cand)

        time_overlap = cand.start_time <= current.end_time

        short_gap = cand.start_time - current.end_time <= merge_gap_for(current, cand)

        if same_place and (time_overlap or short_gap):

            current = merge_candidates(current, cand)

        else:
            merged.append(current)
            current = cand

    merged.append(current)

    return convert_candidates_to_stop_events(merged)
```

Merging logic:

```pseudo
function merge_candidates(a, b):

    combined_point_ids = union(a.cluster_point_ids, b.cluster_point_ids)

    combined_points = get_points_by_id(combined_point_ids)

    center = robust_center(combined_points)

    distances = distance of combined_points to center

    return StopCandidate(
        start_time = min(a.start_time, b.start_time),
        end_time = max(a.end_time, b.end_time),
        center_x = center.x,
        center_y = center.y,
        profile_name = higher_category(a.profile_name, b.profile_name),
        score = max(a.score, b.score),
        cluster_point_ids = combined_point_ids,
        inlier_ratio = recomputed_inlier_ratio,
        r80 = percentile(distances, 80),
        r90 = percentile(distances, 90)
    )
```

This prevents one restaurant break from becoming many tiny stops.

---

# Classification and upgrading

After merging, classify by final duration and quality.

```pseudo
function classify_and_validate_stop_events(stop_events, points):

    validated = []

    for event in stop_events:

        event_points = points_between(
            points,
            event.start_time,
            event.end_time
        )

        category = null

        # Try largest category first
        for profile in [LONG_BREAK, BREAK, SHORT_STOP, MICRO_STOP]:

            if event.duration_s < profile.min_duration_s:
                continue

            if not sample_quality_ok(event_points, profile):
                continue

            if event_is_spatially_valid(event_points, event, profile):
                category = profile.name
                break

        if category is null:
            # Do not pretend it is a stop
            # It may become UNKNOWN or be discarded
            continue

        event.category = category

        event.confidence = estimate_confidence(event, category)

        validated.append(event)

    return validated
```

Spatial validation:

```pseudo
function event_is_spatially_valid(event_points, event, profile):

    effective_points = remove_invalid_and_spikes(event_points)

    center = robust_center(effective_points)

    distances = []

    for p in effective_points:
        distances.append(distance_m(p, center))

    r80 = percentile(distances, 80)
    r90 = percentile(distances, 90)

    inliers = count points where distance_m(p, center) <= profile.eps_m

    inlier_ratio = inliers / count(effective_points)

    if r80 > profile.r80_threshold_m:
        return false

    if r90 > profile.r90_threshold_m:
        return false

    if inlier_ratio < profile.min_inlier_ratio:
        return false

    return true
```

---

# Hysteresis: do not exit a stop too early

A stop should not end because of one far point.

Use an exit rule:

```text
Leave stop only after sustained movement outside the stop radius.
```

```pseudo
function sustained_escape_detected(points_after_stop, stop_event, profile):

    recent = first_points_covering_duration(
        points_after_stop,
        profile.exit_confirmation_s
    )

    effective_recent = remove_invalid_and_spikes(recent)

    if count(effective_recent) < minimum_escape_points(profile):
        return false

    outside_count = 0
    distances = []

    for p in effective_recent:

        d = distance_xy(
            p.x,
            p.y,
            stop_event.center_x,
            stop_event.center_y
        )

        distances.append(d)

        if d > profile.exit_radius_m:
            outside_count += 1

    outside_ratio = outside_count / count(effective_recent)

    median_distance = median(distances)

    if outside_ratio >= 0.70
       and median_distance > profile.exit_radius_m:
        return true

    return false
```

This means:

```text
one point 120 m away = not enough
several points over 60 seconds outside 100 m = real exit
```

---

# Building the cleaned track

This is where the deletion happens.

```pseudo
function build_cleaned_track(points, stop_events, gap_events):

    cleaned = []
    deleted = []

    stop_intervals = build_interval_index(stop_events)

    for p in points:

        if p.INVALID:
            deleted.append({
                point_id: p.id,
                reason: "invalid point"
            })
            continue

        if p.ISOLATED_SPIKE:
            deleted.append({
                point_id: p.id,
                reason: "isolated GPS spike"
            })
            continue

        stop = find_stop_containing_time(stop_intervals, p.time)

        if stop is not null:

            deleted.append({
                point_id: p.id,
                reason: "point inside confirmed stop / GPS drift collapsed",
                stop_id: stop.id
            })

            stop.deleted_point_ids.append(p.id)

            continue

        cleaned.append(p)

    # Add synthetic stop anchors
    for stop in stop_events:

        start_anchor = synthetic_point(
            time = stop.start_time,
            x = stop.center_x,
            y = stop.center_y,
            type = "STOP_START_ANCHOR",
            stop_id = stop.id
        )

        end_anchor = synthetic_point(
            time = stop.end_time,
            x = stop.center_x,
            y = stop.center_y,
            type = "STOP_END_ANCHOR",
            stop_id = stop.id
        )

        cleaned.append(start_anchor)
        cleaned.append(end_anchor)

    cleaned = sort_by_time(cleaned)

    cleaned = split_or_mark_unknown_gaps(cleaned, gap_events)

    return cleaned, deleted
```

The synthetic anchors are optional but recommended.

They preserve the time spent stopped without preserving the noisy geometry.

---

# Handling unknown gaps

Unknown gaps should not be treated as movement or stops.

Example:

```text
12:00:00 point A
12:08:00 point B
```

The cleaned track should not necessarily draw a movement line from A to B.

Instead:

```pseudo
function split_or_mark_unknown_gaps(cleaned, gap_events):

    for gap in gap_events:

        mark_cleaned_track_split(
            start_time = gap.start_time,
            end_time = gap.end_time,
            reason = "unknown movement due to sparse samples"
        )

    return cleaned
```

Downstream systems should know:

```text
distance/speed across this gap is unreliable
```

This is especially important if your logger sometimes sleeps.

---

# Full algorithm in compact form

```pseudo
raw_points
    ↓
backup raw points
    ↓
sort + validate timestamps
    ↓
project coordinates to meters
    ↓
mark isolated GPS spikes
    ↓
mark unknown sampling gaps
    ↓
for each rolling window:
    for each stop profile:
        check sample sufficiency
        find largest dense cluster
        check r80/r90 compactness
        check inlier ratio
        check duration
        create stop candidate
    ↓
merge overlapping stop candidates
    ↓
classify as MICRO_STOP / SHORT_STOP / BREAK / LONG_BREAK
    ↓
apply hysteresis:
    do not enter stop too easily
    do not exit stop from one jump
    ↓
delete stop-drift points from cleaned track
    ↓
insert optional synthetic stop anchors
    ↓
return:
    cleaned movement track
    stop events
    deleted point ids/reasons
    unknown gaps
```

---

# What happens to your restaurant example

The raw data looks like:

```text
many points jumping around while you are stationary
```

The detector sees:

```text
- enough points over several minutes
- most points belong to one compact area
- some points are far away, but they are minority outliers
- there is no sustained escape from the restaurant center
```

So it creates:

```text
StopEvent:
    category = BREAK or LONG_BREAK
    center = robust restaurant center
    deleted_point_ids = all noisy restaurant GPS points
```

The cleaned route becomes:

```text
arrival path
→ restaurant stop anchor
→ restaurant stop anchor
→ departure path
```

The fake scribble disappears.

---

# Key design decisions

## 1. Same algorithm, different parameters

Yes, use one detector with profiles:

```text
MICRO_STOP
SHORT_STOP
BREAK
LONG_BREAK
```

This avoids maintaining four separate algorithms.

---

## 2. Sparse data becomes unknown

This is critical.

```text
not enough points ≠ stopped
```

Sparse data should produce:

```text
UNKNOWN_GAP
LOW_CONFIDENCE_POSSIBLE_STOP
```

not a confirmed stop.

---

## 3. Use percentile radius, not max distance

Do not use:

```text
maximum distance between any two points
bounding-box diagonal
```

as the main stop test.

Use:

```text
r80
r90
inlier ratio
dense cluster duration
```

That makes the detector robust against one or two GPS jumps.

---

## 4. Delete drift points from the cleaned track

Since the raw backup exists, the cleaned track should be allowed to be aggressive.

Confirmed stop drift points should be removed from the working track.

Downstream code should process:

```text
cleaned movement points + stop events
```

not the raw GPS scribble.

---

## 5. Do not let one point enter or exit a stop

Entering a stop requires a compact cluster over time.

Exiting a stop requires sustained escape.

This is the hysteresis:

```text
enter radius: smaller
exit radius: larger
exit requires multiple points over time
```

Example:

```text
enter BREAK when 70% of points are within 50–60 m
exit BREAK only after 45–90 seconds mostly outside 100–120 m
```

---

# Final recommended implementation target

The cleaned pipeline should produce four kinds of results:

```text
1. MOVEMENT_POINT
   A GPS point that survived and represents actual movement.

2. DELETED_POINT
   A raw point removed from the cleaned track.
   Reasons:
       INVALID
       ISOLATED_SPIKE
       STOP_DRIFT_COLLAPSED

3. STOP_EVENT
   A stationary interval with center, duration, category, confidence.

4. UNKNOWN_GAP
   A region where the data was too sparse to infer movement or stop.
```

That gives you a robust GPS-only system that can handle:

```text
- indoor restaurant drift
- short 30-second stops, when enough samples exist
- long breaks
- missing data
- GPS jump-out-and-return spikes
- downstream systems that cannot tolerate noisy stop scribbles
```


## Problem statement

We need to clean a GPS track where the user may be **physically stationary**, but the recorded GPS points jump around because of indoor reception, urban multipath, poor satellite geometry, or phone/GPS noise.

The current failure mode is:

```text
User is stopped at a restaurant
↓
GPS points drift around
↓
Algorithm interprets drift as movement
↓
Downstream systems see false distance, false speed, false route segments, and missed breaks
```

The specific constraints are:

```text
Available data:
    timestamp
    latitude
    longitude

Unavailable data:
    HDOP / VDOP
    satellite count
    accelerometer
    step counter
    phone activity state
    GPS accuracy metadata
```

The desired output is not just “detect a break.” We want:

```text
1. Detect stops of different lengths:
       - micro-stops around 30 seconds
       - short stops
       - normal breaks
       - long breaks

2. Avoid false stops when sampling is too sparse.

3. Remove GPS drift points from the processed track because they cause downstream issues.

4. Preserve the true raw data separately.
```

Since your program already backs up the true raw data, we can treat the cleaned track as a **destructive filtered representation**. That means we can delete bad points from the processed track, as long as the raw backup remains untouched.

---

# Core idea

A GPS stop should not be detected from point-to-point speed.

Instead, define a stop as:

> A time interval where enough GPS samples are available, and most of those samples form a compact spatial cluster for long enough.

So instead of asking:

```text
Did the user move slowly between point A and point B?
```

we ask:

```text
Over this time window, do most points remain inside one compact area?
Do we have enough samples to trust that conclusion?
Did the compact pattern persist long enough for a specific stop category?
```

The approach should be:

```text
Raw GPS points
    ↓
Pre-clean impossible / isolated spikes
    ↓
Evaluate rolling windows using the same stop detector
    ↓
Apply different parameter profiles for micro-stop, short-stop, break, long-break
    ↓
Use a state machine with hysteresis
    ↓
Create stop events
    ↓
Delete stop-jitter points from the processed track
    ↓
Insert optional synthetic stop-anchor points
    ↓
Output clean movement track + stop events
```

---

# Important design decision

Use the **same algorithm** for every stop category, but with different parameters.

That is the right architecture.

You do not want completely separate algorithms for 30-second stops, 3-minute breaks, and 20-minute breaks. That becomes hard to maintain and inconsistent.

Instead, use one shared function:

```pseudo
evaluate_stop_window(points, params)
```

Then run it with different parameter profiles:

```text
MICRO_STOP params
SHORT_STOP params
BREAK params
LONG_BREAK params
```

The shorter the stop, the stricter the sampling requirements should be.

A 30-second stop needs many points close together in time.
A 10-minute break can tolerate sparser sampling.

---

# Stop categories

Suggested starting categories:

| Category     | Typical duration | Confidence goal | Example                        |
| ------------ | ---------------: | --------------- | ------------------------------ |
| `MICRO_STOP` |        30–60 sec | medium          | traffic light, quick pause     |
| `SHORT_STOP` |          1–3 min | medium/high     | pickup, short wait             |
| `BREAK`      |         3–10 min | high            | café, restaurant start, rest   |
| `LONG_BREAK` |          10+ min | very high       | meal, visit, parked/stationary |

Suggested initial parameters:

```text
MICRO_STOP:
    min_duration:        30 sec
    window:              45 sec
    min_points:          6
    max_median_gap:      7 sec
    max_gap:             12 sec
    min_time_coverage:   70%
    cluster_eps:         35–50 m
    r80_threshold:       35–50 m
    min_inlier_ratio:    0.80
    exit_duration:       15 sec
    exit_radius:         70–90 m

SHORT_STOP:
    min_duration:        90 sec
    window:              120 sec
    min_points:          8
    max_median_gap:      12 sec
    max_gap:             25 sec
    min_time_coverage:   65%
    cluster_eps:         40–60 m
    r80_threshold:       40–60 m
    min_inlier_ratio:    0.75
    exit_duration:       30 sec
    exit_radius:         80–110 m

BREAK:
    min_duration:        180 sec
    window:              300 sec
    min_points:          10–15
    max_median_gap:      20–30 sec
    max_gap:             60 sec
    min_time_coverage:   50–60%
    cluster_eps:         50–75 m
    r80_threshold:       50–75 m
    min_inlier_ratio:    0.70
    exit_duration:       60 sec
    exit_radius:         100–130 m

LONG_BREAK:
    min_duration:        600 sec
    window:              900 sec or more
    min_points:          12+
    max_median_gap:      relaxed
    max_gap:             120–180 sec
    min_time_coverage:   40–50%
    cluster_eps:         60–100 m
    r80_threshold:       60–100 m
    min_inlier_ratio:    0.65
    exit_duration:       90 sec
    exit_radius:         120–160 m
```

These are starting values. You should tune them against your real tracks.

---

# Key principle: insufficient data must not become a stop

This is critical.

This should **not** become a confirmed 30-second stop:

```text
t = 00s   point near restaurant
t = 30s   point near restaurant
```

There are only two points. The user may have moved between them.

That should be classified as:

```text
INSUFFICIENT_DATA
```

not:

```text
MICRO_STOP
```

This is much stronger evidence:

```text
t = 00s
t = 05s
t = 10s
t = 15s
t = 20s
t = 25s
t = 30s
```

Now the algorithm has enough temporal coverage to say:

```text
These points stayed compact for roughly 30 seconds.
```

So every stop profile needs a **sample sufficiency gate** before spatial clustering is even considered.

---

# What to delete

Since your raw data is already backed up, the filtered track can delete points that are harmful downstream.

Delete these from the processed track:

```text
1. Invalid points
   - invalid timestamp
   - duplicate timestamp with worse position
   - impossible coordinates

2. Isolated GPS spikes
   - jump far away
   - immediately jump back
   - not consistent with surrounding points

3. Stop-jitter points
   - points inside a confirmed stop interval
   - including the restaurant scribble

4. Failed escape points
   - while stopped, GPS jumps far away
   - then returns to the stop center
   - these should be deleted as stop drift

5. Optional: very poor movement points
   - only if they are clearly inconsistent with the surrounding movement
```

Do **not** blindly delete sparse ambiguous points.

For example:

```text
point at 12:00
no data for 5 minutes
point at 12:05 nearby
```

That should not automatically become a break. It should be:

```text
UNKNOWN_GAP
```

For downstream processing, you can split the track segment at that gap so the system does not draw or calculate a false movement line across missing data.

---

# Recommended output model

The cleaned system should produce at least two outputs.

## 1. Clean movement track

This is used by downstream distance, speed, map display, routing, etc.

```pseudo
CleanPoint {
    timestamp
    lat
    lon
    source: RAW_KEPT | SYNTHETIC_STOP_ANCHOR
    segment_id
}
```

## 2. Stop events

These preserve the stop information even though noisy points are deleted from the clean track.

```pseudo
StopEvent {
    id
    category              # MICRO_STOP, SHORT_STOP, BREAK, LONG_BREAK
    start_time
    end_time
    duration
    center_lat
    center_lon
    radius_r80
    inlier_ratio
    confidence
    raw_point_count
    deleted_point_count
}
```

I strongly recommend adding **synthetic stop anchors** to the clean track.

For a confirmed stop, insert:

```text
STOP_START point at stop center and stop start time
STOP_END   point at same stop center and stop end time
```

That gives downstream systems a clean representation:

```text
arrive at stop center
remain there with zero distance
leave from stop center
```

The raw noisy restaurant scribble is deleted from the processed track, but the stop duration is preserved.

---

# Full approach summary

## Step 1: Normalize the input

Sort by timestamp, remove invalid points, and project coordinates into meters.

Do not run distance logic directly on latitude/longitude degrees.

```text
lat/lon → local metric coordinates
```

Use UTM, local tangent plane, or another metric projection.

---

## Step 2: Pre-delete obvious isolated spikes

Look for the classic GPS pattern:

```text
A → B → C
```

where:

```text
A and C are close together
B is far away
```

Example:

```text
restaurant point
GPS jump 120 m away
restaurant point again
```

That middle point is almost certainly noise.

Delete it from the processed track.

---

## Step 3: Evaluate stop candidates using rolling windows

For each rolling window:

```text
1. Check sample sufficiency.
2. Find the largest compact cluster.
3. Calculate robust compactness.
4. Calculate inlier ratio.
5. Check whether it satisfies one or more stop profiles.
```

Use robust metrics:

```text
80th percentile radius
90th percentile radius
inlier ratio
cluster duration
```

Avoid relying on:

```text
maximum distance
bounding-box diagonal
point-to-point speed
```

Those are too sensitive to outliers.

---

## Step 4: Use one detector with different profiles

The same detector checks all categories.

```pseudo
for profile in [MICRO_STOP, SHORT_STOP, BREAK, LONG_BREAK]:
    result = evaluate_stop_window(window, profile)
```

The highest valid profile wins.

Example:

```text
After 30 seconds:
    MICRO_STOP may become valid.

After 90 seconds:
    SHORT_STOP may become valid.

After 3 minutes:
    BREAK may become valid.

After 10 minutes:
    LONG_BREAK may become valid.
```

A stop can upgrade over time:

```text
POSSIBLE_STOP → MICRO_STOP → SHORT_STOP → BREAK → LONG_BREAK
```

---

## Step 5: Use hysteresis

Entering a stop should be easier than exiting it.

Example:

```text
Enter stop:
    70–80% of points within 50 m for 3 minutes

Exit stop:
    points must remain outside 100 m for 60 seconds
```

A single far-away point should never end a stop.

While stopped, suspicious far-away points go into a temporary escape buffer.

If they return to the stop center, delete them as GPS drift.

If they stay away long enough, confirm exit and keep those points as movement.

---

## Step 6: Delete stop-jitter points

After stop intervals are confirmed:

```text
delete all original GPS points inside the stop interval from the processed track
```

Then insert synthetic anchors:

```text
stop_start at stop center
stop_end at stop center
```

This prevents downstream systems from seeing the stop as movement.

---

## Step 7: Post-process

After detecting all stops:

```text
1. Merge nearby stop fragments.
2. Split unknown gaps.
3. Recompute clean movement distances.
4. Recompute segment IDs.
```

Merge rule example:

```pseudo
if gap_between_stops < 120 sec
   and distance(stop1.center, stop2.center) < 60 m:
       merge stops
```

This helps with cases where a restaurant stop is briefly broken by GPS drift.

---

# Pseudocode

## Data structures

```pseudo
enum PointStatus:
    KEEP
    DELETE_INVALID
    DELETE_SPIKE
    DELETE_STOP_JITTER
    DELETE_FAILED_ESCAPE

enum StopCategory:
    MICRO_STOP
    SHORT_STOP
    BREAK
    LONG_BREAK

enum EvalStatus:
    STOP_CANDIDATE
    NOT_STOPPED
    INSUFFICIENT_DATA
```

```pseudo
Point:
    id
    timestamp
    lat
    lon
    x_meters
    y_meters
    status
    segment_id
```

```pseudo
StopProfile:
    category
    min_duration_sec
    window_sec

    min_points
    max_median_gap_sec
    max_gap_sec
    min_time_coverage

    cluster_eps_m
    min_cluster_points
    r80_threshold_m
    min_inlier_ratio

    exit_duration_sec
    exit_radius_m
    min_exit_points
```

```pseudo
StopCandidate:
    status
    category
    start_time
    end_time
    center_x
    center_y
    radius_r80
    radius_r90
    inlier_ratio
    confidence
    cluster_points
```

```pseudo
StopEvent:
    id
    category
    start_time
    end_time
    center_x
    center_y
    radius_r80
    inlier_ratio
    confidence
    raw_point_count
    deleted_point_count
```

---

# Parameter profiles

```pseudo
PROFILES = [

    StopProfile(
        category = MICRO_STOP,
        min_duration_sec = 30,
        window_sec = 45,

        min_points = 6,
        max_median_gap_sec = 7,
        max_gap_sec = 12,
        min_time_coverage = 0.70,

        cluster_eps_m = 45,
        min_cluster_points = 5,
        r80_threshold_m = 45,
        min_inlier_ratio = 0.80,

        exit_duration_sec = 15,
        exit_radius_m = 80,
        min_exit_points = 3
    ),

    StopProfile(
        category = SHORT_STOP,
        min_duration_sec = 90,
        window_sec = 120,

        min_points = 8,
        max_median_gap_sec = 12,
        max_gap_sec = 25,
        min_time_coverage = 0.65,

        cluster_eps_m = 55,
        min_cluster_points = 6,
        r80_threshold_m = 55,
        min_inlier_ratio = 0.75,

        exit_duration_sec = 30,
        exit_radius_m = 100,
        min_exit_points = 4
    ),

    StopProfile(
        category = BREAK,
        min_duration_sec = 180,
        window_sec = 300,

        min_points = 12,
        max_median_gap_sec = 30,
        max_gap_sec = 60,
        min_time_coverage = 0.55,

        cluster_eps_m = 65,
        min_cluster_points = 8,
        r80_threshold_m = 65,
        min_inlier_ratio = 0.70,

        exit_duration_sec = 60,
        exit_radius_m = 120,
        min_exit_points = 5
    ),

    StopProfile(
        category = LONG_BREAK,
        min_duration_sec = 600,
        window_sec = 900,

        min_points = 12,
        max_median_gap_sec = 60,
        max_gap_sec = 180,
        min_time_coverage = 0.45,

        cluster_eps_m = 85,
        min_cluster_points = 8,
        r80_threshold_m = 85,
        min_inlier_ratio = 0.65,

        exit_duration_sec = 90,
        exit_radius_m = 150,
        min_exit_points = 5
    )
]
```

---

# Main function

```pseudo
function process_track(raw_points):

    # 1. Prepare data
    points = normalize_points(raw_points)
    points = sort_by_timestamp(points)
    points = project_to_local_meters(points)

    # 2. Mark invalid points
    for p in points:
        if invalid_coordinate(p) or invalid_timestamp(p):
            p.status = DELETE_INVALID
        else:
            p.status = KEEP

    # 3. Delete obvious isolated GPS spikes from processed track
    mark_isolated_spikes(points)

    # 4. Detect stop intervals using remaining effective points
    effective_points = [p for p in points if p.status == KEEP]

    stop_events = detect_stop_events(effective_points)

    # 5. Merge stop fragments
    stop_events = merge_nearby_stops(stop_events)

    # 6. Delete all original points inside confirmed stop intervals
    for stop in stop_events:
        stop_points = points_between(points, stop.start_time, stop.end_time)

        for p in stop_points:
            if p.status == KEEP:
                p.status = DELETE_STOP_JITTER
                stop.deleted_point_count += 1

    # 7. Build clean track
    clean_points = []

    for p in points:
        if p.status == KEEP:
            clean_points.append(p)

    # 8. Insert synthetic stop anchors
    for stop in stop_events:
        clean_points.append(
            synthetic_point(
                timestamp = stop.start_time,
                x = stop.center_x,
                y = stop.center_y,
                source = "SYNTHETIC_STOP_START",
                stop_id = stop.id
            )
        )

        clean_points.append(
            synthetic_point(
                timestamp = stop.end_time,
                x = stop.center_x,
                y = stop.center_y,
                source = "SYNTHETIC_STOP_END",
                stop_id = stop.id
            )
        )

    # 9. Sort final cleaned points
    clean_points = sort_by_timestamp(clean_points)

    # 10. Reassign segment ids
    clean_points = assign_segments(clean_points, stop_events)

    # 11. Convert back to lat/lon if needed
    clean_points = unproject_to_lat_lon(clean_points)
    stop_events = unproject_stop_centers(stop_events)

    return clean_points, stop_events
```

---

# Spike deletion

```pseudo
function mark_isolated_spikes(points):

    for i from 1 to len(points) - 2:

        A = points[i - 1]
        B = points[i]
        C = points[i + 1]

        if A.status != KEEP or B.status != KEEP or C.status != KEEP:
            continue

        dt_ab = B.timestamp - A.timestamp
        dt_bc = C.timestamp - B.timestamp

        if dt_ab <= 0 or dt_bc <= 0:
            B.status = DELETE_INVALID
            continue

        d_ab = distance(A, B)
        d_bc = distance(B, C)
        d_ac = distance(A, C)

        # Classic jump-out-and-return pattern
        if d_ab > 50
           and d_bc > 50
           and d_ac < 25
           and dt_ab < 60
           and dt_bc < 60:

            B.status = DELETE_SPIKE
```

You can make this more adaptive later, but this simple rule catches many restaurant-style GPS spikes.

---

# Stop event detection

```pseudo
function detect_stop_events(points):

    state = "MOVING"
    rolling_buffer = empty deque
    current_stop = null
    escape_buffer = empty list
    stop_events = []

    max_window = max(profile.window_sec for profile in PROFILES)

    for p in points:

        # Handle large data gaps
        if rolling_buffer is not empty:
            previous = rolling_buffer.last()

            if is_large_time_gap(previous, p):
                if state == "STOPPED":
                    # Do not assume the stop continued through missing data.
                    # Close at last verified stopped time.
                    current_stop.end_time = current_stop.last_verified_time
                    stop_events.append(current_stop)

                    current_stop = null
                    escape_buffer.clear()
                    state = "MOVING"

                rolling_buffer.clear()

        rolling_buffer.append(p)
        remove_points_older_than(rolling_buffer, p.timestamp - max_window)

        if state == "MOVING":

            candidate = best_stop_candidate(rolling_buffer)

            if candidate.status == STOP_CANDIDATE:

                current_stop = new StopEvent()
                current_stop.category = candidate.category
                current_stop.start_time = candidate.start_time
                current_stop.end_time = candidate.end_time
                current_stop.center_x = candidate.center_x
                current_stop.center_y = candidate.center_y
                current_stop.radius_r80 = candidate.radius_r80
                current_stop.inlier_ratio = candidate.inlier_ratio
                current_stop.confidence = candidate.confidence
                current_stop.raw_point_count = count(candidate.cluster_points)
                current_stop.last_verified_time = candidate.end_time

                escape_buffer.clear()
                state = "STOPPED"

        else if state == "STOPPED":

            # Check whether the stop continues and whether it upgrades
            update_stop_with_point(current_stop, p)

            upgraded = classify_existing_stop(current_stop, points)

            if upgraded is not null:
                current_stop.category = upgraded.category
                current_stop.radius_r80 = upgraded.radius_r80
                current_stop.inlier_ratio = upgraded.inlier_ratio
                current_stop.confidence = upgraded.confidence
                current_stop.last_verified_time = p.timestamp

            # Check for real exit
            if distance_xy(p.x_meters, p.y_meters,
                           current_stop.center_x, current_stop.center_y) > current_stop_exit_radius(current_stop):

                escape_buffer.append(p)

            else:
                # The user returned near the stop center.
                # Any previous far-away points were failed escape drift.
                mark_failed_escape_points(escape_buffer)
                escape_buffer.clear()

            if escape_confirmed(escape_buffer, current_stop):

                exit_start_time = escape_buffer[0].timestamp

                current_stop.end_time = exit_start_time
                stop_events.append(current_stop)

                # Escape points should be kept as movement, not deleted.
                # They will be outside the stop interval.
                current_stop = null

                # Rebuild rolling buffer from escape points,
                # because they are now part of movement.
                rolling_buffer = deque(escape_buffer)
                escape_buffer.clear()

                state = "MOVING"

    # End of track
    if state == "STOPPED":
        current_stop.end_time = current_stop.last_verified_time
        stop_events.append(current_stop)

    return stop_events
```

---

# Finding the best stop candidate

```pseudo
function best_stop_candidate(rolling_buffer):

    candidates = []

    for profile in PROFILES:

        window_points = points_within_last_seconds(
            rolling_buffer,
            profile.window_sec
        )

        result = evaluate_stop_window(window_points, profile)

        if result.status == STOP_CANDIDATE:
            candidates.append(result)

    if candidates is empty:
        return EvalResult(status = NOT_STOPPED)

    # Prefer the longest category that passes.
    # Example: BREAK beats SHORT_STOP, SHORT_STOP beats MICRO_STOP.
    return highest_category_candidate(candidates)
```

---

# Stop window evaluation

```pseudo
function evaluate_stop_window(points, profile):

    if duration(points) < profile.min_duration_sec:
        return EvalResult(status = NOT_STOPPED)

    quality = sample_quality(points, profile)

    if quality == "INSUFFICIENT":
        return EvalResult(status = INSUFFICIENT_DATA)

    cluster = largest_compact_cluster(points, profile)

    if cluster is null:
        return EvalResult(status = NOT_STOPPED)

    cluster_duration = cluster.last.timestamp - cluster.first.timestamp

    if cluster_duration < profile.min_duration_sec:
        return EvalResult(status = NOT_STOPPED)

    center = robust_center(cluster)

    distances = []

    for p in cluster:
        distances.append(distance_xy(p.x_meters, p.y_meters, center.x, center.y))

    r80 = percentile(distances, 80)
    r90 = percentile(distances, 90)

    inlier_ratio = count(cluster) / count(points)

    if r80 > profile.r80_threshold_m:
        return EvalResult(status = NOT_STOPPED)

    if inlier_ratio < profile.min_inlier_ratio:
        return EvalResult(status = NOT_STOPPED)

    confidence = compute_confidence(
        profile = profile,
        sample_quality = quality,
        r80 = r80,
        r90 = r90,
        inlier_ratio = inlier_ratio,
        cluster_duration = cluster_duration
    )

    return StopCandidate(
        status = STOP_CANDIDATE,
        category = profile.category,
        start_time = cluster.first.timestamp,
        end_time = cluster.last.timestamp,
        center_x = center.x,
        center_y = center.y,
        radius_r80 = r80,
        radius_r90 = r90,
        inlier_ratio = inlier_ratio,
        confidence = confidence,
        cluster_points = cluster
    )
```

---

# Sample sufficiency gate

This is one of the most important parts.

```pseudo
function sample_quality(points, profile):

    if count(points) < profile.min_points:
        return "INSUFFICIENT"

    gaps = []

    for i from 1 to len(points) - 1:
        gaps.append(points[i].timestamp - points[i - 1].timestamp)

    if median(gaps) > profile.max_median_gap_sec:
        return "INSUFFICIENT"

    if max(gaps) > profile.max_gap_sec:
        return "INSUFFICIENT"

    coverage = time_coverage(points, profile)

    if coverage < profile.min_time_coverage:
        return "INSUFFICIENT"

    return "OK"
```

Time coverage can be calculated with bins.

```pseudo
function time_coverage(points, profile):

    start = points[0].timestamp
    end = points[-1].timestamp
    total_duration = end - start

    if total_duration <= 0:
        return 0

    # Short stops need small bins.
    # Long stops can use larger bins.
    bin_size = min(
        profile.max_median_gap_sec,
        profile.min_duration_sec / 4
    )

    bin_count = ceil(total_duration / bin_size)
    occupied = set()

    for p in points:
        bin_index = floor((p.timestamp - start) / bin_size)
        occupied.add(bin_index)

    return count(occupied) / bin_count
```

This prevents false detection from sparse samples.

---

# Largest compact cluster

You can use DBSCAN here.

```pseudo
function largest_compact_cluster(points, profile):

    clusters = DBSCAN(
        points = points,
        eps = profile.cluster_eps_m,
        min_samples = profile.min_cluster_points
    )

    if clusters is empty:
        return null

    valid_clusters = []

    for cluster in clusters:

        if count(cluster) < profile.min_cluster_points:
            continue

        center = robust_center(cluster)

        distances = []

        for p in cluster:
            distances.append(distance_xy(p.x_meters, p.y_meters, center.x, center.y))

        r80 = percentile(distances, 80)

        # Prevent DBSCAN chain effects.
        # A slow moving path can be density-connected,
        # but it will not be spatially compact.
        if r80 <= profile.r80_threshold_m:
            valid_clusters.append(cluster)

    if valid_clusters is empty:
        return null

    return cluster_with_most_points(valid_clusters)
```

The compactness check after DBSCAN is important. DBSCAN alone can sometimes chain together a slow-moving path.

---

# Existing stop classification / upgrading

Once a stop is active, it can upgrade.

```pseudo
function classify_existing_stop(current_stop, all_points):

    stop_points = points_between(
        all_points,
        current_stop.start_time,
        current_time()
    )

    candidates = []

    for profile in PROFILES:

        if duration(stop_points) < profile.min_duration_sec:
            continue

        result = evaluate_stop_window_or_interval(stop_points, profile)

        if result.status == STOP_CANDIDATE:
            candidates.append(result)

    if candidates is empty:
        return null

    return highest_category_candidate(candidates)
```

Example progression:

```text
00:30 → MICRO_STOP
01:30 → SHORT_STOP
03:00 → BREAK
10:00 → LONG_BREAK
```

The stop object remains the same. Only its category changes.

---

# Escape confirmation

```pseudo
function escape_confirmed(escape_buffer, current_stop):

    if escape_buffer is empty:
        return false

    duration = escape_buffer[-1].timestamp - escape_buffer[0].timestamp

    profile = profile_for_category(current_stop.category)

    if duration < profile.exit_duration_sec:
        return false

    if count(escape_buffer) < profile.min_exit_points:
        return false

    distances = []

    for p in escape_buffer:
        distances.append(
            distance_xy(
                p.x_meters,
                p.y_meters,
                current_stop.center_x,
                current_stop.center_y
            )
        )

    outside_ratio = count(d for d in distances if d > profile.exit_radius_m) / count(distances)

    if outside_ratio < 0.75:
        return false

    median_distance = median(distances)

    if median_distance <= profile.exit_radius_m:
        return false

    # Optional additional guard:
    # recent points should not be repeatedly returning to the stop center.
    recent_points = last_n_points(escape_buffer, 3)

    for p in recent_points:
        if distance_to_stop(p, current_stop) < profile.r80_threshold_m:
            return false

    return true
```

This prevents one GPS jump from ending the stop.

---

# Failed escape handling

While stopped, points that jump away and return should be deleted from the processed track.

```pseudo
function mark_failed_escape_points(escape_buffer):

    for p in escape_buffer:
        if p.status == KEEP:
            p.status = DELETE_FAILED_ESCAPE
```

Example:

```text
restaurant center
jump 100 m away
jump 120 m away
return to restaurant center
```

Those far-away points should not survive into the processed track.

---

# Merging nearby stop fragments

GPS drift may split one restaurant break into several pieces. Merge them after detection.

```pseudo
function merge_nearby_stops(stop_events):

    if stop_events is empty:
        return []

    merged = []
    current = stop_events[0]

    for next_stop in stop_events[1:]:

        time_gap = next_stop.start_time - current.end_time
        center_distance = distance_between_stops(current, next_stop)

        if time_gap <= 120
           and center_distance <= 60:

            current.end_time = next_stop.end_time
            current.center_x = weighted_average(
                current.center_x,
                next_stop.center_x,
                current.raw_point_count,
                next_stop.raw_point_count
            )
            current.center_y = weighted_average(
                current.center_y,
                next_stop.center_y,
                current.raw_point_count,
                next_stop.raw_point_count
            )

            current.raw_point_count += next_stop.raw_point_count
            current.deleted_point_count += next_stop.deleted_point_count
            current.category = max_category(current.category, next_stop.category)

        else:
            merged.append(current)
            current = next_stop

    merged.append(current)

    return merged
```

---

# Handling unknown gaps

Do not convert missing data into stops.

```pseudo
function is_large_time_gap(previous, current):

    dt = current.timestamp - previous.timestamp

    # This can be adaptive, but start simple.
    if dt > 180 seconds:
        return true

    return false
```

When a large gap happens:

```text
Do not infer movement.
Do not infer stop.
Split the clean track segment.
Classify the interval as UNKNOWN_GAP if needed.
```

This avoids false conclusions.

---

# Final filtered-track behavior

Suppose the raw track during a restaurant break looks like:

```text
12:00:00  arrive
12:00:10  GPS drift
12:00:20  GPS drift
12:00:30  GPS jump
12:01:00  GPS drift
...
12:30:00  leave
```

The processed output should become:

```text
Clean movement track:
    11:59:50  real movement point
    12:00:00  synthetic STOP_START at restaurant center
    12:30:00  synthetic STOP_END at restaurant center
    12:30:15  real movement point

Stop events:
    BREAK or LONG_BREAK
    start: 12:00:00
    end:   12:30:00
    center: restaurant center
    deleted points: all noisy restaurant GPS points
```

The fake scribble disappears from downstream processing.

---

# The final algorithm in one sentence

Use one shared, GPS-only stop detector that requires **sufficient temporal sampling**, finds a **compact spatial cluster**, classifies the stop using **duration-specific parameter profiles**, stabilizes decisions with a **hysteresis state machine**, and then deletes confirmed stop-jitter points from the processed track while preserving the stop as an event plus optional synthetic anchor points.
