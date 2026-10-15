# End-user regression

A full regression uses a frozen coverage plan, one packet per coverage ID, a resumable run state, and a final report. Keep completed outcomes and source snapshots as dated evidence.

Reusable workflow and tooling are linked from the [full-regression instructions](full-regression/workflow.md). The current [coverage plan](full-regression/coverage-plan.md) defines new runs; an existing run uses its own snapshot.

Follow the [stable test identifier standard](../frontend.md#stable-test-identifiers)
for browser checks. Normally add missing IDs in the application instead of XPath
or fragile CSS/positional selectors. Clear scoped roles and labels remain valid;
document necessary exceptions for installed releases or canvas interactions.

Focused indexer checks cover live filesystem changes and startup catch-up after offline changes. Use the [indexer regression cases](indexer-regression.md) and the repository's indexer skill for that workflow. Preserve working-function screenshots as well as failures.

New runs use public or synthetic files and temporary services with restart disabled. Connected personal-account Health checks require their existing local prerequisites and keep personal values out of evidence.
