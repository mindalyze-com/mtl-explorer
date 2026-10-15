# JFR Profiling

The runtime image includes `jattach`, not a full JDK. Replace `<container>` with the app container name.

```bash
# Start a bounded profile recording.
docker exec <container> jattach 1 jcmd \
  'JFR.start name=mtl_profile settings=profile maxage=30m maxsize=256m'

# Dump before stopping the container. An OOM SIGKILL cannot flush a recording.
docker exec <container> jattach 1 jcmd \
  'JFR.dump name=mtl_profile filename=/app/logs/mtl-profile.jfr'

# Inspect heap capacity and obtain a thread dump.
docker exec <container> jattach 1 jcmd 'GC.heap_info'
docker exec <container> jattach 1 jcmd 'Thread.print -l'
```

For container memory, use cgroup counters:

```bash
docker exec <container> cat /sys/fs/cgroup/memory.current
docker exec <container> cat /sys/fs/cgroup/memory.peak
docker exec <container> cat /sys/fs/cgroup/memory.events
```

The source image uses G1 with `MaxRAMPercentage=40.0` and
`InitialRAMPercentage=6.25`. At the default 1248 MiB app container limit,
Java 25 G1 rounds the maximum heap to 500 MiB and the initial heap to 78 MiB.
A 1176 MiB limit gives a 472 MiB maximum heap and a 74 MiB initial heap.

Demo preparation finishes before Java starts. The remaining 60% of the app
limit covers JVM native allocations and the shared media process slot, which
allows one libvips, ImageMagick, FFprobe or FFmpeg child at a time by default.
Heap capacity is only part of the JVM's total memory usage. A synthetic 4K HEVC
to 1080p HLS conversion used about 328 MiB of FFmpeg RSS; a 256 MiB helper
container failed, while 384 MiB completed. Historical JVM native allocations
were about 300 MiB. These separate measurements guide the budget; they do not
establish a safe combined limit for every workload. A focused ARM64 check ran
synthetic GPX imports and 1080p conversion together under the default app limit.
Larger workloads and AMD64 memory peaks remain unverified. Larger media or more
concurrent helpers may require a larger app limit.

Upgrade to an image containing this profile to apply the new percentage.
Raising both Compose memory and swap limits and recreating the app container
scales the heap according to the running image's percentage. Other limits or
JVM options may change the effective heap; check it with `GC.heap_info` above.

Remove any fixed `-Xmx` override from `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`,
or a custom Java command when adopting automatic heap sizing. Keep unrelated
JVM options. A fixed heap size takes precedence over the percentage.
See [container memory limits](#container-memory-limits) for the Compose settings.

The published 0.8.1 Dockerfile used ZGC with `MaxRAMPercentage=50.0`.
ZGC process RSS could count multiple mappings of the same heap pages. Use
cgroup usage and OOM events for container decisions, and inspect the running
image's JVM arguments before comparing profiles.

## Container memory limits

The [default Compose file](../../docker-compose.yml) uses these settings.
Memory and swap limits are equal, which disables container swap.

| Service | Memory and swap limit | Related settings |
| --- | --- | --- |
| `app` | 1248 MiB | Image heap profile: 40%; routing timeout: `MTL_PLANNER_BROUTER_TIMEOUT_MS=310000` |
| `db` | 512 MiB | Keep the complete PostgreSQL command, including `shared_buffers=64MB`, `work_mem=1MB`, and `max_connections=20` |
| `brouter` | 512 MiB | `BROUTER_JAVA_XMX=128m` |
| `location-search` | 128 MiB | `MTL_LOCATION_SEARCH_MMAP_SIZE=33554432` (32 MiB) |
| `map-server` (optional) | 128 MiB | `LOWZOOM_MAXZOOM=6` |

The limits are at least 20 percent above historical validated limits: 640 MiB
for the standard app, 384 MiB for PostgreSQL and BRouter, and 96 MiB for location
search and maps. Those checks used earlier app workloads and heap settings;
they do not establish a 20 percent margin for the current app with 1080p conversion.
BRouter was checked with routes up to about 1,500 km. The map limit was checked
while extracting its zoom 0-6 overview and serving concurrent range requests.
Compose allows a routing request up to 310 seconds, just above BRouter's
300-second route ceiling. Large image or video conversions use the app headroom
one at a time. Docker and the operating system need additional memory.

When raising a limit, set the memory and swap limits to the same value, then
recreate the container. A process restart does not apply changed limits or
environment settings.

Every installation uses a local Compose file, including copies held by NAS
container managers. Compare it with the new release's template and merge the
settings for every service you run. Keep your credentials, ports, mounts, and
deliberately larger limits. Apply the related settings above together with
their memory limits; copy the full PostgreSQL command from the template.

For the current image, the default app limit needs no fixed heap override.
Merge this example into the existing app service:

```yaml
services:
  app:
    memswap_limit: 1248m
    deploy:
      resources:
        limits:
          memory: 1248m
```

Validate the edited Compose file with `docker compose config --quiet`, then
recreate the affected services with `docker compose up -d`. With the current
image, the default 1248 MiB limit, and no fixed heap override, the app startup
log should report `heapMaxMiB=500.0`. Larger limits produce a larger heap;
check the effective size with `GC.heap_info`.
