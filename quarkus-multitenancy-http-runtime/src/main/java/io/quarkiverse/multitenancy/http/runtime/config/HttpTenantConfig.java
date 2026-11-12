/*
 * Copyright the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
     * {@code path}, or {@code none} (custom resolvers only).
     *
     * <p>
     * The default is {@code header,cookie}. The {@code jwt} strategy is
     * opt-in — add it explicitly (for example {@code header,jwt,cookie}) —
     * because it requires a configured token verification source.
     * Set {@code none} as the sole entry to disable built-in strategies while
     * keeping custom resolvers and the default-tenant fallback active.
     * {@code none} cannot be combined with other strategies.
     * The {@code path} strategy is likewise opt-in, as it typically requires an
     * application-specific URL prefix (see {@link #pathPattern()}).
     */
    @WithDefault("header,cookie")
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
         * with {@link #rejectStatus()} (HTTP 400 by default) and never reaches
         * the {@code TenantContext}, closing length- and injection-based abuse
         * of downstream consumers such as logs, SQL parameters and ORM tenant
         * lookups.
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

        /**
         * HTTP status used to abort a request when a resolved tenant
         * identifier fails the {@link #maxLength()} or {@link #pattern()}
         * policy. Defaults to {@code 400} (Bad Request) because a malformed
         * identifier is bad <em>request input</em>, not an authentication
         * failure.
         *
         * <p>
         * This is deliberately distinct from the {@code 401} returned for an
         * authentication-related rejection ({@code TenantResolution.Rejected},
         * for example an invalid or untrusted bearer token), which is not
         * affected by this setting. Set it to {@code 401} to restore the
         * behaviour from before this property existed.
         *
         * <p>
         * Only a client-error ({@code 4xx}) status is supported: a value
         * outside the {@code 400}-{@code 499} range fails fast at startup.
         */
        @WithDefault("400")
        int rejectStatus();
    }
}
