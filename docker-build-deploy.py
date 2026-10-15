#!/usr/bin/env python3
"""
Guided Docker build and deploy helper for MTL Explorer.

Default interactive flow:
  1. Show a start screen with every release option.
  2. Let you edit/toggle version tags, channels, build mode, and verification.
  3. Publish immutable version tags and the alpha channel by default.
  4. Publish beta/latest only when you enable them.
  5. Build multiple platforms by default when full-build mode is enabled.

Full builds compile a committed app source snapshot inside the app Dockerfile.
Host Maven outputs are not used for the app image.

Examples:
  ./docker-build-deploy.py
  ./docker-build-deploy.py --tag-only
  ./docker-build-deploy.py --yes --include-beta
  ./docker-build-deploy.py --yes --app-only
  ./docker-build-deploy.py --dry-run --yes --include-latest
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import subprocess
import sys
import tarfile
import tempfile
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Sequence

from documentation.tools.repository_paths import private_repository


# =============================================================================
# USER VARIABLES - EDIT THESE FIRST
# =============================================================================

DOCKER_USERNAME = "wauwau0977"
DOCKER_HUB_REGISTRY_URL = "https://index.docker.io/v1/"
DOCKER_HUB_TOKEN_URLS = (
    "https://index.docker.io/v1/access-token",
    "https://index.docker.io/v1/refresh-token",
)

# Version tags. These are the most important values to check before every run.
APP_IMAGE_NAME = "mytraillog"
APP_VERSION_TAG = "1.501"  # version tag, (v. 1.1 -> 2025-10 release)

MAP_IMAGE_NAME = "mytraillog-maps"
MAP_VERSION_TAG = "1.501"

LOCATION_SEARCH_IMAGE_NAME = "mytraillog-location-search"
LOCATION_SEARCH_VERSION_TAG = "1.501"

BROUTER_IMAGE_NAME = "mytraillog-brouter"
BROUTER_VERSION_TAG = "1.501"

# Channel tags. Version + alpha are published by default; beta/latest are opt-in.
ALPHA_TAG = "alpha"
BETA_TAG = "beta"
LATEST_TAG = "latest"

# Guided defaults.
DEFAULT_FULL_BUILD = True
DEFAULT_INCLUDE_ALPHA = True
DEFAULT_INCLUDE_BETA = False
DEFAULT_INCLUDE_LATEST = False
DEFAULT_MULTI_PLATFORM = True
DEFAULT_NO_CACHE = False
DEFAULT_DOCKER_LOGIN = True
DEFAULT_PULL_AFTER_PUBLISH = True
DEFAULT_FORCE_DOCKER_LOGIN = False

# Build settings.
MULTI_PLATFORM_PLATFORMS = "linux/amd64,linux/arm64"
SINGLE_PLATFORM = "linux/amd64"
BUILDX_BUILDER_NAME = "multi-builder"

# =============================================================================
# SCRIPT CONSTANTS
# =============================================================================

SCRIPT_DIR = Path(__file__).resolve().parent
RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
RED = "\033[31m"
GREEN = "\033[32m"
YELLOW = "\033[33m"
CYAN = "\033[36m"
USE_COLOR = sys.stdout.isatty()
SOURCE_TREE_LABEL = "com.mtl-explorer.source-tree"
RELEASE_RECORD_SUBDIRECTORY = Path("docs-my/operations/releases/builds")


@dataclass(frozen=True)
class ImageConfig:
    label: str
    image_name: str
    version_tag: str
    context_dir: Path
    dockerfile: Path | None = None


@dataclass(frozen=True)
class ReleaseSettings:
    docker_username: str
    app_version_tag: str
    map_version_tag: str
    location_search_version_tag: str
    brouter_version_tag: str
    include_alpha: bool
    include_beta: bool
    include_latest: bool
    full_build: bool
    multi_platform: bool
    no_cache: bool
    docker_login: bool
    force_docker_login: bool
    pull_after_publish: bool
    dry_run: bool
    app_only: bool = False
    release_record_dir: Path | None = None

    @property
    def platforms(self) -> str:
        return MULTI_PLATFORM_PLATFORMS if self.multi_platform else SINGLE_PLATFORM

    @property
    def channel_tags(self) -> list[str]:
        tags: list[str] = []
        if self.include_alpha:
            tags.append(ALPHA_TAG)
        if self.include_beta:
            tags.append(BETA_TAG)
        if self.include_latest:
            tags.append(LATEST_TAG)
        return unique(tags)


@dataclass
class SettingsDraft:
    app_version_tag: str
    map_version_tag: str
    location_search_version_tag: str
    brouter_version_tag: str
    include_alpha: bool
    include_beta: bool
    include_latest: bool
    full_build: bool
    multi_platform: bool
    no_cache: bool
    docker_login: bool
    force_docker_login: bool
    pull_after_publish: bool
    dry_run: bool
    app_only: bool = False
    release_record_dir: Path | None = None

    @property
    def platforms(self) -> str:
        return MULTI_PLATFORM_PLATFORMS if self.multi_platform else SINGLE_PLATFORM

    @property
    def channel_tags(self) -> list[str]:
        tags: list[str] = []
        if self.include_alpha:
            tags.append(ALPHA_TAG)
        if self.include_beta:
            tags.append(BETA_TAG)
        if self.include_latest:
            tags.append(LATEST_TAG)
        return unique(tags)


@dataclass
class StepResult:
    label: str
    status: str
    detail: str = ""


def color(code: str, text: str) -> str:
    if not USE_COLOR:
        return text
    return f"{code}{text}{RESET}"


def banner(text: str) -> None:
    print()
    print(color(CYAN, "=" * 78))
    print(color(CYAN + BOLD, f"  {text}"))
    print(color(CYAN, "=" * 78))


def section(text: str) -> None:
    print()
    print(color(BOLD, f"{text}"))
    print(color(DIM, "-" * len(text)))


def info(text: str) -> None:
    print(color(DIM, text))


def warn(text: str) -> None:
    print(color(YELLOW, text))


def error(text: str) -> None:
    print(color(RED, text))


def ok(text: str) -> None:
    print(color(GREEN, text))


def clear_screen() -> None:
    if sys.stdin.isatty() and sys.stdout.isatty():
        print("\033[2J\033[H", end="")
        sys.stdout.flush()


def shlex_join(cmd: Sequence[object]) -> str:
    return " ".join(shlex.quote(str(part)) for part in cmd)


def unique(items: Iterable[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for item in items:
        if item not in seen:
            result.append(item)
            seen.add(item)
    return result


def utc_timestamp() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def git_output(*args: str, root: Path = SCRIPT_DIR) -> str:
    return subprocess.check_output(["git", *args], cwd=root, text=True).rstrip("\n")


def app_source_identity(root: Path = SCRIPT_DIR, *, dry_run: bool = False) -> dict[str, str]:
    # Private run evidence belongs to its own checkout and cannot enter this archive.
    changes = git_output("status", "--porcelain=v1", "--untracked-files=all", root=root)
    pending = changes.splitlines()
    if pending and not dry_run:
        raise SystemExit("Commit source changes before publishing an app image:\n" + "\n".join(pending))
    if pending:
        warn("Dry run uses HEAD metadata; commit source changes before publishing.")
    revision = git_output("rev-parse", "HEAD", root=root)
    return {"revision": revision, "tree": git_output("rev-parse", f"{revision}^{{tree}}", root=root)}


@contextmanager
def app_source_snapshot(revision: str, root: Path = SCRIPT_DIR):
    with tempfile.TemporaryDirectory(prefix="mtl-app-release-") as temporary:
        directory = Path(temporary)
        archive = directory / "source.tar"
        subprocess.run(["git", "archive", "--format=tar", f"--output={archive}", revision],
                       cwd=root, check=True)
        source = directory / "source"
        source.mkdir()
        with tarfile.open(archive) as contents:
            contents.extractall(source, filter="data")
        archive.unlink()
        yield source


def prompt_text(question: str, default: str) -> str:
    while True:
        try:
            raw_answer = input(f"{question} [{default}]: ").strip()
        except KeyboardInterrupt:
            print()
            raise SystemExit("Interrupted.")
        except EOFError:
            print()
            raise SystemExit("No input available. Re-run with --yes to use defaults.")

        value = raw_answer or default
        if value:
            return value
        print("Please enter a non-empty value.")


def remote_ref(username: str, image_name: str, tag: str) -> str:
    return f"{username}/{image_name}:{tag}"


def docker_config_path() -> Path:
    return Path(os.environ.get("DOCKER_CONFIG", Path.home() / ".docker")) / "config.json"


def load_docker_config() -> dict[str, object]:
    config_path = docker_config_path()
    try:
        content = config_path.read_text(encoding="utf-8")
    except FileNotFoundError:
        return {}
    except OSError:
        return {}

    try:
        config = json.loads(content)
    except json.JSONDecodeError:
        return {}
    return config if isinstance(config, dict) else {}


def docker_hub_credentials_available() -> bool:
    config = load_docker_config()
    if docker_config_auth_available(config):
        return True

    helper = docker_credential_helper(config, DOCKER_HUB_REGISTRY_URL)
    if not helper:
        return False

    return any(credential_helper_entry_exists(helper, url) for url in [DOCKER_HUB_REGISTRY_URL, *DOCKER_HUB_TOKEN_URLS])


def docker_config_auth_available(config: dict[str, object]) -> bool:
    auths = config.get("auths")
    if not isinstance(auths, dict):
        return False

    for url in [DOCKER_HUB_REGISTRY_URL, *DOCKER_HUB_TOKEN_URLS]:
        entry = auths.get(url)
        if isinstance(entry, dict) and (entry.get("auth") or entry.get("identitytoken")):
            return True
    return False


def docker_credential_helper(config: dict[str, object], registry_url: str) -> str | None:
    cred_helpers = config.get("credHelpers")
    if isinstance(cred_helpers, dict):
        for key in [registry_url, "index.docker.io", "registry-1.docker.io"]:
            helper = cred_helpers.get(key)
            if isinstance(helper, str) and helper:
                return helper

    creds_store = config.get("credsStore")
    return creds_store if isinstance(creds_store, str) and creds_store else None


def credential_helper_entry_exists(helper: str, server_url: str) -> bool:
    helper_bin = helper if helper.startswith("docker-credential-") else f"docker-credential-{helper}"
    try:
        result = subprocess.run(
            [helper_bin, "get"],
            input=server_url,
            text=True,
            capture_output=True,
            timeout=5,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    return result.returncode == 0


def describe_tags(version_tag: str, channel_tags: Sequence[str]) -> str:
    return " + ".join([version_tag, *channel_tags])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Guided Docker build/deploy script for MTL Explorer.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument("--yes", action="store_true", help="Use defaults and do not prompt.")
    parser.add_argument("--dry-run", action="store_true", help="Print the plan and commands without running them.")
    parser.add_argument("--app-only", action="store_true", help="Build or tag only the app image.")

    mode_group = parser.add_mutually_exclusive_group()
    mode_group.add_argument("--full-build", dest="full_build", action="store_true", default=None)
    mode_group.add_argument("--tag-only", dest="full_build", action="store_false")

    platform_group = parser.add_mutually_exclusive_group()
    platform_group.add_argument("--multi-platform", dest="multi_platform", action="store_true", default=None)
    platform_group.add_argument("--multi-platform-app", dest="multi_platform", action="store_true")
    platform_group.add_argument("--single-platform", dest="multi_platform", action="store_false")

    parser.add_argument("--alpha", dest="include_alpha", action="store_true", default=None)
    parser.add_argument("--no-alpha", dest="include_alpha", action="store_false")
    parser.add_argument("--include-beta", "--publish-beta", dest="include_beta", action="store_true", default=None)
    parser.add_argument("--include-latest", "--publish-latest", dest="include_latest", action="store_true", default=None)
    parser.add_argument("--tag-beta-only", action="store_true", help="Compatibility alias: tag existing version images as beta only.")
    parser.add_argument("--tag-latest-only", action="store_true", help="Compatibility alias: tag existing version images as latest only.")

    parser.add_argument(
        "--skip-maven",
        action="store_true",
        help="Deprecated no-op. The app Dockerfile builds the JAR inside Docker.",
    )
    parser.add_argument("--no-cache", action="store_true", help="Disable Docker build cache.")
    parser.add_argument("--no-login", action="store_true", help="Do not run docker login before publishing.")
    parser.add_argument("--force-login", action="store_true", help="Run docker login even if Docker Hub credentials already exist.")
    parser.add_argument("--no-pull-verify", action="store_true", help="Do not pull published refs after a full build.")

    parser.add_argument("--app-tag", default=None, help="Override the app version tag for this run.")
    parser.add_argument("--map-tag", default=None, help="Override the map image version tag for this run.")
    parser.add_argument("--location-search-tag", default=None, help="Override the location-search image version tag for this run.")
    parser.add_argument("--brouter-tag", default=None, help="Override the BRouter image version tag for this run.")
    parser.add_argument("--release-record-dir", type=Path,
                        help="Directory outside the application checkout for release manifests; defaults to the private checkout's docs-my/operations/releases/builds (MTL_PRIVATE_REPO overrides the checkout).")

    args = parser.parse_args()
    apply_compatibility_aliases(args, parser)
    return args


def apply_compatibility_aliases(args: argparse.Namespace, parser: argparse.ArgumentParser) -> None:
    tag_only_aliases = [args.tag_beta_only, args.tag_latest_only]
    if sum(1 for enabled in tag_only_aliases if enabled) > 1:
        parser.error("Use only one tag-only alias.")

    if args.tag_beta_only:
        args.full_build = False
        args.include_alpha = False
        args.include_beta = True
        args.include_latest = False

    if args.tag_latest_only:
        args.full_build = False
        args.include_alpha = False
        args.include_beta = False
        args.include_latest = True


def build_settings(args: argparse.Namespace) -> ReleaseSettings:
    draft = initial_settings_draft(args)

    if args.yes:
        banner("MTL Explorer Docker Release")
        print_user_variables(draft.app_version_tag, draft.map_version_tag, draft.location_search_version_tag, draft.brouter_version_tag)
    else:
        configure_interactively(draft)

    if args.skip_maven:
        warn("--skip-maven is ignored; the app Dockerfile builds the JAR inside Docker.")

    settings = release_settings_from_draft(draft)

    if not settings.full_build and not settings.channel_tags:
        raise SystemExit("Tag-only mode needs at least one channel tag.")

    print_plan(settings)
    return settings


def initial_settings_draft(args: argparse.Namespace) -> SettingsDraft:
    full_build = args.full_build if args.full_build is not None else DEFAULT_FULL_BUILD
    multi_platform = args.multi_platform if args.multi_platform is not None else DEFAULT_MULTI_PLATFORM
    no_cache = DEFAULT_NO_CACHE or args.no_cache

    return SettingsDraft(
        app_version_tag=args.app_tag or APP_VERSION_TAG,
        map_version_tag=args.map_tag or MAP_VERSION_TAG,
        location_search_version_tag=args.location_search_tag or LOCATION_SEARCH_VERSION_TAG,
        brouter_version_tag=args.brouter_tag or BROUTER_VERSION_TAG,
        include_alpha=args.include_alpha if args.include_alpha is not None else DEFAULT_INCLUDE_ALPHA,
        include_beta=args.include_beta if args.include_beta is not None else DEFAULT_INCLUDE_BETA,
        include_latest=args.include_latest if args.include_latest is not None else DEFAULT_INCLUDE_LATEST,
        full_build=full_build,
        multi_platform=multi_platform,
        no_cache=no_cache if full_build else False,
        docker_login=DEFAULT_DOCKER_LOGIN and not args.no_login,
        force_docker_login=DEFAULT_FORCE_DOCKER_LOGIN or args.force_login,
        pull_after_publish=DEFAULT_PULL_AFTER_PUBLISH and not args.no_pull_verify if full_build else False,
        dry_run=args.dry_run,
        app_only=args.app_only,
        release_record_dir=args.release_record_dir,
    )


def release_record_directory(output: Path | None = None) -> Path:
    if output is None:
        private = private_repository(SCRIPT_DIR)
        if not (private / ".git").exists():
            raise SystemExit(
                f"Private checkout not found: {private}. "
                "Clone it, set MTL_PRIVATE_REPO, or supply --release-record-dir."
            )
        output = private / RELEASE_RECORD_SUBDIRECTORY
    directory = output.expanduser().resolve()
    if directory.is_relative_to(SCRIPT_DIR):
        raise SystemExit("Release records must be stored outside the application checkout.")
    return directory


def release_settings_from_draft(draft: SettingsDraft) -> ReleaseSettings:
    return ReleaseSettings(
        docker_username=DOCKER_USERNAME,
        app_version_tag=draft.app_version_tag,
        map_version_tag=draft.map_version_tag,
        location_search_version_tag=draft.location_search_version_tag,
        brouter_version_tag=draft.brouter_version_tag,
        include_alpha=draft.include_alpha,
        include_beta=draft.include_beta,
        include_latest=draft.include_latest,
        full_build=draft.full_build,
        multi_platform=draft.multi_platform,
        no_cache=draft.no_cache if draft.full_build else False,
        docker_login=draft.docker_login,
        force_docker_login=draft.force_docker_login,
        pull_after_publish=draft.pull_after_publish if draft.full_build else False,
        dry_run=draft.dry_run,
        app_only=draft.app_only,
        release_record_dir=release_record_directory(draft.release_record_dir) if draft.full_build else None,
    )


def configure_interactively(draft: SettingsDraft) -> None:
    notice = "Defaults are version + alpha, full build, and multi-platform images."
    while True:
        clear_screen()
        print_options_menu(draft, notice)
        notice = ""
        choice = prompt_menu_choice()

        if choice == "s":
            if not draft.full_build and not draft.channel_tags:
                notice = "Tag-only mode needs at least one channel tag enabled."
                continue
            clear_screen()
            return
        if choice == "q":
            clear_screen()
            raise SystemExit("Aborted.")

        notice = handle_menu_choice(draft, choice)


def prompt_menu_choice() -> str:
    try:
        return input("Select a number to edit/toggle, S to start, Q to quit: ").strip().lower()
    except KeyboardInterrupt:
        print()
        raise SystemExit("Interrupted.")
    except EOFError:
        print()
        raise SystemExit("No input available. Re-run with --yes to use defaults.")


def print_options_menu(draft: SettingsDraft, notice: str = "") -> None:
    banner("MTL Explorer Docker Release")
    print("Edit the release plan before anything starts.")
    if notice:
        print(color(YELLOW, f"Status: {notice}"))
    print()
    print(f"  1  Operation            {operation_label(draft)}")
    print(f"  2  App version tag      {draft.app_version_tag}")
    print(f"  3  Map version tag      {draft.map_version_tag}")
    print(f"  4  Location search tag  {draft.location_search_version_tag}")
    print(f"  5  BRouter version tag  {draft.brouter_version_tag}")
    print(f"  6  Alpha channel        {enabled_label(draft.include_alpha)}")
    print(f"  7  Beta channel         {enabled_label(draft.include_beta)}")
    print(f"  8  Latest channel       {enabled_label(draft.include_latest)}")
    print(f"  9  Multi-platform       {platform_menu_label(draft)}")
    print(f" 10  Docker cache         {cache_menu_label(draft)}")
    print(f" 11  Docker login         {enabled_label(draft.docker_login)}")
    print(f" 12  Pull verification    {enabled_label(draft.pull_after_publish) if draft.full_build else 'n/a in tag-only mode'}")
    print(f" 13  Dry run              {enabled_label(draft.dry_run)}")
    print(f" 14  App image only       {enabled_label(draft.app_only)}")
    print(f" 15  Release record dir   {draft.release_record_dir or '(private checkout default)'}")
    print()
    print("  S  Start release")
    print("  Q  Quit")
    print()


def handle_menu_choice(draft: SettingsDraft, choice: str) -> str:
    if choice == "1":
        draft.full_build = not draft.full_build
        if draft.full_build:
            draft.pull_after_publish = DEFAULT_PULL_AFTER_PUBLISH
        else:
            draft.no_cache = False
            draft.pull_after_publish = False
        return f"Operation set to {operation_label(draft)}."
    if choice == "2":
        draft.app_version_tag = prompt_text("App version tag", draft.app_version_tag)
        return f"App version tag set to {draft.app_version_tag}."
    if choice == "3":
        draft.map_version_tag = prompt_text("Map image version tag", draft.map_version_tag)
        return f"Map version tag set to {draft.map_version_tag}."
    if choice == "4":
        draft.location_search_version_tag = prompt_text("Location search image version tag", draft.location_search_version_tag)
        return f"Location search version tag set to {draft.location_search_version_tag}."
    if choice == "5":
        draft.brouter_version_tag = prompt_text("BRouter image version tag", draft.brouter_version_tag)
        return f"BRouter version tag set to {draft.brouter_version_tag}."
    if choice == "6":
        draft.include_alpha = not draft.include_alpha
        return f"Alpha channel {enabled_label(draft.include_alpha)}."
    if choice == "7":
        draft.include_beta = not draft.include_beta
        return f"Beta channel {enabled_label(draft.include_beta)}."
    if choice == "8":
        draft.include_latest = not draft.include_latest
        return f"Latest channel {enabled_label(draft.include_latest)}."
    if choice == "9":
        if draft.full_build:
            draft.multi_platform = not draft.multi_platform
            return f"Multi-platform builds {enabled_label(draft.multi_platform)}."
        else:
            return "Multi-platform builds are only used in full-build mode."
    if choice == "10":
        if draft.full_build:
            draft.no_cache = not draft.no_cache
            return "Docker cache disabled." if draft.no_cache else "Docker cache enabled."
        else:
            return "Docker cache is only used in full-build mode."
    if choice == "11":
        draft.docker_login = not draft.docker_login
        return f"Docker login {enabled_label(draft.docker_login)}."
    if choice == "12":
        if draft.full_build:
            draft.pull_after_publish = not draft.pull_after_publish
            return f"Pull verification {enabled_label(draft.pull_after_publish)}."
        else:
            return "Pull verification is only used in full-build mode."
    if choice == "13":
        draft.dry_run = not draft.dry_run
        return f"Dry run {enabled_label(draft.dry_run)}."
    if choice == "14":
        draft.app_only = not draft.app_only
        return f"App image only {enabled_label(draft.app_only)}."
    if choice == "15":
        current = draft.release_record_dir or private_repository(SCRIPT_DIR) / RELEASE_RECORD_SUBDIRECTORY
        draft.release_record_dir = Path(prompt_text("Release record directory", str(current)))
        return f"Release record directory set to {draft.release_record_dir}."

    return "Unknown option."


def operation_label(draft: SettingsDraft) -> str:
    return "full build + push" if draft.full_build else "tag existing version images only"


def platform_menu_label(draft: SettingsDraft) -> str:
    if not draft.full_build:
        return "n/a in tag-only mode"
    return f"{enabled_label(draft.multi_platform)} ({draft.platforms})"


def cache_menu_label(draft: SettingsDraft) -> str:
    if not draft.full_build:
        return "n/a in tag-only mode"
    return "disabled" if draft.no_cache else "enabled"


def enabled_label(enabled: bool) -> str:
    return "enabled" if enabled else "disabled"


def print_user_variables(app_version_tag: str, map_version_tag: str, location_search_version_tag: str, brouter_version_tag: str) -> None:
    section("Current User Variables")
    print(f"Docker user:       {DOCKER_USERNAME}")
    print(f"App image:         {APP_IMAGE_NAME}:{app_version_tag}")
    print(f"Map image:         {MAP_IMAGE_NAME}:{map_version_tag}")
    print(f"Location search:   {LOCATION_SEARCH_IMAGE_NAME}:{location_search_version_tag}")
    print(f"BRouter image:     {BROUTER_IMAGE_NAME}:{brouter_version_tag}")
    print(f"Default channel:   {ALPHA_TAG}")
    print(f"Optional channels: {BETA_TAG}, {LATEST_TAG}")


def image_configs(settings: ReleaseSettings) -> list[ImageConfig]:
    images = [
        ImageConfig("App", APP_IMAGE_NAME, settings.app_version_tag, SCRIPT_DIR),
        ImageConfig("Location Search", LOCATION_SEARCH_IMAGE_NAME, settings.location_search_version_tag, SCRIPT_DIR, SCRIPT_DIR / "docker-location-search" / "Dockerfile"),
        ImageConfig("Map", MAP_IMAGE_NAME, settings.map_version_tag, SCRIPT_DIR / "docker-maps"),
        ImageConfig("BRouter", BROUTER_IMAGE_NAME, settings.brouter_version_tag, SCRIPT_DIR / "docker-brouter"),
    ]
    return images[:1] if settings.app_only else images


def refs_for_image(settings: ReleaseSettings, image: ImageConfig) -> list[str]:
    tags = unique(tag for tag in [image.version_tag, *settings.channel_tags] if tag)
    return [remote_ref(settings.docker_username, image.image_name, tag) for tag in tags]


def all_remote_refs(settings: ReleaseSettings) -> list[str]:
    refs: list[str] = []
    for image in image_configs(settings):
        refs.extend(refs_for_image(settings, image))
    return unique(refs)


def print_plan(settings: ReleaseSettings) -> None:
    section("Release Plan")
    operation = "full build + push" if settings.full_build else "tag existing version images only"
    cache = "disabled" if settings.no_cache else "enabled" if settings.full_build else "n/a"
    platforms = settings.platforms if settings.full_build else "n/a"
    channels = ", ".join(settings.channel_tags) if settings.channel_tags else "(none)"
    docker_login = docker_login_plan_label(settings)
    print(f"Operation:         {operation}")
    print(f"Version tags:      app={settings.app_version_tag}, maps={settings.map_version_tag}, location-search={settings.location_search_version_tag}, brouter={settings.brouter_version_tag}")
    print(f"Channel tags:      {channels}")
    print(f"Platforms:         {platforms}")
    print(f"Docker cache:      {cache}")
    print(f"Docker login:      {docker_login}")
    print(f"Dry run:           {'yes' if settings.dry_run else 'no'}")
    if settings.full_build:
        print(f"Release records:   {settings.release_record_dir}")

    print()
    print("Images:")
    for image in image_configs(settings):
        print(f"  - {settings.docker_username}/{image.image_name}:{describe_tags(image.version_tag, settings.channel_tags)}")


def docker_login_plan_label(settings: ReleaseSettings) -> str:
    if not settings.docker_login:
        return "no"
    if settings.force_docker_login:
        return "yes (forced)"
    return "yes (skip when existing Docker Hub credentials are present)"


def print_docker_login_recovery_hint(username: str) -> None:
    warn("Docker login failed. On macOS this is often a stale Docker Hub entry in the keychain.")
    warn("To clear only the Docker Hub helper entries, run:")
    print()
    print("  docker logout")
    print(f"  for server in {shlex.quote(DOCKER_HUB_REGISTRY_URL)} {' '.join(shlex.quote(url) for url in DOCKER_HUB_TOKEN_URLS)}; do")
    print("    printf '%s' \"$server\" | docker-credential-osxkeychain erase || true")
    print("  done")
    print(f"  docker login -u {shlex.quote(username)}")


def docker_context_name() -> str:
    try:
        result = subprocess.run(["docker", "context", "show"], text=True, capture_output=True)
    except OSError:
        return ""
    return result.stdout.strip() if result.returncode == 0 else ""


def print_docker_daemon_recovery_hint() -> None:
    context_name = docker_context_name()
    if context_name:
        warn(f"Docker daemon is not reachable for the active context: {context_name}")
    else:
        warn("Docker daemon is not reachable.")

    if context_name == "orbstack":
        warn("Start OrbStack, then rerun the release helper.")
    else:
        warn("Start Docker Desktop or your Docker daemon, or switch to a working context with `docker context use`.")


class DockerReleaseRunner:
    def __init__(self, settings: ReleaseSettings) -> None:
        self.settings = settings
        self.results: list[StepResult] = []
        self.image_build_time = utc_timestamp()
        self.app_source: dict[str, str] = {}
        self.app_release: dict[str, object] = {}
        self.release_records: list[Path] = []

    def run(self) -> None:
        if self.settings.full_build:
            self.run_full_build()
        else:
            self.run_tag_only()
        self.print_summary()

    def run_full_build(self) -> None:
        section("Full Build")
        self.app_source = app_source_identity(dry_run=self.settings.dry_run)
        info(f"App source revision: {self.app_source['revision']}")
        info(f"App source tree:     {self.app_source['tree']}")
        self.prepare_release_record_directory()
        self.ensure_docker_daemon()

        if self.settings.docker_login:
            self.run_docker_login()
        else:
            self.results.append(StepResult("Docker login", "SKIP", "--no-login"))

        self.ensure_app_version_unused()
        for image in image_configs(self.settings)[1:]:
            self.ensure_image_version_unused(image)
        self.remove_previous_local_images()
        self.ensure_buildx_builder()
        self.build_app_image()
        if not self.settings.app_only:
            self.build_location_search_image()
            self.build_map_image()
            self.build_brouter_image()

        self.write_bundle_record()
        if self.settings.pull_after_publish:
            self.pull_published_refs()
        else:
            self.results.append(StepResult("Pull published refs", "SKIP", "--no-pull-verify"))

    def run_tag_only(self) -> None:
        section("Tag Existing Version Images")
        if self.settings.docker_login:
            self.run_docker_login()
        else:
            self.results.append(StepResult("Docker login", "SKIP", "--no-login"))

        for channel in self.settings.channel_tags:
            for image in image_configs(self.settings):
                source = remote_ref(self.settings.docker_username, image.image_name, image.version_tag)
                target = remote_ref(self.settings.docker_username, image.image_name, channel)
                self.run_step(
                    f"Publish {image.label} :{channel}",
                    ["docker", "buildx", "imagetools", "create", "-t", target, source],
                )
                self.run_step(
                    f"Verify {image.label} :{channel}",
                    ["docker", "buildx", "imagetools", "inspect", target],
                    capture=True,
                )

    def run_docker_login(self) -> None:
        if not self.settings.force_docker_login and docker_hub_credentials_available():
            ok("Existing Docker Hub credentials found; skipping docker login.")
            self.results.append(StepResult("Docker login", "SKIP", "existing Docker Hub credentials"))
            return

        result = self.run_step(
            "Docker login",
            ["docker", "login", "-u", self.settings.docker_username],
            allow_failure=True,
        )
        if result is not None and result.returncode != 0:
            print()
            print_docker_login_recovery_hint(self.settings.docker_username)
            self.print_summary()
            raise SystemExit(result.returncode)

    def ensure_docker_daemon(self) -> None:
        result = self.run_step(
            "Docker daemon",
            ["docker", "info", "--format", "{{.ServerVersion}}"],
            capture=True,
            allow_failure=True,
        )
        if result is not None and result.returncode != 0:
            print()
            print_docker_daemon_recovery_hint()
            self.print_summary()
            raise SystemExit(result.returncode)

    def run_step(
        self,
        label: str,
        cmd: Sequence[object],
        *,
        cwd: Path = SCRIPT_DIR,
        capture: bool = False,
        allow_failure: bool = False,
        record: bool = True,
    ) -> subprocess.CompletedProcess[str] | None:
        print()
        print(color(BOLD, label))
        info(f"$ {shlex_join(cmd)}")

        if self.settings.dry_run:
            if record:
                self.results.append(StepResult(label, "OK", "dry-run"))
            return None

        result = subprocess.run(
            [str(part) for part in cmd],
            cwd=str(cwd),
            text=True,
            capture_output=capture,
        )

        if capture and result.stdout.strip():
            info(result.stdout.strip())
        if capture and result.stderr.strip():
            stream = warn if result.returncode == 0 else error
            stream(result.stderr.strip())

        status = "OK" if result.returncode == 0 else "FAIL"
        if record:
            detail = "" if result.returncode == 0 else f"exit {result.returncode}"
            self.results.append(StepResult(label, status, detail))

        if result.returncode != 0 and not allow_failure:
            self.print_summary()
            raise SystemExit(result.returncode)

        return result

    def remove_previous_local_images(self) -> None:
        section("Local Image Cleanup")
        refs: list[str] = []
        for image in image_configs(self.settings):
            refs.append(f"{image.image_name}:{image.version_tag}")
            refs.extend(refs_for_image(self.settings, image))

        for ref in unique(refs):
            self.run_step(
                f"Remove local image {ref}",
                ["docker", "rmi", ref],
                capture=True,
                allow_failure=True,
                record=False,
            )
        self.results.append(StepResult("Local image cleanup", "OK", "missing images ignored"))

    def ensure_buildx_builder(self) -> None:
        section("Docker Buildx")
        if self.settings.dry_run:
            self.run_step("Inspect buildx builders", ["docker", "buildx", "ls"])
            self.run_step("Use buildx builder", ["docker", "buildx", "use", BUILDX_BUILDER_NAME])
            return

        result = self.run_step(
            "Inspect buildx builders",
            ["docker", "buildx", "ls"],
            capture=True,
        )
        builder_exists = bool(result and BUILDX_BUILDER_NAME in result.stdout)
        if not builder_exists:
            self.run_step(
                "Create buildx builder",
                ["docker", "buildx", "create", "--name", BUILDX_BUILDER_NAME, "--driver", "docker-container", "--bootstrap"],
            )
        else:
            ok(f"Buildx builder '{BUILDX_BUILDER_NAME}' already exists.")
            self.results.append(StepResult("Create buildx builder", "SKIP", "already exists"))

        self.run_step("Use buildx builder", ["docker", "buildx", "use", BUILDX_BUILDER_NAME])

    def build_app_image(self) -> None:
        image = image_configs(self.settings)[0]
        label = f"Build+push {image.image_name}:{describe_tags(image.version_tag, self.settings.channel_tags)}"
        if self.settings.dry_run:
            snapshot_image = ImageConfig(image.label, image.image_name, image.version_tag,
                                         Path("<committed-source-snapshot>"))
            self.run_step(label, self.buildx_command(snapshot_image))
            return
        with app_source_snapshot(self.app_source["revision"]) as source:
            snapshot_image = ImageConfig(image.label, image.image_name, image.version_tag, source)
            metadata_path = source.parent / "build-metadata.json"
            cmd = self.buildx_command(snapshot_image)
            cmd[-1:-1] = ["--metadata-file", metadata_path]
            self.run_step(label, cmd)
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            digest = metadata["containerimage.digest"]
            self.app_release = {
                "buildId": image.version_tag,
                "image": remote_ref(self.settings.docker_username, image.image_name, image.version_tag),
                "digest": digest,
                "revision": self.app_source["revision"],
                "tree": self.app_source["tree"],
                "build_time": self.image_build_time,
                "platforms": self.settings.platforms.split(","),
                "tags": refs_for_image(self.settings, image),
            }
            record = self.write_release_record(f"{image.image_name}-{image.version_tag}.json", self.app_release)
            ok(f"App release record: {record}")
            for ref in refs_for_image(self.settings, image):
                result = self.run_step(f"Verify app digest {ref}",
                                       ["docker", "buildx", "imagetools", "inspect", ref,
                                        "--format", "{{.Manifest.Digest}}"], capture=True)
                if result is None or result.stdout.strip() != digest:
                    raise SystemExit(f"Published digest for {ref} does not match the build record.")

    def write_bundle_record(self) -> None:
        if self.settings.dry_run or not self.app_release:
            return
        components = {}
        component_ids = {APP_IMAGE_NAME: "app", LOCATION_SEARCH_IMAGE_NAME: "location-search",
                         MAP_IMAGE_NAME: "map-server", BROUTER_IMAGE_NAME: "brouter"}
        images = image_configs(self.settings)[:1] if self.settings.app_only else image_configs(self.settings)
        for image in images:
            ref = remote_ref(self.settings.docker_username, image.image_name, image.version_tag)
            result = self.run_step(f"Record component digest {ref}",
                                   ["docker", "buildx", "imagetools", "inspect", ref,
                                    "--format", "{{.Manifest.Digest}}"], capture=True)
            digest = result.stdout.strip() if result else ""
            if not re.fullmatch(r"sha256:[a-f0-9]{64}", digest):
                raise SystemExit(f"Invalid published digest for {ref}; bundle record stopped.")
            if image.image_name == APP_IMAGE_NAME and digest != self.app_release["digest"]:
                raise SystemExit("Application digest changed before recording release bundle.")
            components[component_ids[image.image_name]] = {"buildId": image.version_tag, "image": ref, "digest": digest}
        record = self.write_release_record(f"mtl-bundle-{components['app']['buildId']}.json", {"components": components})
        ok(f"Component release record: {record}")

    def prepare_release_record_directory(self) -> None:
        if self.settings.dry_run:
            return
        directory = self.settings.release_record_dir
        if directory is None:
            raise SystemExit("A release record directory is required for full builds.")
        try:
            directory.mkdir(parents=True, exist_ok=True)
            for filename in (f"{APP_IMAGE_NAME}-{self.settings.app_version_tag}.json",
                             f"mtl-bundle-{self.settings.app_version_tag}.json"):
                record = directory / filename
                if record.exists():
                    raise SystemExit(f"Release record already exists: {record}. Choose a new build tag or record directory.")
            with tempfile.TemporaryFile(dir=directory):
                pass
        except OSError as exc:
            raise SystemExit(f"Cannot write release records to {directory}: {exc}") from exc

    def write_release_record(self, filename: str, contents: dict[str, object]) -> Path:
        directory = self.settings.release_record_dir
        if directory is None:
            raise SystemExit("A release record directory is required for full builds.")
        directory.mkdir(parents=True, exist_ok=True)
        record = directory / filename
        with record.open("x", encoding="utf-8") as output:
            output.write(json.dumps(contents, indent=2) + "\n")
        self.release_records.append(record)
        return record

    def ensure_app_version_unused(self) -> None:
        self.ensure_image_version_unused(image_configs(self.settings)[0])

    def ensure_image_version_unused(self, image: ImageConfig) -> None:
        ref = remote_ref(self.settings.docker_username, image.image_name, image.version_tag)
        result = self.run_step(f"Check {image.label} version is unused",
                               ["docker", "buildx", "imagetools", "inspect", ref],
                               capture=True, allow_failure=True, record=False)
        if result is None:
            return
        if result.returncode == 0:
            raise SystemExit(f"Image version {ref} already exists; choose a new version tag.")
        message = result.stderr.lower()
        if not any(reason in message for reason in (f"{ref}: not found", "manifest unknown")):
            raise SystemExit(f"Could not verify that {ref} is unused; release stopped.")

    def build_map_image(self) -> None:
        image = image_configs(self.settings)[2]
        label = f"Build+push {image.image_name}:{describe_tags(image.version_tag, self.settings.channel_tags)}"
        self.run_step(label, self.buildx_command(image))

    def build_brouter_image(self) -> None:
        image = image_configs(self.settings)[3]
        label = f"Build+push {image.image_name}:{describe_tags(image.version_tag, self.settings.channel_tags)}"
        self.run_step(label, self.buildx_command(image))

    def build_location_search_image(self) -> None:
        image = image_configs(self.settings)[1]
        label = f"Build+push {image.image_name}:{describe_tags(image.version_tag, self.settings.channel_tags)}"
        self.run_step(label, self.buildx_command(image, {"REQUIRE_GEONAMES_DB": "true"}))

    def buildx_command(self, image: ImageConfig, build_args: dict[str, str] | None = None) -> list[object]:
        # Keep every image on the release builder if the shared selection changes.
        cmd: list[object] = ["docker", "buildx", "build", "--builder", BUILDX_BUILDER_NAME]
        if self.settings.no_cache:
            cmd.append("--no-cache")
        if image.dockerfile is not None:
            cmd.extend(["-f", image.dockerfile])
        effective_build_args = {
            "MTL_IMAGE_VERSION": image.version_tag or "",
            "MTL_IMAGE_BUILD_TIME": self.image_build_time,
            **(build_args or {}),
        }
        if image.image_name == APP_IMAGE_NAME:
            effective_build_args["MTL_IMAGE_REVISION"] = self.app_source["revision"]
        for key, value in effective_build_args.items():
            cmd.extend(["--build-arg", f"{key}={value}"])
        if image.image_name == APP_IMAGE_NAME:
            labels = {
                "org.opencontainers.image.version": image.version_tag,
                "org.opencontainers.image.created": self.image_build_time,
                "org.opencontainers.image.revision": self.app_source["revision"],
                SOURCE_TREE_LABEL: self.app_source["tree"],
            }
            for key, value in labels.items():
                cmd.extend(["--label", f"{key}={value}", "--annotation", f"index,manifest:{key}={value}"])
            cmd.append("--provenance=mode=min")
        cmd.extend(["--platform", self.settings.platforms])
        for ref in refs_for_image(self.settings, image):
            cmd.extend(["-t", ref])
        cmd.append("--push")
        cmd.append(image.context_dir)
        return cmd

    def pull_published_refs(self) -> None:
        section("Published Image Verification")
        failed = False
        for ref in all_remote_refs(self.settings):
            result = self.run_step(
                f"Pull {ref}",
                ["docker", "pull", ref],
                allow_failure=True,
            )
            if result is not None and result.returncode != 0:
                failed = True

        if failed:
            warn("One or more published refs could not be pulled. Review the step summary.")

    def collect_image_info(self) -> dict[str, str]:
        if self.settings.dry_run:
            return {}

        image_info: dict[str, str] = {}
        for ref in all_remote_refs(self.settings):
            result = subprocess.run(
                ["docker", "image", "ls", "--format", "{{.ID}}|{{.Size}}", ref],
                cwd=str(SCRIPT_DIR),
                text=True,
                capture_output=True,
            )
            first_line = result.stdout.strip().splitlines()[0] if result.stdout.strip() else ""
            if not first_line:
                image_info[ref] = "not found in local daemon"
                continue
            image_id, _, size = first_line.partition("|")
            image_info[ref] = f"ID {image_id} / Size {size}"
        return image_info

    def print_summary(self) -> None:
        section("Release Summary")
        channels = ", ".join(self.settings.channel_tags) if self.settings.channel_tags else "(none)"
        operation = "full build + push" if self.settings.full_build else "tag existing version images only"
        platforms = self.settings.platforms if self.settings.full_build else "n/a"
        print(f"Operation:       {operation}")
        print(f"Channels:        {channels}")
        print(f"Platforms:       {platforms}")
        if self.app_release:
            print(f"App revision:    {self.app_release['revision']}")
            print(f"App digest:      {self.app_release['digest']}")
        if self.release_records:
            print()
            print("Release records (commit these in the private repository):")
            for record in self.release_records:
                print(f"  - {record}")

        print()
        print("Steps:")
        overall_ok = True
        for result in self.results:
            marker = f"[{result.status}]"
            if result.status == "FAIL":
                overall_ok = False
            detail = f" ({result.detail})" if result.detail else ""
            print(f"  {marker} {result.label}{detail}")

        if self.settings.full_build and self.published_pull_started():
            image_info = self.collect_image_info()
            if image_info:
                print()
                print("Images:")
                for ref, detail in image_info.items():
                    print(f"  - {ref}: {detail}")

        print()
        if overall_ok:
            ok("All requested steps completed successfully.")
        else:
            error("One or more steps failed. Review the output above.")

    def published_pull_started(self) -> bool:
        return any(result.label.startswith("Pull ") for result in self.results)


def main() -> None:
    args = parse_args()
    settings = build_settings(args)
    runner = DockerReleaseRunner(settings)
    runner.run()


if __name__ == "__main__":
    main()
