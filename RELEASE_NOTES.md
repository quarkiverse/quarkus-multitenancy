# Release notes — `0.3.0`

Third preview release of Quarkus Multitenancy, building on `0.2.0` with deterministic custom resolver ordering, named Hibernate ORM persistence units, a programmatic tenant-connection example, and explicit custom-only HTTP resolution.

The extension remains preview; pin the exact version while its API stabilises.

## Highlights

### Deterministic custom resolver ordering

Multiple `TenantResolver` beans now execute in a stable order: higher Jakarta `@Priority` values first, then implementation class name for equal priorities. Resolvers without an explicit annotation use priority `0`. Ambiguous beans of the same type and priority fail startup with an actionable error. The built-in HTTP chain still follows `quarkus.multi-tenant.http.strategy`.

See [PR #79](https://github.com/quarkiverse/quarkus-multitenancy/pull/79).

### Named Hibernate ORM persistence units

Use `quarkus.multi-tenant.orm.named-persistence-units` to attach the shared `TenantContext` adapter to selected multitenant named persistence units. The default unit remains integrated automatically, and application-provided tenant resolvers retain precedence. Invalid names and non-multitenant selections fail the build.

See [PR #81](https://github.com/quarkiverse/quarkus-multitenancy/pull/81).

### Custom-only HTTP resolution

Applications can disable all built-in HTTP tenant strategies explicitly:

```properties
quarkus.multi-tenant.http.strategy=none
```

Custom `TenantResolver` beans, tenant-id validation, and the configured default-tenant fallback remain active. Configure `none` by itself; combining it with another strategy fails startup. This resolves [issue #95](https://github.com/quarkiverse/quarkus-multitenancy/issues/95) without requiring an empty configuration value.

See [PR #96](https://github.com/quarkiverse/quarkus-multitenancy/pull/96).

### Programmatic tenant connections and runnable scenarios

The demo uses Quarkus' existing `TenantConnectionResolver` SPI with an application-owned catalog and Agroal pools integrated with Narayana transactions. It includes Docker-free H2 routing tests and PostgreSQL Testcontainers coverage, plus seeded databases that demonstrate tenant isolation across HTTP resolution scenarios.

This is an application example, not a new runtime tenant-registration API. Quarkus caches the provider returned for a tenant; live pool replacement, credential rotation, removal, and re-registration remain outside this example's scope.

See [PR #80](https://github.com/quarkiverse/quarkus-multitenancy/pull/80), [PR #82](https://github.com/quarkiverse/quarkus-multitenancy/pull/82), and the [demo guide](quarkus-multitenancy-demo/README.md).

## Documentation and maintenance

- Dedicated Kafka propagation guide and expanded project overview ([PR #85](https://github.com/quarkiverse/quarkus-multitenancy/pull/85)).
- Quarkiverse parent updated from `22` to `26`; Quarkus baseline updated from `3.33.3.1` to `3.40.1`.
- Current Quarkus JUnit artifact names and extension test APIs restore compatibility with Quarkus main snapshots.
- PostgreSQL test-image pulls use ECR Public with Docker Hub fallback in CI.
- Java `21` remains the compilation and release-toolchain baseline.
- The JVM CI matrix now runs the full reactor on both Java `21` and `25`. Native compilation remains on Java `21`.

## Upgrade from `0.2.0`

1. Review applications with multiple custom resolvers: ordering is now deterministic. Set distinct `@Priority` values where precedence matters; higher values run first.
2. Opt into named ORM persistence units only where needed using `quarkus.multi-tenant.orm.named-persistence-units`.
3. For custom-only HTTP resolution, use `strategy=none` rather than an empty strategy value. Keep the HTTP filter enabled.
4. Validate your application against the Quarkus `3.40.1` baseline.
5. Treat the connection-catalog demo as a starting point: implement production secret management and lifecycle handling appropriate to your application.

The `header,cookie` default chain and tenant-id validation defaults were introduced in `0.2.0` and are unchanged in this release. Kafka propagation and `TenantContextRunner` were also already available in `0.2.0`.

## Release validation

The source baseline is commit `f50fdebd9fc83969dbc43c5fa83a9bc355ce8522`. Its [main build](https://github.com/quarkiverse/quarkus-multitenancy/actions/runs/43318310386) passed both `mvn -B clean install -Dno-format` and `mvn -B install -Dnative -Dquarkus.native.container-build -Dnative.surefire.skip`.

The release preparation PR must pass its own checks before merge. Merging the `.github/project.yml` version change invokes the existing Quarkiverse prepare-release workflow; publication is handled by the perform-release workflow.

The Quarkus `3.40.1.2` update proposed in the [Renovate dashboard](https://github.com/quarkiverse/quarkus-multitenancy/issues/58) is not included in this baseline. Java `25` is added as a JVM CI target, while Java `21` remains the minimum compilation version and release toolchain. The new Java `25` job must pass before merging this release preparation.

**Full changelog:** [0.2.0...0.3.0](https://github.com/quarkiverse/quarkus-multitenancy/compare/0.2.0...0.3.0)

Previous release: [0.2.0 notes](docs/releases/0.2.0.md).
