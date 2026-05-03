#!/usr/bin/env python3
"""
Guided Docker build and deploy helper for MyTrailLog.

Default interactive flow:
  1. Show a start screen with every release option.
  2. Let you edit/toggle version tags, channels, build mode, and verification.
  3. Publish immutable version tags and the alpha channel by default.
  4. Publish beta/latest only when you enable them.
  5. Build multiple platforms by default when full-build mode is enabled.

Examples:
  ./docker-build-deploy.py
  ./docker-build-deploy.py --tag-only
  ./docker-build-deploy.py --yes --include-beta
  ./docker-build-deploy.py --dry-run --yes --include-latest
"""

from __future__ import annotations

import argparse
import os
import shlex
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


# =============================================================================
# USER VARIABLES - EDIT THESE FIRST
# =============================================================================

DOCKER_USERNAME = "wauwau0977"

# Version tags. These are the most important values to check before every run.
APP_IMAGE_NAME = "mytraillog"
APP_VERSION_TAG = "1.231"  # version tag, (v. 1.1 -> 2025-10 release)

MAP_IMAGE_NAME = "mytraillog-maps"
MAP_VERSION_TAG = "1.33"

BROUTER_IMAGE_NAME = "mytraillog-brouter"
BROUTER_VERSION_TAG = "1.2"

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
DEFAULT_RUN_MAVEN = True
DEFAULT_NO_CACHE = False
DEFAULT_DOCKER_LOGIN = True
DEFAULT_PULL_AFTER_PUBLISH = True

# Build settings.
MULTI_PLATFORM_PLATFORMS = "linux/amd64,linux/arm64"
SINGLE_PLATFORM = "linux/amd64"
BUILDX_BUILDER_NAME = "multi-builder"
MAVEN_COMMAND = ["mvn", "clean", "install", "-DskipTests=true"]
SERVER_JAR_PATH = Path("mtl-server/target/mtl-server-0.0.1-SNAPSHOT.jar")

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


@dataclass(frozen=True)
class ImageConfig:
    label: str
    image_name: str
    version_tag: str
    context_dir: Path


@dataclass(frozen=True)
class ReleaseSettings:
    docker_username: str
    app_version_tag: str
    map_version_tag: str
    brouter_version_tag: str
    include_alpha: bool
    include_beta: bool
    include_latest: bool
    full_build: bool
    multi_platform: bool
    run_maven: bool
    no_cache: bool
    docker_login: bool
    pull_after_publish: bool
    dry_run: bool

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
    brouter_version_tag: str
    include_alpha: bool
    include_beta: bool
    include_latest: bool
    full_build: bool
    multi_platform: bool
    run_maven: bool
    no_cache: bool
    docker_login: bool
    pull_after_publish: bool
    dry_run: bool

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


def describe_tags(version_tag: str, channel_tags: Sequence[str]) -> str:
    return " + ".join([version_tag, *channel_tags])


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Guided Docker build/deploy script for MyTrailLog.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )

    parser.add_argument("--yes", action="store_true", help="Use defaults and do not prompt.")
    parser.add_argument("--dry-run", action="store_true", help="Print the plan and commands without running them.")

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

    parser.add_argument("--skip-maven", action="store_true", help="Skip the Maven build in full-build mode.")
    parser.add_argument("--no-cache", action="store_true", help="Disable Docker build cache.")
    parser.add_argument("--no-login", action="store_true", help="Do not run docker login before publishing.")
    parser.add_argument("--no-pull-verify", action="store_true", help="Do not pull published refs after a full build.")

    parser.add_argument("--app-tag", default=None, help="Override the app version tag for this run.")
    parser.add_argument("--map-tag", default=None, help="Override the map image version tag for this run.")
    parser.add_argument("--brouter-tag", default=None, help="Override the BRouter image version tag for this run.")

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
        banner("MyTrailLog Docker Release")
        print_user_variables(draft.app_version_tag, draft.map_version_tag, draft.brouter_version_tag)
    else:
        configure_interactively(draft)

    settings = release_settings_from_draft(draft)

    if not settings.full_build and not settings.channel_tags:
        raise SystemExit("Tag-only mode needs at least one channel tag.")

    print_plan(settings)
    return settings


def initial_settings_draft(args: argparse.Namespace) -> SettingsDraft:
    full_build = args.full_build if args.full_build is not None else DEFAULT_FULL_BUILD
    multi_platform = args.multi_platform if args.multi_platform is not None else DEFAULT_MULTI_PLATFORM
    run_maven = DEFAULT_RUN_MAVEN and not args.skip_maven
    no_cache = DEFAULT_NO_CACHE or args.no_cache

    return SettingsDraft(
        app_version_tag=args.app_tag or APP_VERSION_TAG,
        map_version_tag=args.map_tag or MAP_VERSION_TAG,
        brouter_version_tag=args.brouter_tag or BROUTER_VERSION_TAG,
        include_alpha=args.include_alpha if args.include_alpha is not None else DEFAULT_INCLUDE_ALPHA,
        include_beta=args.include_beta if args.include_beta is not None else DEFAULT_INCLUDE_BETA,
        include_latest=args.include_latest if args.include_latest is not None else DEFAULT_INCLUDE_LATEST,
        full_build=full_build,
        multi_platform=multi_platform,
        run_maven=run_maven if full_build else False,
        no_cache=no_cache if full_build else False,
        docker_login=DEFAULT_DOCKER_LOGIN and not args.no_login,
        pull_after_publish=DEFAULT_PULL_AFTER_PUBLISH and not args.no_pull_verify if full_build else False,
        dry_run=args.dry_run,
    )


def release_settings_from_draft(draft: SettingsDraft) -> ReleaseSettings:
    return ReleaseSettings(
        docker_username=DOCKER_USERNAME,
        app_version_tag=draft.app_version_tag,
        map_version_tag=draft.map_version_tag,
        brouter_version_tag=draft.brouter_version_tag,
        include_alpha=draft.include_alpha,
        include_beta=draft.include_beta,
        include_latest=draft.include_latest,
        full_build=draft.full_build,
        multi_platform=draft.multi_platform,
        run_maven=draft.run_maven if draft.full_build else False,
        no_cache=draft.no_cache if draft.full_build else False,
        docker_login=draft.docker_login,
        pull_after_publish=draft.pull_after_publish if draft.full_build else False,
        dry_run=draft.dry_run,
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
    banner("MyTrailLog Docker Release")
    print("Edit the release plan before anything starts.")
    if notice:
        print(color(YELLOW, f"Status: {notice}"))
    print()
    print(f"  1  Operation            {operation_label(draft)}")
    print(f"  2  App version tag      {draft.app_version_tag}")
    print(f"  3  Map version tag      {draft.map_version_tag}")
    print(f"  4  BRouter version tag  {draft.brouter_version_tag}")
    print(f"  5  Alpha channel        {enabled_label(draft.include_alpha)}")
    print(f"  6  Beta channel         {enabled_label(draft.include_beta)}")
    print(f"  7  Latest channel       {enabled_label(draft.include_latest)}")
    print(f"  8  Multi-platform       {platform_menu_label(draft)}")
    print(f"  9  Maven build          {enabled_label(draft.run_maven) if draft.full_build else 'n/a in tag-only mode'}")
    print(f" 10  Docker cache         {cache_menu_label(draft)}")
    print(f" 11  Docker login         {enabled_label(draft.docker_login)}")
    print(f" 12  Pull verification    {enabled_label(draft.pull_after_publish) if draft.full_build else 'n/a in tag-only mode'}")
    print(f" 13  Dry run              {enabled_label(draft.dry_run)}")
    print()
    print("  S  Start release")
    print("  Q  Quit")
    print()


def handle_menu_choice(draft: SettingsDraft, choice: str) -> str:
    if choice == "1":
        draft.full_build = not draft.full_build
        if draft.full_build:
            draft.run_maven = DEFAULT_RUN_MAVEN
            draft.pull_after_publish = DEFAULT_PULL_AFTER_PUBLISH
        else:
            draft.run_maven = False
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
        draft.brouter_version_tag = prompt_text("BRouter image version tag", draft.brouter_version_tag)
        return f"BRouter version tag set to {draft.brouter_version_tag}."
    if choice == "5":
        draft.include_alpha = not draft.include_alpha
        return f"Alpha channel {enabled_label(draft.include_alpha)}."
    if choice == "6":
        draft.include_beta = not draft.include_beta
        return f"Beta channel {enabled_label(draft.include_beta)}."
    if choice == "7":
        draft.include_latest = not draft.include_latest
        return f"Latest channel {enabled_label(draft.include_latest)}."
    if choice == "8":
        if draft.full_build:
            draft.multi_platform = not draft.multi_platform
            return f"Multi-platform builds {enabled_label(draft.multi_platform)}."
        else:
            return "Multi-platform builds are only used in full-build mode."
    if choice == "9":
        if draft.full_build:
            draft.run_maven = not draft.run_maven
            return f"Maven build {enabled_label(draft.run_maven)}."
        else:
            return "Maven build is only used in full-build mode."
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


def print_user_variables(app_version_tag: str, map_version_tag: str, brouter_version_tag: str) -> None:
    section("Current User Variables")
    print(f"Docker user:       {DOCKER_USERNAME}")
    print(f"App image:         {APP_IMAGE_NAME}:{app_version_tag}")
    print(f"Map image:         {MAP_IMAGE_NAME}:{map_version_tag}")
    print(f"BRouter image:     {BROUTER_IMAGE_NAME}:{brouter_version_tag}")
    print(f"Default channel:   {ALPHA_TAG}")
    print(f"Optional channels: {BETA_TAG}, {LATEST_TAG}")


def image_configs(settings: ReleaseSettings) -> list[ImageConfig]:
    return [
        ImageConfig("App", APP_IMAGE_NAME, settings.app_version_tag, SCRIPT_DIR),
        ImageConfig("Map", MAP_IMAGE_NAME, settings.map_version_tag, SCRIPT_DIR / "docker-maps"),
        ImageConfig("BRouter", BROUTER_IMAGE_NAME, settings.brouter_version_tag, SCRIPT_DIR / "docker-brouter"),
    ]


def refs_for_image(settings: ReleaseSettings, image: ImageConfig) -> list[str]:
    tags = unique([image.version_tag, *settings.channel_tags])
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
    print(f"Operation:         {operation}")
    print(f"Version tags:      app={settings.app_version_tag}, maps={settings.map_version_tag}, brouter={settings.brouter_version_tag}")
    print(f"Channel tags:      {channels}")
    print(f"Platforms:         {platforms}")
    print(f"Maven build:       {'yes' if settings.run_maven else 'no'}")
    print(f"Docker cache:      {cache}")
    print(f"Docker login:      {'yes' if settings.docker_login else 'no'}")
    print(f"Dry run:           {'yes' if settings.dry_run else 'no'}")

    print()
    print("Images:")
    for image in image_configs(settings):
        print(f"  - {settings.docker_username}/{image.image_name}:{describe_tags(image.version_tag, settings.channel_tags)}")


class DockerReleaseRunner:
    def __init__(self, settings: ReleaseSettings) -> None:
        self.settings = settings
        self.results: list[StepResult] = []

    def run(self) -> None:
        if self.settings.full_build:
            self.run_full_build()
        else:
            self.run_tag_only()
        self.print_summary()

    def run_full_build(self) -> None:
        section("Full Build")
        if self.settings.run_maven:
            self.run_step("Maven build", MAVEN_COMMAND)
        else:
            self.results.append(StepResult("Maven build", "SKIP", "--skip-maven"))

        self.verify_server_jar()

        if self.settings.docker_login:
            self.run_step("Docker login", ["docker", "login"])
        else:
            self.results.append(StepResult("Docker login", "SKIP", "--no-login"))

        self.remove_previous_local_images()
        self.ensure_buildx_builder()
        self.build_app_image()

        self.build_map_image()
        self.build_brouter_image()

        if self.settings.pull_after_publish:
            self.pull_published_refs()
        else:
            self.results.append(StepResult("Pull published refs", "SKIP", "--no-pull-verify"))

    def run_tag_only(self) -> None:
        section("Tag Existing Version Images")
        if self.settings.docker_login:
            self.run_step("Docker login", ["docker", "login"])
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

    def verify_server_jar(self) -> None:
        label = "Server JAR check"
        jar_path = SCRIPT_DIR / SERVER_JAR_PATH
        if self.settings.dry_run:
            self.results.append(StepResult(label, "OK", "dry-run"))
            return

        if jar_path.is_file():
            ok(f"{label}: found {SERVER_JAR_PATH}")
            self.results.append(StepResult(label, "OK"))
            return

        self.results.append(StepResult(label, "FAIL", f"missing {SERVER_JAR_PATH}"))
        error(f"ERROR: {SERVER_JAR_PATH} not found.")
        error("Run Maven first, or do not use --skip-maven.")
        self.print_summary()
        raise SystemExit(1)

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
        self.run_step(label, self.buildx_command(image))

    def build_map_image(self) -> None:
        image = image_configs(self.settings)[1]
        label = f"Build+push {image.image_name}:{describe_tags(image.version_tag, self.settings.channel_tags)}"
        self.run_step(label, self.buildx_command(image))

    def build_brouter_image(self) -> None:
        image = image_configs(self.settings)[2]
        label = f"Build+push {image.image_name}:{describe_tags(image.version_tag, self.settings.channel_tags)}"
        self.run_step(label, self.buildx_command(image))

    def buildx_command(self, image: ImageConfig, build_args: dict[str, str] | None = None) -> list[object]:
        cmd: list[object] = ["docker", "buildx", "build"]
        if self.settings.no_cache:
            cmd.append("--no-cache")
        if build_args:
            for key, value in build_args.items():
                cmd.extend(["--build-arg", f"{key}={value}"])
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

        print()
        print("Steps:")
        overall_ok = True
        for result in self.results:
            marker = f"[{result.status}]"
            if result.status == "FAIL":
                overall_ok = False
            detail = f" ({result.detail})" if result.detail else ""
            print(f"  {marker} {result.label}{detail}")

        if self.settings.full_build:
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


def main() -> None:
    args = parse_args()
    settings = build_settings(args)
    runner = DockerReleaseRunner(settings)
    runner.run()


if __name__ == "__main__":
    main()
