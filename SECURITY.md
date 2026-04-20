# Security Policy

## Reporting a vulnerability

**Please do not open public GitHub issues for security vulnerabilities.**

Instead, report them privately by email:

- **hey.lueg@gmail.com**
- Subject: `[SECURITY] MyTrailLog – <short description>`

Please include, if possible:

- A description of the issue and its impact.
- Steps to reproduce (PoC welcome).
- Affected version / commit SHA.
- Your name / handle if you would like to be credited.

You can also use GitHub's
[Private Vulnerability Reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability)
on the repository once it is enabled.

## Response process

- Acknowledgement: within **7 days**.
- Initial assessment and severity: within **14 days**.
- Fix / mitigation / coordinated disclosure timeline: agreed with the
  reporter, typically 30–90 days depending on severity and complexity.

## Scope

In scope:

- `mtl-server` (Spring Boot backend, REST API, authentication, file upload,
  background jobs).
- `mtl-client` (Vue/TypeScript frontend, service worker, auth flows).
- The official Docker images built from this repository.
- The Docker-internal pipelines (`docker-brouter`, `docker-maps`,
  `docker/garmin_*`) when run with default configuration.

Out of scope:

- Third-party dependencies — please report those directly upstream.
- Self-hosted deployments with custom modifications.
- Social-engineering attacks, physical attacks, rate-limiting of public demo
  instances.

## Supported versions

Only the latest `main` branch and the most recent tagged release receive
security fixes. Older releases are best-effort.
