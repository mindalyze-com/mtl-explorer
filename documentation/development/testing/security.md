# Security checks

Review the actual trust boundary affected by the change: authenticated APIs, source-file access, OAuth state and identity, token storage, downloads, or operator-owned upstream configuration.

Verify rejected access and malformed input without printing credentials or personal payloads. Account consent and callback rules belong to [the authorization contract](../../architecture/health/authorization.md).

Use [the security policy](../../../.github/SECURITY.md) for reporting. Keep private findings and reproduction details in internal verification until a public disclosure is explicitly authorized.

Dated security results are kept in internal verification; they do not certify a later build.
