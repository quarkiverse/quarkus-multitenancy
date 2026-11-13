# Release notes — `0.2.0`

Second preview release of the **Quarkus Multitenancy** extension.

> **Status:** preview. The API may evolve before `1.0`. Pin the exact version while we stabilise.

---

## What's in this release

`0.2.0` substantially expands the extension beyond synchronous HTTP tenant resolution. It adds tenant-scoped background execution and Kafka propagation, defines the supported reactive/asynchronous request contract, strengthens tenant-id validation and startup checks, protects internal ORM tenant state, unifies HTTP and ORM request resolution, and aligns the project with the shared Quarkiverse Quarkus LTS policy.

The extension now provides four main integration areas:

| Extension | Purpose |
|-----------|---------|
| `io.quarkiverse.multitenancy:quarkus-multitenancy-core` | `TenantResolver` SPI, request-scoped `TenantContext`, shared tenant-id validation, reserved tenant identifiers, and `TenantContextRunner`. |
| `io.quarkiverse.multitenancy:quarkus-multitenancy-http` | HTTP tenant resolution through header, JWT claim, cookie, and path strategies. |
| `io.quarkiverse.multitenancy:quarkus-multitenancy-messaging-kafka` | Tenant propagation through Kafka record headers with validation and lifecycle handling. |
| `io.quarkiverse.multitenancy:quarkus-multitenancy-orm` | Bridges the shared `TenantContext` into Hibernate ORM multitenancy. |

---

## Highlights

### Tenant-scoped background work

`TenantContextRunner` is a first-class API for running synchronous `Runnable` or `Supplier<T>` work under a temporary tenant, including scheduled jobs, startup observers, and maintenance tasks.

It activates a CDI request context when one is not already active, preserves and restores the previous tenant, and safely supports nested, sequential, successful, and failing invocations.

The runner is intentionally synchronous-only. Returning a deferred Mutiny `Uni` or `CompletionStage` is rejected immediately so asynchronous work cannot silently outlive the temporary tenant binding. (PR #60 — **Add tenant-scoped background work API** — @mathias82)

### Kafka tenant propagation

A new optional `quarkus-multitenancy-messaging-kafka` extension propagates tenant identity through SmallRye Reactive Messaging Kafka boundaries. (PR #61 — **Propagate tenants through Kafka messaging** — @mathias82)

It provides automatic outgoing/incoming propagation, ack/nack lifecycle cleanup, explicit metadata precedence, configurable headers, strict missing-tenant policies, UTF-8 decoding and validation, custom `KafkaTenantValidator` support, and fail-fast configuration validation.

For incoming propagation, enable the Quarkus Messaging request scope:

```properties
quarkus.messaging.request-scoped.enabled=true
```

### Reactive and asynchronous request propagation

The supported tenant-context behavior across Quarkus REST asynchronous boundaries is now explicitly documented and tested. (PR #72 — **Add reactive and async tenant propagation contract tests (#66)** — @lu1tr0n)

Within the same HTTP request, the resolved tenant remains available across supported Mutiny `Uni`, `@Blocking` / worker-thread, `ManagedExecutor`, failure, and cancellation boundaries. Raw JDK executor submissions are not automatically context-aware.

### Tenant identifier hardening

Every tenant identifier resolved through the HTTP pipeline is validated before it reaches `TenantContext`. (PR #27 — **feat(http): validate resolved tenant id length and character set (#16)** — @lu1tr0n; PR #34 — **feat(http): make tenant-id reject status configurable (#29)** — @lu1tr0n)

The default policy is:

```properties
quarkus.multi-tenant.http.tenant-id.validation-enabled=true
quarkus.multi-tenant.http.tenant-id.max-length=64
quarkus.multi-tenant.http.tenant-id.pattern=[A-Za-z0-9_-]+
quarkus.multi-tenant.http.tenant-id.reject-status=400
```

Rejected identifiers are sanitized and length-bounded before logging. Only `4xx` values are accepted for `reject-status`; invalid values fail fast at startup. (PR #41 — **Fail fast on non-4xx tenant-id reject-status** — @lu1tr0n)

The configured `default-tenant` is validated against the same policy at startup. (PR #43 — **Fail fast when default-tenant violates the tenant-id policy** — @lu1tr0n)

### Safer HTTP strategy configuration

The default HTTP strategy chain is now `header,cookie`; JWT is explicit opt-in. (PR #32 — **feat(http): default strategy chain to header,cookie (#15)** — @lu1tr0n)

Built-in strategy names are case-insensitive, and unknown names fail fast at startup. (PR #42 — **Fail fast on unknown HTTP tenant strategy names** — @lu1tr0n)

Custom `TenantResolver` precedence is documented explicitly: custom resolvers execute before the configured built-in chain. (PR #45 — **docs: clarify TenantResolver precedence for custom resolvers** — @ahfoysal)

### Reserved ORM bootstrap tenant

The internal Hibernate ORM tenant identifier `__bootstrap` is reserved and cannot be selected from externally controlled tenant input. (PR #69 — **fix: prevent external selection of the ORM bootstrap tenant** — @mathias82)

### Unified HTTP and ORM tenant resolution

Applications using both HTTP and ORM no longer need to manually disable the ORM header filter. The HTTP pipeline is authoritative and the ORM filter acts only as fallback. (PR #73 — **Unify HTTP and ORM tenant resolution** — @mathias82)

### Quarkus LTS alignment

The project is aligned with the shared Quarkiverse Quarkus LTS policy and now targets Quarkus `3.33.3.1`. The alignment was validated across the full JVM reactor and native integration tests without requiring compatibility changes. (PR #77 — **Align Quarkus version with the shared LTS policy** — @lu1tr0n)

---

## Migration notes from `0.1.0`

### JWT is now opt-in

If your application relied on JWT because it was part of the old implicit `header,jwt,cookie` chain, configure it explicitly:

```properties
quarkus.multi-tenant.http.strategy=header,jwt,cookie
```

### Tenant-id validation is enabled by default

Identifiers accepted by `0.1.0` may now be rejected if they are longer than 64 characters or contain characters outside `[A-Za-z0-9_-]`. Configure a wider policy deliberately if required.

### Tenant-id validation failures now default to HTTP 400

Syntactically invalid tenant identifiers use HTTP `400` by default, while authentication-related resolver rejection remains HTTP `401`. Only statuses from `400` through `499` are accepted.

### Combined HTTP + ORM applications

The old `quarkus.multi-tenant.orm.header-filter.enabled=false` workaround is no longer required merely to prevent ORM from competing with HTTP resolution.

### ORM-only tenant validation

The ORM fallback header now uses the shared tenant-id validation policy. For example, `acme.co` is rejected by the default `[A-Za-z0-9_-]+` policy unless the pattern is widened.

### Background and asynchronous work

`TenantContextRunner` supports synchronous callbacks only. Supported Quarkus request-associated asynchronous boundaries propagate context automatically; raw executors do not.

### Kafka consumers

Incoming Kafka tenant propagation requires:

```properties
quarkus.messaging.request-scoped.enabled=true
```

---

## Testing and reliability

- Native-mode HTTP/ORM integration coverage — PR #47 **test(http): native integration tests for tenant resolution** — @lu1tr0n.
- Reactive/asynchronous JVM and native propagation coverage — PR #72 **Add reactive and async tenant propagation contract tests (#66)** — @lu1tr0n.
- Kafka propagation and lifecycle coverage — PR #61 **Propagate tenants through Kafka messaging** — @mathias82.
- Combined HTTP + ORM regression coverage — PR #73 **Unify HTTP and ORM tenant resolution** — @mathias82.
- Reserved `__bootstrap` regression coverage — PR #69 **fix: prevent external selection of the ORM bootstrap tenant** — @mathias82.
- Quarkus LTS alignment validated with the full JVM reactor and native integration tests — PR #77 **Align Quarkus version with the shared LTS policy** — @lu1tr0n.

---

## Documentation and project maintenance

The documentation has been aligned with the runtime implementation, including dedicated propagation/runtime-contract references, README guidance, Kafka configuration, HTTP/ORM behavior, validation boundaries, migration notes, and propagation limitations. (PR #74 — **Align documentation with multitenancy runtime contracts** — @mathias82)

Other project maintenance includes:

- PR #28 **docs: complete public-API Javadoc and silence default-constructor doclint noise** — @lu1tr0n,
- PR #31 **chore: add Apache-2.0 license headers to source files** — @lu1tr0n,
- Quarkus dependency updates through `3.38.0`, followed by explicit alignment to the supported LTS `3.33.3.1` in PR #77,
- PR #55 **chore: Configure Renovate** — @renovate[bot],
- PR #59 **chore: drop Dependabot in favor of Renovate** — @lu1tr0n,
- CI action updates,
- PR #71 **chore(deps): update postgres docker tag to v17** — @renovate[bot],
- PR #75 **Update postgres Docker tag to v18** — @renovate[bot].

---

## Known limitations / planned follow-ups

The following are intentionally outside the `0.2.0` release scope:

- runtime ORM tenant registration / dynamic connection providers (#50),
- deterministic ordering for multiple custom `TenantResolver` implementations (#64),
- named Hibernate ORM persistence-unit integration (#65),
- optional tenant correlation for logs, metrics, and traces (#67).

The ORM adapter currently targets the default Hibernate ORM persistence unit. Raw executor submissions are not automatically tenant-context aware, and `TenantContextRunner` intentionally remains synchronous-only.

---

## Upgrade checklist

1. Opt into `jwt` explicitly if tenant resolution depends on verified JWT claims.
2. Check existing tenant identifiers against the new default length/pattern policy.
3. Review any custom `tenant-id.reject-status`; it must be a `4xx` value.
4. Verify that the configured `default-tenant` satisfies the tenant-id policy.
5. For ORM-only applications, verify existing header tenant identifiers against the shared validation policy.
6. For combined HTTP + ORM applications, remove the old header-filter-disable workaround unless intentionally required.
7. Review asynchronous boundaries: supported Quarkus request propagation is automatic, raw executors are not.
8. For Kafka consumers, enable `quarkus.messaging.request-scoped.enabled=true` and choose appropriate missing-tenant/failure policies.
9. Validate application compatibility with the Quarkus `3.33.3.1` LTS line used by this release.

---

## Merged pull requests

The following pull requests were merged after `0.1.0` and are included in `0.2.0`:

- PR #27 — **feat(http): validate resolved tenant id length and character set (#16)** — @lu1tr0n
- PR #28 — **docs: complete public-API Javadoc and silence default-constructor doclint noise** — @lu1tr0n
- PR #31 — **chore: add Apache-2.0 license headers to source files** — @lu1tr0n
- PR #32 — **feat(http): default strategy chain to header,cookie (#15)** — @lu1tr0n
- PR #33 — **Bump com.mycila:license-maven-plugin from 4.6 to 5.0.0** — @dependabot[bot]
- PR #34 — **feat(http): make tenant-id reject status configurable (#29)** — @lu1tr0n
- PR #35 — **Bump quarkus.version from 3.36.2 to 3.37.0** — @dependabot[bot]
- PR #41 — **Fail fast on non-4xx tenant-id reject-status** — @lu1tr0n
- PR #42 — **Fail fast on unknown HTTP tenant strategy names** — @lu1tr0n
- PR #43 — **Fail fast when default-tenant violates the tenant-id policy** — @lu1tr0n
- PR #44 — **chore(deps): bump quarkus.version from 3.37.0 to 3.37.1** — @dependabot[bot]
- PR #45 — **docs: clarify TenantResolver precedence for custom resolvers** — @ahfoysal
- PR #46 — **chore(deps): bump quarkus.version from 3.37.1 to 3.37.2** — @dependabot[bot]
- PR #47 — **test(http): native integration tests for tenant resolution** — @lu1tr0n
- PR #51 — **chore(deps): bump quarkus.version from 3.37.2 to 3.37.3** — @dependabot[bot]
- PR #53 — **chore(deps-dev): bump com.mycila:license-maven-plugin from 5.0.0 to 5.1.1** — @dependabot[bot]
- PR #54 — **chore(deps): bump quarkus.version from 3.37.3 to 3.38.0** — @dependabot[bot]
- PR #55 — **chore: Configure Renovate** — @renovate[bot]
- PR #56 — **chore(deps): update actions/checkout action to v7** — @renovate[bot]
- PR #57 — **chore(deps): update actions/setup-java action to v5** — @renovate[bot]
- PR #59 — **chore: drop Dependabot in favor of Renovate** — @lu1tr0n
- PR #60 — **Add tenant-scoped background work API** — @mathias82
- PR #61 — **Propagate tenants through Kafka messaging** — @mathias82
- PR #69 — **fix: prevent external selection of the ORM bootstrap tenant** — @mathias82
- PR #71 — **chore(deps): update postgres docker tag to v17** — @renovate[bot]
- PR #72 — **Add reactive and async tenant propagation contract tests (#66)** — @lu1tr0n
- PR #73 — **Unify HTTP and ORM tenant resolution** — @mathias82
- PR #74 — **Align documentation with multitenancy runtime contracts** — @mathias82
- PR #75 — **Update postgres Docker tag to v18** — @renovate[bot]
- PR #77 — **Align Quarkus version with the shared LTS policy** — @lu1tr0n

---

## Acknowledgements

Maintainers: [@mathias82](https://github.com/mathias82), [@lu1tr0n](https://github.com/lu1tr0n).

Thanks to [@ahfoysal](https://github.com/ahfoysal) for the custom resolver precedence documentation contribution, and to everyone who reviewed, tested, documented, and hardened the tenant propagation and resolution behavior in this release.
