# 🧪 Quarkus Multi-Tenancy Demo

A runnable demo of the Quarkus Multitenancy extension with:

- HTTP tenant resolution from header, cookie, path, and the configured default tenant
- deterministic strategy precedence (`header,cookie,path`)
- a custom `TenantResolver` that only accepts tenants present in the connection catalog
- tenant-id validation for externally supplied values
- Hibernate ORM DATABASE multitenancy with programmatically created Agroal pools
- separate PostgreSQL databases for `tenant1` and `tenant2`

JWT is intentionally not enabled in the default strategy chain. The extension requires a verified `JsonWebToken` source (SmallRye JWT, OIDC, or an authenticated custom producer) before enabling the `jwt` strategy.

## Run the demo

```bash
docker compose down -v
docker compose up -d
mvn quarkus:dev
```

Databases:

- `tenant1` → `localhost:5433/tenant1` → `user1 / pass1`
- `tenant2` → `localhost:5434/tenant2` → `user2 / pass2`

Each database starts with distinct seed data so routing is immediately visible:

- `tenant1@example.com` exists only in `tenant1`
- `tenant2@example.com` exists only in `tenant2`

## Programmatic tenant connections

The demo keeps one Quarkus-managed `__bootstrap` datasource for Hibernate ORM startup. Tenant databases are not declared as `quarkus.datasource.<tenant>` entries. Instead, the application-owned catalog uses `demo.tenant-catalog.tenants.<tenant>` entries:

```properties
demo.tenant-catalog.tenants.tenant1.jdbc-url=jdbc:postgresql://localhost:5433/tenant1
demo.tenant-catalog.tenants.tenant1.username=user1
demo.tenant-catalog.tenants.tenant1.password=pass1
```

`ProgrammaticTenantConnectionResolver` implements Quarkus' existing `TenantConnectionResolver` SPI. On first use of a tenant it reads the catalog entry, creates an Agroal pool with Narayana transaction integration, and returns a Hibernate `ConnectionProvider`.

The config-backed catalog is deliberately replaceable. A production application can implement `TenantConnectionCatalog` with a database, control plane, or secrets-aware service without changing the resolver.

### Lifecycle boundary

This example creates one stable provider per tenant and closes its pools only when the application stops. It does not implement live pool replacement, credential rotation, drain-on-remove, or re-registration. Quarkus also caches the provider returned for a tenant, so changing a catalog record after first resolution does not refresh that tenant's active provider.

Programmatically created pools also need explicit health and metrics integrations. Do not copy plaintext credentials from this demo into production configuration; resolve secret references through the catalog implementation instead.

## Tenant resolution scenarios

Header:

```bash
curl -H "X-Tenant: tenant1" http://localhost:8080/api/users/tenant
```

Cookie:

```bash
curl --cookie "tenant_cookie=tenant2" http://localhost:8080/api/users/tenant
```

Path:

```bash
curl http://localhost:8080/api/users/tenant/path/tenant2
curl http://localhost:8080/api/users/path/tenant2
```

Default tenant (`tenant1`):

```bash
curl http://localhost:8080/api/users/tenant
```

When multiple inputs are present, the configured chain applies in order. For example, the header wins over the cookie.

## ORM isolation

Read each database independently:

```bash
curl -H "X-Tenant: tenant1" http://localhost:8080/api/users
curl -H "X-Tenant: tenant2" http://localhost:8080/api/users
curl --cookie "tenant_cookie=tenant2" http://localhost:8080/api/users
curl http://localhost:8080/api/users/path/tenant2
```

Every response must contain only the selected tenant's data.

## Tests

`TenantResolutionScenariosTest` runs without PostgreSQL and covers header, cookie, path, default-tenant resolution, and precedence.

`ProgrammaticDatabaseRoutingTest` uses two in-memory H2 databases and verifies the programmatic catalog/pool path without Docker. `ProgrammaticTenantConnectionResolverTest` verifies catalog lookup, stable provider caching, and rejection of unknown tenants.

`DatabaseRoutingScenariosTest` starts two real PostgreSQL Testcontainers with different seed rows and verifies end-to-end database routing and isolation for:

- header → tenant1 and tenant2
- cookie → tenant2
- path → tenant2
- default tenant → tenant1
- header-over-cookie precedence

The test containers use the PostgreSQL 18 Docker Official Image mirror at
`public.ecr.aws/docker/library/postgres:18` to avoid Docker Hub's anonymous pull
limit on shared CI runners. Docker and access to Amazon ECR Public are required.

`UserResourceTest` remains as the manually configured PostgreSQL CRUD example when the demo databases are running on ports 5433 and 5434.

From the repository root:

```bash
mvn -B clean install -Dno-format
```

The Postman collection remains available under `postman/demo.postman_collection.json`.
