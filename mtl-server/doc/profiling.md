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

With ZGC, process RSS can overcount heap pages because the collector maps the same physical memory more than once. Use cgroup usage and OOM events for container decisions.
