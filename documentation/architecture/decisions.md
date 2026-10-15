# Accepted architecture decisions

These decisions describe current ownership. Detailed contracts are linked below.

| Decision | Reason and current reference |
| --- | --- |
| Domain calculations run on the server | Shared canonical inputs and results; [segment contract](analysis/segments.md) |
| OpenAPI owns API types | Server export and generated client; [API workflow](../development/api-generation.md) |
| Canonical metric units, converted presentation | Domain meaning stays server-owned; [unit contract](units.md) |
| Source observations and processed geometry stay distinct | Preserve evidence and explicit support; [GPS pipeline](gps/processing.md) |
| Display simplification does not own totals | Maps and reduced charts consume canonical results |
| User corrections preserve media source evidence | Separate precedence and correlation; [media contract](media/correlation.md) |
| Google Health uses installation-owned authorization | Local mirror and identity control; [OAuth contract](health/authorization.md) |

Pending designs belong in internal proposals. After implementation, update the current contract and record the accepted decision here. Split this index into topic records when the number warrants it.
