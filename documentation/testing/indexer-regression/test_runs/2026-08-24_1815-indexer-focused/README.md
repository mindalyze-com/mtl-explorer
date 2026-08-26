# MTL Explorer server upgrade regression

- Date: 2026-08-24
- Branch: `feature/maplibre-upgrade-version-6`
- Baseline commit: `592d98136b82244e8c4eb41d0d1f720739adc767`
- Result: PASS
- Scope: `mtl-server`, its Maven wrapper, and the server stages of the root Dockerfile
- Fixture source: repository synthetic GPX/photo generator; no personal data

## Version decisions

| Component | Before | Evaluated target | Decision |
| --- | --- | --- | --- |
| Java application build/runtime | 21 | 25.0.4 LTS | Updated |
| Maven wrapper | 3.8.7 / wrapper 3.1.1 | 3.9.16 / wrapper 3.3.4 | Updated |
| Spring Boot BOM/plugin | 3.5.14 | 4.1.1 | Updated; source and tests migrated to Boot 4 APIs |
| Spring MVC starter | `spring-boot-starter-web` | `spring-boot-starter-webmvc` | Updated to the Boot 4 module |
| Hibernate ORM/Spatial | Boot-managed 6.x | 7.4.6.Final | Updated together |
| Caffeine | Boot-managed | 3.2.4 | Updated |
| springdoc OpenAPI | 2.8.17 | 3.1.0 | Updated |
| Hibernate Validator | 8.0.3.Final | 9.1.3.Final | Updated |
| OGNL | 3.4.11 | 3.4.12 | Updated |
| PostgreSQL JDBC | Boot-managed | 42.7.13 | Updated |
| Lombok | Boot-managed | 1.18.46 | Updated |
| JPX | 3.2.1 | 4.0.0 | Updated |
| GeoTools modules | 33.5 | 35.1 | Updated as one set |
| jsoup | 1.22.2 | 1.23.1 | Updated |
| Commons Codec | 1.22.0 | 1.22.1 | Updated |
| Commons Collections | 4.5.0 | 4.6.0 | Updated |
| Commons Lang | 3.20.0 | 3.20.0 | Already current; unchanged |
| Commons Math | 3.6.1 | 3.6.1 | Already current; unchanged |
| Liquibase | Boot-managed 4.x | 5.0.4 | Updated with the Boot 4 Liquibase starter; license note updated |
| metadata-extractor | 2.20.0 | 2.21.0 | Updated |
| Reactor Core | Boot-managed | 3.8.7 | Updated |
| JJWT modules | 0.12.7 | 0.13.0 | Updated together |
| OkHttp | 4.12.0 `okhttp` | 5.5.0 `okhttp-jvm` | Updated; proxy client migrated to OkHttp 5 |
| JTS Jackson adapter | 2.21.0 | Removed | Replaced by a local Jackson 3 serializer |
| Maven Compiler | 3.15.0 | 3.15.0 | Already current; Java 25 `release` configured |
| Maven Surefire | 3.5.5 | 3.5.6 | Updated |
| Keytool Maven plugin | 1.7 | 2.0.2 | Updated |
| Maven lifecycle plugins | implicit defaults | clean 3.5.0, resources 3.5.0, jar 3.5.1, install/deploy 3.1.4 | Pinned to evaluated stable versions |

Spring Boot 4.1.1 manages the remaining Spring, Jackson, logging, servlet, test, and observability libraries as one tested platform. The resolved runtime graph was checked successfully. It includes Spring Framework 7.0.9, Spring Security 7.1.1, Jackson 3.1.5, Tomcat 11.0.24, and Logback 1.5.38. Jackson 2 remains only where required by springdoc/JJWT compatibility modules.

The GPSBabel builder remains on Java 21 and GPSBabel 1.10. Other Docker images and toolchains were outside this server-only change.

## Automated verification

| Check | Result |
| --- | --- |
| Full server suite on Java 25 with disposable PostGIS 18 | PASS: 516 tests, 0 failures, 0 errors, 2 intentional skips |
| Full client suite used by the GUI smoke test | PASS: 132 files, 766 tests |
| Production multi-stage Docker build | PASS: complete Maven reactor and image build |
| Built runtime | PASS: Temurin 25.0.4 LTS |
| Runtime dependency resolution | PASS |
| Live OpenAPI versus checked-in schema | PASS: OpenAPI 3.1.0, 70 paths, 115 schemas; exact after removing environment-specific `servers` |
| Application log audit | PASS: no `ERROR`, `Exception`, or `Caused by` entries |
| Browser console audit | PASS: no errors; four expected polling warnings during planned app stops |

Built image before cleanup: `mtl-server-upgrade-smoke:20260824`, `sha256:a964e0cf642038b3b743064a1e893bbb95dbfad77b9577858b52c4d3b804c100`.

## Pass 1: live watcher

The server remained running for mutations. On this Docker Desktop bind mount, additions were published atomically from inside the container so the mounted filesystem produced native watcher events. Deletions were made through the host mount. No manual rescan endpoint was used.

| Phase | Active GPS/media index rows | GPS/media domain rows | State check | GUI check |
| --- | ---: | ---: | --- | --- |
| Baseline B1/B2 | 2 / 2 | 2 / 2 | PASS | [Map](assets/LIVE-BASELINE-ui.webp) |
| Add A1/A2/A3 | 5 / 5 | 5 / 5 | PASS | [Map](assets/LIVE-ADD-ui.webp) |
| Delete A2 | 4 / 4 | 4 / 4 | PASS; A2 marked removed | [Map](assets/LIVE-DELETE-ui.webp) |
| Add N1 | 5 / 5 | 5 / 5 | PASS; one N1 row per type | [Map](assets/LIVE-NEW-ui.webp) |
| Restore A2 | 6 / 6 | 6 / 6 | PASS; no duplicate domain rows | [Map](assets/LIVE-RESTORE-ui.webp) |
| Replace B1 in place | 6 / 6 | 6 / 6 | PASS; title became `Live B1 replacement` | [Map](assets/LIVE-REPLACE-ui.webp) |
| Final statistics | 6 / 6 | 6 / 6 | PASS | [Stats](assets/LIVE-FINAL-stats.webp) |

All six GPX records parsed with six points and `SUCCESS` load status. The browser freshness banner was used to reload each changed state.

## Pass 2: startup catch-up

This pass used a new database and watch root. The server was stopped before every mutation and restarted afterward. No manual rescan endpoint was used.

| Phase | Active GPS/media index rows | GPS/media domain rows | State check | GUI check |
| --- | ---: | ---: | --- | --- |
| Baseline B1/B2 | 2 / 2 | 2 / 2 | PASS | [Map](assets/OFFLINE-BASELINE-ui.webp) |
| Add A1/A2/A3 while stopped | 5 / 5 | 5 / 5 | PASS | [Map](assets/OFFLINE-ADD-ui.webp) |
| Delete A2 while stopped | 4 / 4 | 4 / 4 | PASS; A2 marked removed | [Map](assets/OFFLINE-DELETE-ui.webp) |
| Add N1 while stopped | 5 / 5 | 5 / 5 | PASS; one N1 row per type | [Map](assets/OFFLINE-NEW-ui.webp) |
| Restore A2 while stopped | 6 / 6 | 6 / 6 | PASS; no duplicate domain rows | [Map](assets/OFFLINE-RESTORE-ui.webp) |
| Replace B1 while stopped | 6 / 6 | 6 / 6 | PASS; replacement title visible | [Stats](assets/OFFLINE-FINAL-stats.webp) |

The final GUI showed 6 tracks and 6 media. The freshness banner and normal in-app reload path were used after each restart.

## Runtime notes

The only application warnings were the existing Open Session in View default, the development password encoder, the missing optional Thymeleaf template directory, and enabled springdoc endpoints. No regression failure is attached to these warnings.

The checked-in OpenAPI schema was not changed because the full comparison passed.

## Cleanup

PASS. The three disposable databases, live/offline/schema application containers,
three test networks, built smoke image, browser tab, ports 18086-18088, and synthetic
temporary root were removed. The image and fixtures can be recreated from the Dockerfile
and repository generators.
