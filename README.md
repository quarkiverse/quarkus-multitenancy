# 🧩 Quarkus Multitenancy Extension

[![Build](https://github.com/quarkiverse/quarkus-multitenancy/actions/workflows/build.yml/badge.svg)](https://github.com/quarkiverse/quarkus-multitenancy/actions/workflows/build.yml)
[![Documentation](https://img.shields.io/badge/docs-Quarkiverse-blue)](https://docs.quarkiverse.io/quarkus-multitenancy/dev/)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
![Java](https://img.shields.io/badge/Java-21%2B-blue)
![Quarkus](https://img.shields.io/badge/Quarkus-3.x-red)
![Status](https://img.shields.io/badge/status-preview-orange)

> A modular, decoupled multitenancy extension for Quarkus  
> supporting HTTP tenant resolution, ORM integration, and future integrations.

Quarkus Multitenancy is a Quarkiverse extension that provides a generic tenant resolution API and reusable building blocks for Quarkus applications.

It abstracts tenant identification logic away from any specific technology and exposes a consistent `TenantContext` that can be injected by application code or reused by integrations such as HTTP and ORM.

## Why this exists

- Quarkus already provides powerful building blocks such as OIDC multitenancy and Hibernate ORM multitenancy.
- Applications still often need a consistent way to resolve and propagate the current tenant.
- This extension provides a reusable tenant resolution layer across HTTP, ORM, and custom integrations.
- It is now maintained as a Quarkiverse extension.

---

💡 Designed for REST microservices and backend modules, it can provide tenant resolution for HTTP requests, persistence, cache, messaging, or custom application layers.

## 📌 About This Project

**Quarkus Multitenancy** is an extension designed to standardize and simplify tenant resolution for Quarkus services. It provides a decoupled multi-layer architecture.

- A core runtime module that defines `TenantResolver`, `TenantContext`, and tenant resolution contracts.
- Independent HTTP, ORM, and deployment layers built on top of the core.
- Built-in support for tenant resolution from headers, cookies, JWT claims, and request paths.

This makes the extension modular, lightweight, and framework-friendly, so you can plug tenant resolution into HTTP requests, ORM integrations, or custom application code.

- Consistent tenant identification per request
- Pluggable resolvers: header, cookie, JWT claim, path, and custom resolvers
- Minimal boilerplate code
- Integration point for datasources, caches, identity providers, and other tenant-aware components
- Quarkiverse migration completed
- First preview release preparation in progress

---

## 📚 Modules

| Module | Description | Docs |
|--------|-------------|------|
| 🧠 **Core Runtime** | Defines `TenantContext`, `TenantResolver`, and tenant resolution contracts | [Read more →](quarkus-multitenancy-core-runtime/README.md) |
| ⚙️ **Core Deployment** | Build-time Quarkus integration for core | [Read more →](quarkus-multitenancy-core-deployment/README.md) |
| 🌐 **HTTP Runtime** | Resolves tenants from header, cookie, JWT claim, or path | [Read more →](quarkus-multitenancy-http-runtime/README.md) |
| 🧩 **HTTP Deployment** | Registers HTTP tenant resolution support | [Read more →](quarkus-multitenancy-http-deployment/README.md) |
| 🧱 **ORM Runtime** | Integrates tenant context with Hibernate ORM multitenancy use cases | [Read more →](quarkus-multitenancy-orm-runtime/README.md) |
| ⚙️ **ORM Deployment** | Quarkus feature registration for ORM integration | [Read more →](quarkus-multitenancy-orm-deployment/README.md) |
| 🧪 **Demo App** | PostgreSQL multi-tenant REST demo | [Read more →](quarkus-multitenancy-demo/README.md) |

---

# 🧠 Quarkus Multitenancy Core Runtime

The **core foundation** of the Quarkus Multitenancy extension.

It defines the base APIs used to resolve and propagate tenants across layers, from HTTP requests to ORM and custom application code.

---

## 🚀 What It Does

This module provides:

- The **`TenantContext`** – a request-scoped CDI bean exposing the active tenant.
- The **`TenantResolver`** – an interface for resolving tenant identifiers dynamically.
- The **`TenantResolution`** contract – a three-state result model: `Resolved`, `NotApplicable`, and `Rejected`.
- The **`CompositeTenantResolver`** – allows multiple resolvers such as header, JWT, cookie, or path to cooperate.

---

## 🧩 Tenant-Aware Isolation

Using this module together with the HTTP and ORM runtimes, each incoming request can be associated with a resolved tenant identifier.

✅ Each request can carry a tenant identifier, for example `X-Tenant: tenant1`.  
✅ The active tenant is exposed through `TenantContext`.  
✅ The ORM runtime can use the resolved tenant to route persistence operations.  
✅ Actual database isolation depends on how the application configures Hibernate ORM, datasources, schemas, or databases.

For example:

| Request | Header | Tenant Resolved |
|----------|---------|----------------|
| `GET /api/users` | `X-Tenant: tenant1` | `tenant1` |
| `GET /api/users` | `X-Tenant: tenant2` | `tenant2` |

This means the extension provides the tenant resolution layer. The application remains responsible for configuring the actual persistence isolation model.

---

## ⚙️ Required Dependencies

To enable HTTP tenant resolution:

```xml
<dependency>
    <groupId>io.quarkiverse.multitenancy</groupId>
    <artifactId>quarkus-multitenancy-http</artifactId>
    <version>${quarkus-multitenancy.version}</version>
</dependency>
```

To enable ORM integration:

```xml
<dependency>
    <groupId>io.quarkiverse.multitenancy</groupId>
    <artifactId>quarkus-multitenancy-orm</artifactId>
    <version>${quarkus-multitenancy.version}</version>
</dependency>
```

Replace `${quarkus-multitenancy.version}` with the latest released version once the first preview release is published.

---

## 💡 Example Usage

```java
import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/tenant")
public class TenantResource {

    @Inject
    TenantContext tenantContext;

    @GET
    public String getTenant() {
        return tenantContext.getTenantId().orElse("NO TENANT FOUND");
    }
}
```

When you send:

```bash
curl -H "X-Tenant: tenant1" http://localhost:8080/tenant
```

Output:

```text
tenant1
```

| Layer | Module | Responsibility |
|------|--------|----------------|
| **HTTP Runtime** | `quarkus-multitenancy-http` | Resolves tenant per HTTP request |
| **ORM Runtime** | `quarkus-multitenancy-orm` | Connects ORM layer to tenant context |

Together, they provide tenant-aware request handling and persistence integration in Quarkus.

---

## 🔐 JWT Tenant Resolution

The JWT strategy resolves the tenant from a claim in a **verified bearer token**.

Applications must configure SmallRye JWT or Quarkus OIDC before enabling the `jwt` strategy.

Example:

```properties
quarkus.multi-tenant.http.strategy=jwt
quarkus.multi-tenant.http.jwt-claim-name=tenant

mp.jwt.verify.publickey.location=publicKey.pem
mp.jwt.verify.publickey.algorithm=RS256
mp.jwt.verify.issuer=https://my-issuer.example.com
```

OIDC can also be used as the verification source:

```properties
quarkus.oidc.auth-server-url=https://issuer.example.com
```

Named OIDC tenants are also supported:

```properties
quarkus.oidc.customer.auth-server-url=https://customer-issuer.example.com
```

If a bearer token is present but cannot be verified, or if the configured tenant claim is missing, non-string, or blank, the request is rejected with HTTP 401 and does not fall back to the default tenant.

---

## 🚦 Resolution Outcomes

Tenant resolution uses a three-state result contract:

| Outcome | Meaning |
|---------|---------|
| `Resolved` | A resolver successfully resolved a tenant identifier |
| `NotApplicable` | The resolver had no input to process, so the next strategy may be tried |
| `Rejected` | The resolver found invalid input and the request must be rejected |

This distinction is important for security.

For example, a missing bearer token can be `NotApplicable`, but a malformed or unverifiable bearer token is `Rejected` and must not silently fall back to `defaultTenant`.

---

## 🚀 Quick Start

```bash
mvn clean install
cd quarkus-multitenancy-demo
mvn quarkus:dev
```

To test the demo, import the `demo.postman_collection.json` file into Postman.

---

## 🧭 Architecture Overview

```text
[HTTP Request]
     ↓
[HTTP TenantResolver] (header/JWT/cookie/path)
     ↓
[TenantContext] (request-scoped)
     ↓
[ORM / Application / Custom Integration]
```

👉 See the `quarkus-multitenancy-demo` README for full setup with Docker, PostgreSQL, Postman, and sample tenants.

---

## 📖 Documentation

Full documentation is available at:

```text
https://docs.quarkiverse.io/quarkus-multitenancy/dev/
```

---

## 📄 License

This project is licensed under the Apache License 2.0.
