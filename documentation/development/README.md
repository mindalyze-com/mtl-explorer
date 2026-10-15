# Development

Start with [the development setup](getting-started.md). Java builds and tests run inside the existing development container.

| Topic | Reference |
| --- | --- |
| Server | [Backend](backend.md), [GPS diagnostics module](gps-quality-modules.md), [profiling](profiling.md) |
| Client | [Frontend](frontend.md), [styling](styling.md) |
| API | [Schema export and client generation](api-generation.md) |
| Verification | [Testing](testing/README.md) |
| Plans | [Google Health synchronization plan](google-health-synchronization-plan.md), [ingest issue acknowledgement plan](ingest-issue-acknowledgement-plan.md) |
| Packaging | [Container builds](containers.md), [release workflow](releases.md) |
| Public site | [Website and documentation](website/README.md) |

Read [Architecture](../architecture/README.md) before changing domain ownership. Follow [repository instructions](../../AGENTS.md) and [contribution guidance](../project/contributing.md).

## Shared code rules

Use named constants for semantic, reused, or tunable values. Avoid production
magic numbers and strings. Reuse existing constants and keep new ones in the
smallest useful scope.

## Software versions

Verify the latest stable non-beta release before proposing or pinning software,
runtime, package, or base-image versions. Prefer the latest stable LTS release
unless compatibility requires an older version.
