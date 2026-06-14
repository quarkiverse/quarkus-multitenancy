# Changelog

## Unreleased

### Added

- Tenant identifier hardening: a resolved tenant id is now validated against a configurable maximum length (`quarkus.multi-tenant.http.tenant-id.max-length`, default `64`) and a character-set pattern (`quarkus.multi-tenant.http.tenant-id.pattern`, default `[A-Za-z0-9_-]+`) before it is published to the `TenantContext`. A violating identifier is rejected with HTTP 401 and never reaches downstream consumers (logs, SQL parameters, ORM tenant lookups). Rejected identifiers are sanitised — control characters stripped and length-capped — before being logged, closing a log-injection vector. (#16)

### Changed

- **Behaviour change:** tenant-id validation is enabled by default. An application whose tenant identifiers contain characters outside `[A-Za-z0-9_-]` or exceed 64 characters will now receive HTTP 401 where `0.1.0` accepted the request. Widen `quarkus.multi-tenant.http.tenant-id.pattern` / `quarkus.multi-tenant.http.tenant-id.max-length`, or set `quarkus.multi-tenant.http.tenant-id.validation-enabled=false`, to restore the previous behaviour.

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
- The default strategy chain is `header,cookie`. Applications that include the `jwt` strategy **must** configure one of the following before the application boots:
    - SmallRye JWT verification via `mp.jwt.verify.publickey.*` and `mp.jwt.verify.issuer`.
    - Quarkus OIDC via `quarkus.oidc.auth-server-url` (or a named-tenant equivalent such as `quarkus.oidc.<tenant>.auth-server-url`).
- Applications that produce a custom `JsonWebToken` outside of SmallRye JWT / OIDC can opt out of the boot-time verification check with `quarkus.multi-tenant.http.jwt.skip-startup-check=true`.
- The `quarkus-multitenancy-orm` module registers an `X-Tenant` header filter by default that rejects requests without the header (HTTP 400). When tenant resolution is driven by `quarkus-multitenancy-http` (path, jwt, or cookie), disable the ORM-side filter with `quarkus.multi-tenant.orm.header-filter.enabled=false` to avoid the two filters colliding on the request lifecycle. The resolved tenant still reaches Hibernate ORM through the shared `TenantContext`.
- Tenants resolved from any strategy are propagated through the same `TenantContext`. The extension does not enforce length, charset, or log-injection guards on the resolved identifier; downstream consumers should treat the value as untrusted input until a future hardening release lands.
