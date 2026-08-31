# 🧩 Quarkus Multitenancy Extension

[![Build](https://github.com/quarkiverse/quarkus-multitenancy/actions/workflows/build.yml/badge.svg)](https://github.com/quarkiverse/quarkus-multitenancy/actions/workflows/build.yml)
[![Documentation](https://img.shields.io/badge/docs-Quarkiverse-blue)](https://docs.quarkiverse.io/quarkus-multitenancy/dev/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21%2B-blue)
![Quarkus](https://img.shields.io/badge/Quarkus-3.x-red)
![Status](https://img.shields.io/badge/status-preview-orange)

> **Resolve a tenant once. Keep it across HTTP, reactive code, Kafka, background work, and Hibernate ORM.**

A modular multitenancy extension for Quarkus providing a shared tenant context, pluggable HTTP resolution, Kafka tenant propagation, and Hibernate ORM integration.

Quarkus Multitenancy provides a generic tenant-resolution contract and a request-scoped `TenantContext` that can be reused across application and integration boundaries.

The extension focuses on **tenant identification and propagation**. It does not create database, schema, cache, or authorization isolation by itself; those remain application and framework configuration concerns.

## Why this exists

Quarkus already provides powerful building blocks such as OIDC multitenancy and Hibernate ORM multitenancy. Applications still frequently need a common tenant abstraction that can be resolved once and consumed consistently across HTTP, persistence, messaging, background work, and custom integrations.

This extension provides that reusable layer while keeping the individual integrations independent.

```text
                         ┌──────────────────────────┐
 HTTP request            │ header · cookie · JWT   │
 ───────────────────────▶│ path · custom resolver │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                             ┌─────────────────┐
                             │  TenantContext  │
                             └───────┬─────────┘
                                     │
                 ┌───────────────────┼────────────────────┐
                 │                   │                    │
                 ▼                   ▼                    ▼
        Reactive / worker      Hibernate ORM         Kafka producer
             work             tenant routing       X-Tenant header
                                                        │
                                                        ▼
                                                  Kafka consumer
                                                        │
                                                        ▼
                                                  TenantContext
```

### End-to-end in 60 seconds

An incoming request can establish a tenant once:

```http
POST /orders
X-Tenant: acme
```

Downstream application code reads the shared context:

```java
@Inject
TenantContext tenantContext;

String tenant = tenantContext.getTenantId().orElseThrow();
```

Hibernate ORM can use that same context for tenant routing. If the application then publishes through a Kafka channel:

```java
@Inject
@Channel("orders")
Emitter<String> orders;

void publish(String order) {
    orders.send(order);
}
```

the Kafka integration can propagate the current tenant as record metadata:

```text
X-Tenant: acme
```

and restore it for the consumer handler:

```java
@Incoming("orders")
void consume(String order) {
    String tenant = tenantContext.getTenantId().orElseThrow();
    // tenant == "acme"
}
```

**One tenant identity from HTTP ingress to persistence to Kafka consumer, without putting `tenantId` in every application payload.**

See the [Kafka tenant propagation guide](docs/modules/ROOT/pages/kafka-tenant-propagation.adoc) for the complete messaging contract, validation, strict missing-tenant policies, and failure handling.

## Modules

| Module | Responsibility |
| --- | --- |
| `quarkus-multitenancy-core-runtime` | `TenantContext`, `TenantResolver`, resolution outcomes, and synchronous tenant binding |
| `quarkus-multitenancy-core-deployment` | Core Quarkus build-time integration |
| `quarkus-multitenancy-http-runtime` | HTTP tenant resolution from header, cookie, JWT claim, path, or custom resolvers |
| `quarkus-multitenancy-http-deployment` | HTTP integration registration |
| `quarkus-multitenancy-messaging-kafka-runtime` | Incoming and outgoing Kafka tenant propagation |
| `quarkus-multitenancy-messaging-kafka-deployment` | Kafka integration registration |
| `quarkus-multitenancy-orm-runtime` | Bridges the shared `TenantContext` into Hibernate ORM tenant resolution |
| `quarkus-multitenancy-orm-deployment` | ORM integration registration |
| `quarkus-multitenancy-demo` | PostgreSQL multi-tenant demo application |

## Core model

`TenantContext` is request scoped and exposes the active tenant to downstream code:

```java
@Inject
TenantContext tenantContext;

String tenant = tenantContext.getTenantId().orElseThrow();
```

Tenant resolvers return one of three explicit outcomes:

| Outcome | Meaning |
| --- | --- |
| `Resolved` | A tenant identifier was resolved successfully |
| `NotApplicable` | The resolver had no applicable input, so resolution may continue |
| `Rejected` | Input was present but invalid or untrusted; the request must not silently fall back |

For HTTP requests, a `Rejected` outcome aborts the request with HTTP `401`. If every resolver returns `NotApplicable`, the configured default tenant is used.

## HTTP tenant resolution

Add the HTTP extension:

```xml
<dependency>
    <groupId>io.quarkiverse.multitenancy</groupId>
    <artifactId>quarkus-multitenancy-http</artifactId>
    <version>${quarkus-multitenancy.version}</version>
</dependency>
```

The built-in HTTP strategies are:

- `header` — tenant from a configurable HTTP header, default `X-Tenant`
- `cookie` — tenant from a configurable cookie, default `tenant_cookie`
- `jwt` — tenant from a verified JWT claim, default `tenant`
- `path` — tenant from a configurable request-path regular expression

The default built-in strategy chain is:

```properties
quarkus.multi-tenant.http.strategy=header,cookie
```

A complete example:

```properties
quarkus.multi-tenant.http.enabled=true
quarkus.multi-tenant.http.strategy=header,jwt,cookie,path
quarkus.multi-tenant.http.header-name=X-Tenant
quarkus.multi-tenant.http.cookie-name=tenant_cookie
quarkus.multi-tenant.http.jwt-claim-name=tenant
quarkus.multi-tenant.http.default-tenant=public
quarkus.multi-tenant.http.path-pattern=^/t/([^/]+)(?:/|$)
quarkus.multi-tenant.http.path-group=1
```

Custom CDI beans implementing `TenantResolver` run before the configured built-in chain. Annotate custom resolvers with Jakarta `@Priority` when more than one may handle the same request; higher values run first, the default is `0`, and equal priorities are ordered by implementation class name. Built-in resolvers run in the order declared by `quarkus.multi-tenant.http.strategy`.

### HTTP tenant-id validation

Resolved HTTP tenant identifiers are validated before they are published to `TenantContext`:

```properties
quarkus.multi-tenant.http.tenant-id.validation-enabled=true
quarkus.multi-tenant.http.tenant-id.max-length=64
quarkus.multi-tenant.http.tenant-id.pattern=[A-Za-z0-9_-]+
quarkus.multi-tenant.http.tenant-id.reject-status=400
```

The default rejection status for an invalid resolved identifier is HTTP `400`. This is intentionally different from a resolver-level `Rejected` outcome, which represents an authentication/trust failure and produces HTTP `401`.

Invalid strategy names, invalid tenant-validation configuration, invalid path regular expressions, invalid path capture groups, and incompatible JWT setup fail fast during startup where applicable.

## JWT tenant resolution

The `jwt` strategy is opt-in and expects a verified identity supplied by SmallRye JWT, Quarkus OIDC, or an application-provided `JsonWebToken` bean.

Example with SmallRye JWT:

```properties
quarkus.multi-tenant.http.strategy=jwt
quarkus.multi-tenant.http.jwt-claim-name=tenant

mp.jwt.verify.publickey.location=publicKey.pem
mp.jwt.verify.publickey.algorithm=RS256
mp.jwt.verify.issuer=https://issuer.example.com
```

If a bearer token is present but cannot be trusted, or the configured tenant claim is invalid, the request is rejected and does not fall back to the default tenant.

Applications that intentionally provide their own authenticated `JsonWebToken` bean can opt out of the built-in startup check:

```properties
quarkus.multi-tenant.http.jwt.skip-startup-check=true
```

## Synchronous background work

For scheduled jobs, startup observers, maintenance callbacks, or other synchronous work outside the HTTP pipeline, use `TenantContextRunner`:

```java
@Inject
TenantContextRunner tenantRunner;

void refreshTenant() {
    tenantRunner.runAsTenant("acme", () -> {
        // TenantContext contains "acme" here.
    });
}
```

`TenantContextRunner` restores the previous tenant after the callback completes or throws and can temporarily activate the CDI request context when one is not already active.

It is intentionally **synchronous only**. Do not use it to return a `CompletionStage`, Mutiny `Uni`, or other asynchronous result that may outlive the callback.

`TenantContextRunner` is a trusted programmatic boundary and does not apply the HTTP or Kafka tenant-id validation policy automatically. Validate or map externally controlled tenant identifiers before binding them.

## Reactive work within an HTTP request

Reactive does not automatically mean that tenant propagation must be handled manually. While work remains inside the same Quarkus REST request, the request-scoped `TenantContext` is preserved by Quarkus across supported context-aware boundaries.

This includes Mutiny `Uni` pipelines, `@Blocking`/worker-thread dispatch, and `CompletionStage` work executed through a MicroProfile `ManagedExecutor`. A REST endpoint using those mechanisms does not need `TenantContextRunner` just because execution is asynchronous.

This behavior comes from Quarkus REST, Vert.x duplicated context, and SmallRye Context Propagation rather than from a custom propagation mechanism in this extension. Work submitted directly to a raw JDK executor is different and does not automatically inherit the request context.

See `docs/modules/ROOT/pages/index.adoc` for the detailed reactive boundary table and `docs/modules/ROOT/pages/context-propagation.adoc` for the cross-boundary propagation guide.

## Kafka tenant propagation

Add the optional Kafka module:

```xml
<dependency>
    <groupId>io.quarkiverse.multitenancy</groupId>
    <artifactId>quarkus-multitenancy-messaging-kafka</artifactId>
    <version>${quarkus-multitenancy.version}</version>
</dependency>
```

The Kafka integration applies only to SmallRye Reactive Messaging channels backed by the Kafka connector. It does not modify AMQP, in-memory, or custom connectors.

Incoming propagation requires Quarkus Messaging request scope:

```properties
quarkus.messaging.request-scoped.enabled=true
```

Basic configuration:

```properties
quarkus.multi-tenant.messaging.kafka.enabled=true
quarkus.multi-tenant.messaging.kafka.header-name=X-Tenant
quarkus.multi-tenant.messaging.kafka.fail-on-missing-incoming-tenant=false
quarkus.multi-tenant.messaging.kafka.fail-on-missing-outgoing-tenant=false
```

Outgoing messages inherit the current tenant through Kafka record metadata unless application code already supplied the configured tenant header explicitly.

Incoming Kafka tenant identifiers are treated as untrusted external input and are validated before binding:

```properties
quarkus.multi-tenant.messaging.kafka.tenant-id.validation-enabled=true
quarkus.multi-tenant.messaging.kafka.tenant-id.max-length=64
quarkus.multi-tenant.messaging.kafka.tenant-id.pattern=[A-Za-z0-9_-]+
```

Applications can also provide CDI beans implementing `KafkaTenantValidator` for domain-specific checks.

For complete producer/consumer examples, explicit-header precedence, strict missing-tenant behavior, validation inheritance, custom validators, and nack/failure-strategy guidance, see the [dedicated Kafka tenant propagation guide](docs/modules/ROOT/pages/kafka-tenant-propagation.adoc).

## Hibernate ORM integration

Add the ORM extension:

```xml
<dependency>
    <groupId>io.quarkiverse.multitenancy</groupId>
    <artifactId>quarkus-multitenancy-orm</artifactId>
    <version>${quarkus-multitenancy.version}</version>
</dependency>
```

`OrmTenantResolverAdapter` bridges the request-scoped `TenantContext` into Hibernate ORM's tenant resolver SPI.

During ORM bootstrap, the adapter uses an internal reserved bootstrap tenant. During application ORM access, a tenant must already be present in `TenantContext`; otherwise access fails instead of silently selecting another tenant.

The ORM module also contains a legacy `X-Tenant` request filter, enabled by default:

```properties
quarkus.multi-tenant.orm.header-filter.enabled=true
```

When HTTP tenant resolution is handled by `quarkus-multitenancy-http` — especially when using `jwt`, `cookie`, or `path` — disable the ORM header filter so both filters do not compete:

```properties
quarkus.multi-tenant.orm.header-filter.enabled=false
```

The default Hibernate ORM persistence unit is integrated automatically. To use the same `TenantContext` with named multitenant persistence units, select them explicitly at build time:

```properties
quarkus.multi-tenant.orm.named-persistence-units=users,inventory

quarkus.hibernate-orm."users".multitenant=DATABASE
quarkus.hibernate-orm."inventory".multitenant=SCHEMA
```

Every selected name must identify an existing Hibernate ORM persistence unit with multitenancy enabled. A non-selected, non-multitenant unit receives no adapter. If the application provides its own `TenantResolver` for a persistence unit, that resolver overrides the built-in adapter without creating a CDI ambiguity.

## Propagation boundaries

| Boundary | Mechanism |
| --- | --- |
| Incoming HTTP request | HTTP resolver chain |
| Reactive/worker work inside the same HTTP request | Automatic through supported Quarkus REST / Vert.x / SmallRye context propagation (`Uni`, `@Blocking`, `ManagedExecutor`) |
| Synchronous scheduled/background callback | `TenantContextRunner.runAsTenant(...)` |
| Async work after leaving the request or temporary background binding | Use a context-aware mechanism for that framework, or pass the tenant id explicitly |
| Raw executor submitted to directly | Request context is not propagated automatically |
| Incoming Kafka message | Kafka record header → validated `TenantContext` |
| Outgoing Kafka message | Current `TenantContext` → Kafka record header |
| Hibernate ORM access | `TenantContext` → ORM tenant resolver adapter |

`TenantContext` should not be treated as global or as a general-purpose thread-local value. Supported reactive work within an active Quarkus REST request keeps the request context automatically; explicit propagation is needed when work leaves that context or crosses into another integration boundary.

## Quick start

```bash
mvn clean install
cd quarkus-multitenancy-demo
mvn quarkus:dev
```

## Documentation

The Quarkiverse documentation contains the detailed runtime contracts, configuration guidance, and tenant-context propagation behavior:

https://docs.quarkiverse.io/quarkus-multitenancy/dev/

Relevant source pages in this repository include:

- `docs/modules/ROOT/pages/index.adoc`
- `docs/modules/ROOT/pages/context-propagation.adoc`
- `docs/modules/ROOT/pages/kafka-tenant-propagation.adoc`
- `docs/modules/ROOT/pages/runtime-contracts.adoc`
- `docs/modules/ROOT/pages/programmatic-tenant-connections.adoc`
- `docs/modules/ROOT/pages/migration-0.2.adoc`

## License

This project is licensed under the Apache License 2.0.
