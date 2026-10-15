#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
# Copyright (C) 2020-2026 Patrick Heusser and MTL Explorer contributors.
"""Check shipped Compose memory settings against the tuning guide; no containers."""

import json
from pathlib import Path
import re
import subprocess
import sys


REPOSITORY = Path(__file__).resolve().parents[3]
MIB = 1024 * 1024
COMPOSE_FILES = (
    "docker-compose.yml",
    "docker-compose-demo.yml",
    "docker-compose-map-server-public.yml",
)
RELATED_ENVIRONMENT = (
    "BROUTER_JAVA_XMX",
    "MTL_LOCATION_SEARCH_MMAP_SIZE",
    "MTL_PLANNER_BROUTER_TIMEOUT_MS",
    "LOWZOOM_MAXZOOM",
)


def compose_config(filename, override=None):
    command = ["docker", "compose", "--env-file", "/dev/null", "-f", filename]
    if override is not None:
        command.extend(["-f", "-"])
    command.extend(["--profile", "local-maps", "config", "--format", "json"])
    result = subprocess.run(command, cwd=REPOSITORY, input=override, text=True,
                            capture_output=True, check=True)
    return json.loads(result.stdout)["services"]


def check_services(services, documented_limits, reference):
    for name, service in services.items():
        limit = int(service["deploy"]["resources"]["limits"]["memory"])
        if limit != documented_limits[name]:
            raise ValueError(f"{name}: Compose memory differs from the guide")
        if int(service.get("memswap_limit", 0)) != limit:
            raise ValueError(f"{name}: memory and swap limits differ")
        environment = service.get("environment", {})
        expected = reference[name].get("environment", {})
        for key in RELATED_ENVIRONMENT:
            if environment.get(key) != expected.get(key):
                raise ValueError(f"{name}: {key} differs between Compose examples")
        if name == "db" and service.get("command") != reference[name].get("command"):
            raise ValueError("db: PostgreSQL tuning differs between Compose examples")
        if name == "app":
            for key in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"):
                if "-Xmx" in (environment.get(key) or ""):
                    raise ValueError(f"app: {key} pins the heap in a default example")
            command = service.get("command") or []
            if "-Xmx" in (command if isinstance(command, str) else " ".join(command)):
                raise ValueError("app: a default example's custom command pins the heap")


def main():
    guide = (REPOSITORY / "documentation/development/profiling.md").read_text()
    documented_limits = {
        name: int(value) * MIB
        for name, value in re.findall(
            r"^\| `([^`]+)`(?: \(optional\))? \| (\d+) MiB \|", guide, re.MULTILINE)
    }
    reference = compose_config(COMPOSE_FILES[0])
    if set(documented_limits) != set(reference):
        raise ValueError("The guide must list every service's memory limit")
    for name, settings in re.findall(
            r"^\| `([^`]+)`(?: \(optional\))? \| \d+ MiB \| (.*?) \|$", guide, re.MULTILINE):
        for key, value in re.findall(r"`([A-Z][A-Z0-9_]*|shared_buffers|work_mem|max_connections)=([^`]+)`", settings):
            if key.isupper():
                if reference[name].get("environment", {}).get(key) != value:
                    raise ValueError(f"{name}: documented {key} differs from Compose")
            elif f"{key}={value}" not in reference[name].get("command", []):
                raise ValueError(f"{name}: documented {key} differs from PostgreSQL tuning")
    sys.path.insert(0, str(REPOSITORY / "docker-location-search"))
    from location_search_server import DEFAULT_MMAP_SIZE
    if DEFAULT_MMAP_SIZE != int(reference["location-search"]["environment"]["MTL_LOCATION_SEARCH_MMAP_SIZE"]):
        raise ValueError("Location-search image and Compose mmap defaults differ")
    for filename in COMPOSE_FILES:
        check_services(compose_config(filename), documented_limits, reference)
    examples = re.findall(r"```yaml\n(.*?)\n```", guide, re.DOTALL)
    if not examples:
        raise ValueError("The guide has no Compose example")
    for example in examples:
        check_services(compose_config(COMPOSE_FILES[0], example), documented_limits, reference)
    heap_percentage = re.search(r"Image heap profile: (\d+)%;", guide)
    dockerfile = (REPOSITORY / "Dockerfile").read_text()
    if heap_percentage is None or f'"-XX:MaxRAMPercentage={heap_percentage[1]}.0"' not in dockerfile:
        raise ValueError("The documented heap percentage differs from the Dockerfile")
    print(f"PASS: {len(COMPOSE_FILES)} Compose files and {len(examples)} guide example; "
          "memory, swap, heap profile, and related settings agree")


if __name__ == "__main__":
    try:
        main()
    except (KeyError, ValueError, subprocess.CalledProcessError, FileNotFoundError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        sys.exit(1)
