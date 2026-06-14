---
title: "Tenant ID hardening: validate length and pattern and handle violations"
labels: ["enhancement", "area(http)"]
---
Summary (EN):
This issue proposes improvements to tenant identifier validation for HTTP requests. The branch `feat/16-tenant-id-hardening` introduced a `TenantIdValidator` and changes to the request filter, but it requires a few clarifications and refinements before merging.

Proposed changes:
- Return HTTP 400 (Bad Request) for syntactically invalid tenant identifiers by default (configurable); keep 401 for authentication failures.
- Cache the compiled Pattern in `TenantIdValidator` (compile once at startup/construction).
- Add/adjust tests for boundary conditions and for disabling validation via config.
- Update documentation and CHANGELOG with new configuration keys, defaults and examples.

Acceptance criteria:
- Unit and integration tests covering validator behavior are added/updated and pass.
- Documentation updated with configuration keys, defaults and examples.

Suggested assignees: @lu1tr0n

Required changes (detailed)
--------------------------------
- quarkus-multitenancy-http-runtime/src/main/java/io/quarkiverse/multitenancy/http/runtime/validation/TenantIdValidator.java
  - Ensure the compiled Pattern is cached (compile once during construction/startup).
  - Expose a `validate(String tenantId)` method that returns a clear enum/result (VALID, INVALID, DISABLED).

- quarkus-multitenancy-http-runtime/src/main/java/io/quarkiverse/multitenancy/http/runtime/config/HttpTenantConfig.java
  - Add properties:
	- `Integer tenantIdMaxLength` (default 64)
	- `String tenantIdPattern` (default `[A-Za-z0-9_-]+`)
	- `Integer tenantIdRejectStatus` (default 400)
	- `Boolean tenantIdValidationEnabled` (default true)

- quarkus-multitenancy-http-runtime/src/main/java/io/quarkiverse/multitenancy/http/runtime/filter/TenantFilter.java
  - Use the `TenantIdValidator` and `HttpTenantConfig` to validate the resolved id before publishing to `TenantContext`.
  - If invalid, return configured `tenantIdRejectStatus` (default 400) and log a WARN with minimal identifying information.
  - Ensure authentication failures remain 401 where appropriate and do not get masked by validation logic.

- quarkus-multitenancy-http-runtime/src/test/java/... (tests)
  - Update/add tests:
	- `TenantIdValidationTest` – valid/invalid characters and max length boundaries.
	- `TenantIdValidationDisabledTest` – ensure validator is bypassed when disabled.
	- `TenantIdValidatorSanitizeTest` – confirm sanitization/normalization if applied.
	- Integration test to assert `TenantContext` is not published for invalid ids.

- docs/modules/ROOT/pages/index.adoc
  - Add configuration section documenting new properties and examples (how to set pattern, max length, and alter reject status).

- quarkus-multitenancy-http-runtime/src/main/resources/META-INF/quarkus-extension.yaml
  - Document and expose the new configuration properties with defaults and descriptions.

- CHANGELOG.md
  - Expand the Unreleased entry with details and recommended migration/configuration steps and default values.

Notes/UX decisions to confirm
--------------------------------
- Default error code: 400 (Bad Request) is recommended for syntactic validation failures; keep 401 for authentication failures. This should be configurable via `tenantIdRejectStatus`.
- Decide whether tenant id sanitization (trimming, lower-casing) is desired — if yes, add a config flag and clear tests.
- Logging should avoid printing full tenant ids in WARN logs; consider hashing or redacting.

If you want, I can now open this issue on GitHub. To do that I need either:
1) A GitHub Personal Access Token with `repo` scope (set in env var GITHUB_TOKEN) so I can run the API call for you; or
2) You can run the command locally (I provide the exact `curl` or `gh issue create` command).

Tell me which option you prefer and, if you want me to create the issue, provide the token or allow me to run the API call.

