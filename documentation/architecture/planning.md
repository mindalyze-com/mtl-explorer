# Route planning

The planner keeps an editable client draft with waypoints, profile, route legs, and local points of interest. Request generations and cancellation prevent stale routing responses from replacing newer edits.

The server calls BRouter and returns route geometry and statistics. Saved plans retain their payload in the existing planner storage. They remain separate from imported GPS activities and their statistics.

Saving waits for current geometry and retains user input on failure. Reopening a plan restores its route and places. GPX export contains route geometry; POI-only plans have no route export.

Public trail and place overlays are visual references. They do not silently supply route waypoints or visiting-order optimization. POI optimization research remains internal.

See [planner source](../../mtl-client/src/planner), [BRouter service](../../docker-brouter/README.md), and [the user procedure](../user/planning.md).

Points of interest use unique UUID identifiers. Persistence validates coordinates,
name length, notes, and supported icons; the client supplies the identifiers.
