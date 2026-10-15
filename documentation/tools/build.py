#!/usr/bin/env python3
"""Build the client and server in the development container, or check docs."""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone

REPOSITORY = Path(__file__).resolve().parents[2]
EXTERNAL_DRIVE = Path("/Volumes/EXT_HD_1TB")
BUILD_WORKSPACE = EXTERNAL_DRIVE / "mytraillog/development/workspace"
DEVELOPMENT_CONTAINER = "mtl-java-dev"
MAVEN_GOALS = ("compile", "test", "package", "verify")
SERVER_INPUTS = ("mtl-server/pom.xml", "mtl-server/src")
API_INPUTS = (
    "mtl-api/pom.xml", "mtl-api/mtl-api-typescript-fetch/pom.xml",
    "mtl-api/open-api-schema/schema.json",
)
CLIENT_INPUTS = (*API_INPUTS, "mtl-client")
CLIENT_REQUIRED_INPUTS = (*API_INPUTS, "mtl-client/pom.xml", "mtl-client/package.json")
CLIENT_NODE_DIRECTORY = PurePosixPath("mtl-api/mtl-api-typescript-fetch/node/node")
CLIENT_NPM_CACHE_DIRECTORY = ".npm-cache"
DEFAULT_CONTAINER_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
MAVEN_FAILURE_OPTIONS = ("-fae", "-ff", "-fn")


def git(repository, *arguments):
    return subprocess.check_output(["git", "-C", str(repository), *arguments])


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_files(repository, scope="both"):
    inputs = ("pom.xml",)
    required_inputs = ("pom.xml",)
    if scope != "client":
        inputs += SERVER_INPUTS
        required_inputs += SERVER_INPUTS[:1]
    if scope != "server":
        inputs += CLIENT_INPUTS
        required_inputs += CLIENT_REQUIRED_INPUTS
    names = git(repository, "ls-files", "-z", "--cached", "--others",
                "--exclude-standard", "--", *inputs)
    files = []
    for raw_name in sorted(set(names.split(b"\0")) - {b""}):
        name = os.fsdecode(raw_name)
        path = repository / name
        if not path.exists() and not path.is_symlink():
            continue  # Tracked files deleted in the working tree stay deleted.
        if path.is_symlink() or not path.resolve().is_relative_to(repository):
            raise ValueError(f"Build inputs must be regular repository files: {name}")
        if not path.is_file() or any(character in name for character in "\n\r\\"):
            raise ValueError(f"Unsupported build input: {name}")
        files.append(name)
    for required in required_inputs:
        if required not in files:
            raise ValueError(f"Missing build input: {required}")
    return files


def copy_sources(repository, snapshot, scope="both"):
    hashes = {}
    for name in source_files(repository, scope):
        destination = snapshot / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(repository / name, destination)
        hashes[name] = digest(destination)
    if any(digest(repository / name) != checksum for name, checksum in hashes.items()):
        raise ValueError("Source changed while copying; run the build again")
    return hashes


def container_workspace(workspace, mounts):
    candidates = []
    for mount in mounts:
        if mount.get("Type") != "bind":
            continue
        host_path = Path(mount["Source"]).resolve()
        if workspace.is_relative_to(host_path):
            candidates.append((host_path, mount))
    if not candidates:
        raise ValueError("The build workspace must be bind-mounted in the development container")
    host_path, mount = max(candidates, key=lambda candidate: len(candidate[0].parts))
    if not mount.get("RW"):
        raise ValueError("The development container's build workspace is read-only")
    return PurePosixPath(mount["Destination"]) / workspace.relative_to(host_path).as_posix()


def verify_container_copy(container, snapshot, hashes):
    checksums = "".join(f"{checksum}  {name}\n" for name, checksum in hashes.items())
    subprocess.run(["docker", "exec", "-i", "-w", str(snapshot), container,
                    "sha256sum", "--check", "--quiet", "-"],
                   input=checksums, text=True, check=True)


def validate_maven_arguments(arguments):
    if any(argument.startswith(("-f", "--file")) and argument not in MAVEN_FAILURE_OPTIONS
           for argument in arguments):
        raise ValueError("The wrapper selects the verified build POMs; do not pass -f/--file")


def prune_snapshots(repository, workspace, scope, keep):
    """Prune only successful snapshots explicitly made disposable by --keep."""
    candidates = []
    for snapshot in workspace.iterdir():
        record_path = snapshot / "build.json"
        if (snapshot.is_symlink() or not snapshot.is_dir() or (snapshot / ".keep").exists()
                or record_path.is_symlink() or not record_path.is_file()):
            continue
        try:
            record = json.loads(record_path.read_text())
            if (not isinstance(record, dict) or record.get("disposable") is not True
                    or record.get("exit_code") != 0
                    or record.get("source_repository") != str(repository)
                    or record.get("scope") != scope or record.get("goal") not in MAVEN_GOALS
                    or not snapshot.name.startswith(f"{scope}-{record['goal']}-")):
                continue
            candidates.append((record_path.stat().st_mtime_ns, snapshot))
        except (OSError, ValueError):
            continue  # Incomplete or unrecognized snapshots remain untouched.
    for _, snapshot in sorted(candidates, reverse=True)[keep:]:
        if (snapshot / ".keep").exists():
            continue
        try:
            shutil.rmtree(snapshot)
            print(f"Removed disposable build snapshot: {snapshot}", flush=True)
        except OSError as error:
            print(f"Could not remove disposable build snapshot {snapshot}: {error}", file=sys.stderr)


def container_build(repository, workspace, container, goal, arguments, scope="both", keep=None):
    if not EXTERNAL_DRIVE.is_mount():
        raise ValueError(f"Required external drive is not mounted: {EXTERNAL_DRIVE}")
    workspace = workspace.expanduser().resolve()
    if not workspace.is_relative_to(EXTERNAL_DRIVE) or not workspace.is_dir():
        raise ValueError("Use an existing build workspace on the mounted external drive")
    if keep is not None and keep < 1:
        raise ValueError("--keep must be at least 1")
    validate_maven_arguments(arguments)
    inspection = json.loads(subprocess.check_output(["docker", "inspect", container]))[0]
    mounted_workspace = container_workspace(workspace, inspection["Mounts"])
    if not inspection["State"]["Running"]:
        subprocess.run(["docker", "start", container], check=True)

    snapshot = Path(tempfile.mkdtemp(prefix=f"{scope}-{goal}-", dir=workspace))
    mounted_snapshot = mounted_workspace / snapshot.name
    print(f"Build workspace: {snapshot}", flush=True)
    if scope == "server" and goal in ("package", "verify"):
        print("Server-only packages contain no bundled UI; use the default build for a complete application.",
              file=sys.stderr)
    hashes = copy_sources(repository, snapshot, scope)
    verify_container_copy(container, mounted_snapshot, hashes)
    commands = []
    if scope != "server":
        npm_cache = f"NPM_CONFIG_CACHE={mounted_workspace / CLIENT_NPM_CACHE_DIRECTORY}"
        commands.append(["docker", "exec", "-e", npm_cache,
                         "-w", str(mounted_snapshot / "mtl-api"),
                         container, "mvn", "-B", "install", "-DskipTests"])
        # The API Maven plugin installs Node/npm inside the verified snapshot.
        container_path = next((value.removeprefix("PATH=")
                               for value in inspection["Config"].get("Env", [])
                               if value.startswith("PATH=")), DEFAULT_CONTAINER_PATH)
        client_path = f"{mounted_snapshot / CLIENT_NODE_DIRECTORY}:{container_path}"
        commands.append(["docker", "exec", "-e", f"PATH={client_path}", "-e", npm_cache,
                         "-w", str(mounted_snapshot / "mtl-client"),
                         container, "mvn", "-B", goal if scope == "client" else "package",
                         *(arguments if scope == "client" else [])])
    if scope != "client":
        commands.append(["docker", "exec", "-w", str(mounted_snapshot / "mtl-server"),
                         container, "mvn", "-B", goal, *arguments])
    record = {
        "started_at": datetime.now(timezone.utc).isoformat(),
        "source_commit": git(repository, "rev-parse", "HEAD").decode().strip(),
        "source_repository": str(repository),
        "disposable": keep is not None,
        "dirty_source": bool(git(repository, "status", "--porcelain")),
        "container": container,
        "container_image": inspection["Config"]["Image"],
        "container_image_id": inspection["Image"],
        "goal": goal,
        "scope": scope,
        "commands": commands,
        "source_sha256": hashes,
    }
    record_path = snapshot / "build.json"
    record_path.write_text(json.dumps(record, indent=2) + "\n")
    started = time.monotonic()
    with (snapshot / "build.log").open("w") as log:
        for command in commands:
            step = f"Building: {command[command.index('-w') + 1]}\n"
            print(step, end="", flush=True)
            log.write(step)
            with subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, errors="replace") as process:
                for line in process.stdout:
                    sys.stdout.write(line)
                    log.write(line)
                result = process.wait()
            if result:
                break
    record.update(exit_code=result, duration_seconds=round(time.monotonic() - started, 3))
    record_path.write_text(json.dumps(record, indent=2) + "\n")
    if result == 0 and keep is not None:
        try:
            prune_snapshots(repository, workspace, scope, keep)
        except OSError as error:
            print(f"Could not prune disposable build snapshots in {workspace}: {error}", file=sys.stderr)
    return result


def documentation_build(repository, interpreter, arguments):
    if arguments not in ([], ["--check"]):
        raise ValueError("Documentation accepts only --check")
    python = interpreter.expanduser() if interpreter else repository / ".venv/bin/python"
    if not python.is_file():
        raise ValueError("Documentation needs the project .venv or --docs-python; see the tooling guide")
    subprocess.run([str(python), "-c", "import markdown_it"], check=True)
    tools = repository / "documentation/tools/website"
    subprocess.run([str(python), str(tools / "generate_documentation.py"), *arguments], check=True)
    subprocess.run([str(python), str(tools / "check_documentation.py")], check=True)
    subprocess.run([str(python), str(tools / "generate_releases.py"), "--check"], check=True)
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path(os.environ.get("MTL_SOURCE_REPO", REPOSITORY)))
    parser.add_argument("--container", default=DEVELOPMENT_CONTAINER)
    parser.add_argument("--workspace-root", type=Path, default=BUILD_WORKSPACE)
    parser.add_argument("--docs-python", type=Path)
    parser.add_argument("--keep", type=int,
                       help="Keep N successful disposable snapshots per checkout/component; other snapshots are retained")
    scope = parser.add_mutually_exclusive_group()
    scope.add_argument("--client-only", dest="scope", action="store_const", const="client",
                       help="Build the API package and client without building the server")
    scope.add_argument("--server-only", dest="scope", action="store_const", const="server",
                       help="Build the server without building the API package or client")
    parser.set_defaults(scope="both")
    parser.add_argument("action", choices=(*MAVEN_GOALS, "docs"))
    parser.add_argument("arguments", nargs=argparse.REMAINDER, help="Maven options, or docs --check")
    args = parser.parse_args(argv)
    arguments = args.arguments[1:] if args.arguments[:1] == ["--"] else args.arguments
    repository = args.source.expanduser().resolve()
    try:
        if args.action == "docs":
            if args.scope != "both" or args.keep is not None:
                raise ValueError("--client-only/--server-only/--keep apply only to container build actions")
            return documentation_build(repository, args.docs_python, arguments)
        return container_build(repository, args.workspace_root, args.container, args.action,
                               arguments, scope=args.scope, keep=args.keep)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Build failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
