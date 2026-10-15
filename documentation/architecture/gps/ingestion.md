# GPS ingestion

The filesystem index owns source identity, detected changes, queued processing, and failures. Startup catch-up and live watching feed the same normal import path.

Converters normalize supported formats before GPX parsing. Import preserves original source observations separately from the denoised canonical stream. Recorded sections, missing measurements, and timing provenance remain explicit.

Canonical point metrics feed summaries and analysis. Simplified variants serve map display and reference canonical point indices. Supported `observed_path` geometry omits connecting recording-gap edges for spatial consumers.

Import and downstream completion are distinct: duplicate classification, activity classification, energy, exploration, and media correlation have their own work states. Major operations and failures are logged with context.

Diagnosis publishes [common source issues](../source-issues.md) for account
acknowledgement, including quality findings from successfully imported tracks.
Shared use settings and timing restrictions remain separate.

See [processing](processing.md), [quality ownership](quality.md), and [module boundaries](../../development/gps-quality-modules.md).
