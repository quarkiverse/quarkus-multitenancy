# Changelog

## Unreleased

## 0.3.0

### Fixed

- Support `quarkus.multi-tenant.http.strategy=none` to disable built-in HTTP strategies while retaining custom resolvers, tenant-id validation, and default-tenant fallback. `none` must be the sole strategy. (#95, PR #96)
- Restore Quarkus main snapshot compatibility and make PostgreSQL test-image pulls resilient with an ECR Public / Docker Hub fallback. (PRs #89, #91–#94)

### Changed

- Update the Quarkiverse parent from 22 to 26, the Quarkus baseline from `3.33.3.1` to `3.40.1`, and the test dependencies to the current Quarkus JUnit artifact names. Java 21 remains the compilation baseline.
- Expand the runnable demo's resolution and database-isolation coverage and add a dedicated Kafka tenant propagation guide. (PRs #80, #85)

### Added

- Programmatic tenant-connection demo using Quarkus' existing `TenantConnectionResolver`, an application-owned catalog, Agroal pools with Narayana transaction integration, Docker-free H2 routing tests, and real PostgreSQL Testcontainers coverage. The example documents the provider-cache and lifecycle boundary for the first phase of dynamic tenant onboarding. (#50)
- Named Hibernate ORM persistence-unit support: configure `quarkus.multi-tenant.orm.named-persistence-units` to attach the shared `TenantContext` adapter to one or more multitenant named persistence units. The default persistence unit remains integrated automatically, application-provided tenant resolvers override the built-in adapter cleanly, and invalid unit names or non-multitenant selections now fail the build with an actionable message. (#65)
- Deterministic `TenantResolver` ordering: custom resolvers can declare Jakarta `@Priority` (higher values run first), resolvers without an annotation use priority `0`, and equal priorities use the implementation class name as a stable tie-breaker. Ambiguous same-type/same-priority beans now fail startup with an actionable error. Built-in HTTP strategy order remains controlled by `quarkus.multi-tenant.http.strategy`. (#64)

## 0.2.0

See the [archived release notes](docs/releases/0.2.0.md) for the complete release, including background work, Kafka propagation, reactive context contracts, and unified HTTP/ORM resolution.

### Added

- Tenant identifier hardening: a resolved tenant id is now validated against a configurable maximum length (`quarkus.multi-tenant.http.tenant-id.max-length`, default `64`) and a character-set pattern (`quarkus.multi-tenant.http.tenant-id.pattern`, default `[A-Za-z0-9_-]+`) before it is published to the `TenantContext`. A violating identifier is rejected and never reaches downstream consumers (logs, SQL parameters, ORM tenant lookups). Rejected identifiers are sanitised — control characters stripped and length-capped — before being logged, closing a log-injection vector. (#16)
- Configurable reject status for tenant-id validation: `quarkus.multi-tenant.http.tenant-id.reject-status` (default `400`) sets the HTTP status used when a resolved tenant id fails the length or pattern policy. This is distinct from the `401` returned for an authentication-related rejection (`TenantResolution.Rejected`, e.g. an invalid bearer token), which is unaffected. Set it to `401` to keep a single status for both. (#29)
- Native-mode integration tests: a new `integration-tests` module exercises the HTTP tenant resolution chain end to end (header resolution, verified-JWT resolution, tenant-id validation, default-tenant fallback and the rejected-resolution path). The assertions run in the JVM as a `@QuarkusTest` and, in CI, against the native binary as a `@QuarkusIntegrationTest`, so native-image regressions in tenant resolution are caught before release and JVM/native behaviour is asserted to be identical. (#40)

### Changed

- **Behaviour change:** tenant-id validation is enabled by default. An application whose tenant identifiers contain characters outside `[A-Za-z0-9_-]` or exceed 64 characters will now receive HTTP 400 (configurable via `quarkus.multi-tenant.http.tenant-id.reject-status`) where `0.1.0` accepted the request. Widen `quarkus.multi-tenant.http.tenant-id.pattern` / `quarkus.multi-tenant.http.tenant-id.max-length`, or set `quarkus.multi-tenant.http.tenant-id.validation-enabled=false`, to restore the previous behaviour.
- **Behaviour change:** the default HTTP strategy chain is now `header,cookie` (was `header,jwt,cookie`). The `jwt` strategy is opt-in — applications that resolve tenants from a verified bearer token must set `quarkus.multi-tenant.http.strategy=header,jwt,cookie` (or `jwt`) explicitly. This completes the deprecation cycle begun in `0.1.0`, which logged a startup warning for applications relying on the implicit `jwt` default; that warning has been removed. (#15)

## 0.1.0

Initial Quarkiverse preview release.

### Added

- Core tenant resolution API and CDI context.
- Request-scoped `TenantContext` for resolved tenant propagation.
- HTTP tenant resolution strategies:
    - header
    - JWT claim (verified bearer)
    - cookie
    - path
- ORM integration module bridging `TenantContext` into Hibernate ORM multitenancy use cases.
- `@ConfigMapping`-based configuration on `HttpTenantConfig` with discoverable, documented keys.
- Quarkus extension descriptors (`quarkus-extension.yaml`, build-time processors, codestart metadata).
- Quarkiverse CI/CD canonical workflow set (`build`, `pre-release`, `release`, `perform-release`).
- Daily CI against the Quarkus snapshot via `quarkus-ecosystem-ci`.
- Initial Asciidoc documentation module published at `docs.quarkiverse.io/quarkus-multitenancy`.
- ORM module: `quarkus.multi-tenant.orm.header-filter.enabled` flag to opt out of the `X-Tenant` header filter (`OrmTenantHeaderFilter`), for applications that drive tenant resolution from `quarkus-multitenancy-http` instead of the ORM-side filter.
- Startup warning when the `jwt` strategy is active only through the implicit default chain (`quarkus.multi-tenant.http.strategy` unset), signalling the upcoming default-chain change (#15). The warning is informational and does not change behaviour.

### Changed

- `TenantResolver.resolve` now returns a sealed `TenantResolution` (`Resolved` / `NotApplicable` / `Rejected`) instead of `Optional<String>`. A present-but-invalid input rejects the request with HTTP 401 rather than silently falling back to the default tenant.
- The JWT tenant strategy now requires SmallRye JWT (or Quarkus OIDC) for token verification. The previous base64-decode path that read claims from unsigned tokens has been removed; the resolver injects the verified `JsonWebToken` and rejects requests whose token cannot be verified or is missing the configured claim.
- README rewritten for the post-migration Quarkiverse coordinates and the verified JWT behaviour.
- Quarkiverse parent POM bumped to version 22.

### Operator notes

- The extension is marked as **preview** while the API stabilises. Source and binary compatibility may change before `1.0`.
- The default strategy chain is `header,jwt,cookie`. Applications that include the `jwt` strategy (explicitly or via the implicit default) **must** configure one of the following before the application boots:
    - SmallRye JWT verification via `mp.jwt.verify.publickey.*` and `mp.jwt.verify.issuer`.
    - Quarkus OIDC via `quarkus.oidc.auth-server-url` (or a named-tenant equivalent such as `quarkus.oidc.<tenant>.auth-server-url`).
- Applications that produce a custom `JsonWebToken` outside of SmallRye JWT / OIDC can opt out of the boot-time verification check with `quarkus.multi-tenant.http.jwt.skip-startup-check=true`.
- The `quarkus-multitenancy-orm` module registers an `X-Tenant` header filter by default that rejects requests without the header (HTTP 400). When tenant resolution is driven by `quarkus-multitenancy-http` (path, jwt, or cookie), disable the ORM-side filter with `quarkus.multi-tenant.orm.header-filter.enabled=false` to avoid the two filters colliding on the request lifecycle. The resolved tenant still reaches Hibernate ORM through the shared `TenantContext`.
- Tenants resolved from any strategy are propagated through the same `TenantContext`. The extension does not enforce length, charset, or log-injection guards on the resolved identifier; downstream consumers should treat the value as untrusted input until a future hardening release lands.
