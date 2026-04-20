# JFR Profiling

# CPU + Memory (60s recording)
jcmd 1 JFR.start name=nas_profile settings=profile duration=60s filename=/app/logs/mtl_cpu_profile.jfr

# Heap dump (live objects only)
jcmd 1 GC.heap_dump /app/logs/mtl_heap_snapshot.hprof

# Thread dump
jstack 1 > /app/logs/threaddump.txt

