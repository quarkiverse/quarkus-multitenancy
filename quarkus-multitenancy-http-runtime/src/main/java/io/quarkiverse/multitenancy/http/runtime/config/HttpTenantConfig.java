package io.quarkiverse.multitenancy.http.runtime.config;

import java.util.List;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Runtime configuration for the Quarkus multitenancy HTTP module.
 */
@ConfigMapping(prefix = "quarkus.multi-tenant.http")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface HttpTenantConfig {

    /**
     * Master switch for the HTTP tenant filter. When {@code false} the
     * extension stays on the classpath without resolving a tenant per
     * request.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Ordered list of built-in resolution strategies the HTTP filter
     * will try. Custom (user-defined) {@code TenantResolver} beans run
     * before this chain.
     *
     * <p>
     * Valid values: {@code header}, {@code jwt}, {@code cookie},
     * {@code path}.
     *
     * <p>
     * The default omits {@code path} because path-based tenant
     * resolution typically requires an application-specific URL prefix
     * and is opt-in (see {@link #pathPattern()}).
     */
    @WithDefault("header,jwt,cookie")
    List<String> strategy();

    /**
     * Name of the HTTP header read by {@code HeaderTenantResolver}.
     */
    @WithDefault("X-Tenant")
    String headerName();

    /**
     * Name of the JWT claim read by {@code JwtTenantResolver}.
     */
    @WithDefault("tenant")
    String jwtClaimName();

    /**
     * Name of the HTTP cookie read by {@code CookieTenantResolver}.
     */
    @WithDefault("tenant_cookie")
    String cookieName();

    /**
     * Identifier returned when no resolver yields a tenant.
     */
    @WithDefault("public")
    String defaultTenant();

    /**
     * Regular expression applied to the request path by
     * {@code PathTenantResolver}. The tenant identifier is taken from
     * the capturing group at {@link #pathGroup()}. Only consulted when
     * {@code path} appears in {@link #strategy()}.
     */
    @WithDefault("^/t/([^/]+)(?:/|$)")
    String pathPattern();

    /**
     * Capturing group used by {@link #pathPattern()}.
     */
    @WithDefault("1")
    int pathGroup();

    /**
     * JWT-strategy specific configuration.
     */
    JwtConfig jwt();

    /**
     * Validation applied to a tenant identifier once a resolver has produced
     * it, before it is published to the {@code TenantContext}. The same policy
     * covers built-in and custom resolvers, since every {@code Resolved}
     * outcome passes through the HTTP filter.
     */
    TenantIdConfig tenantId();

    /**
     * Settings consulted by the {@code jwt} resolution strategy and its
     * startup-time validator.
     */
    interface JwtConfig {

        /**
         * Opt out of the {@code JwtStrategyStartupValidator}'s fail-fast
         * check. Useful when {@code JsonWebToken} is produced by a custom
         * bean rather than the standard SmallRye JWT / Quarkus OIDC paths.
         * Setting this to {@code true} leaves the operator responsible for
         * ensuring an authenticated identity reaches the filter.
         */
        @WithDefault("false")
        boolean skipStartupCheck();
    }

    /**
     * Length and character-set policy for a resolved tenant identifier.
     */
    interface TenantIdConfig {

        /**
         * Whether a resolved tenant identifier is validated against
         * {@link #maxLength()} and {@link #pattern()}. When {@code true}
         * (the default), an identifier that violates the policy is rejected
         * with HTTP 401 and never reaches the {@code TenantContext}, closing
         * length- and injection-based abuse of downstream consumers such as
         * logs, SQL parameters and ORM tenant lookups.
         */
        @WithDefault("true")
        boolean validationEnabled();

        /**
         * Maximum number of characters allowed in a resolved tenant
         * identifier. An identifier longer than this is rejected.
         */
        @WithDefault("64")
        int maxLength();

        /**
         * Regular expression a resolved tenant identifier must match in full.
         * The default allows ASCII letters, digits, hyphen and underscore,
         * which is safe to propagate into logs, SQL parameters and ORM tenant
         * lookups. Widen it deliberately if your tenant identifiers use a
         * richer character set.
         */
        @WithDefault("[A-Za-z0-9_-]+")
        String pattern();
    }
}
