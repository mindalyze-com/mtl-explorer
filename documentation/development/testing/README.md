# Testing

Choose checks for the changed behavior. Keep reusable instructions here; dated outcomes belong in internal verification.

- [Unit and integration checks](unit-and-integration.md)
- [Google Health import simulation](google-health-simulation.md)
- [End-user and indexer regression](regression.md)
- [Indexer regression cases](indexer-regression.md)
- [Security checks](security.md)
- [Documentation checks](../website/documentation.md)

For repeatable end-user checks:

- [Full-regression workflow and coverage](full-regression/README.md)
- [GPS regression](gps-regression.md)
- [Media regression](media-regression.md) and [video playback](video-regression.md)
- [Quick installation](quick-install.md) and [container build](container-build.md)

Use public or fully synthetic data. Never derive fixtures from private GPX tracks. Java checks run in the existing development container. Preserve pre-existing failures separately from results for the change.
