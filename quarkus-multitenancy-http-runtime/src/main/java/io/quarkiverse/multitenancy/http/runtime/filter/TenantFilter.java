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
package io.quarkiverse.multitenancy.http.runtime.filter;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

import io.quarkiverse.multitenancy.core.runtime.api.TenantResolution;
import io.quarkiverse.multitenancy.core.runtime.api.TenantResolver;
import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import io.quarkiverse.multitenancy.core.runtime.core.TenantResolverRegistry;
import io.quarkiverse.multitenancy.http.runtime.config.HttpTenantConfig;
import io.quarkiverse.multitenancy.http.runtime.config.HttpTenantStrategy;
import io.quarkiverse.multitenancy.http.runtime.ctx.HttpTenantResolutionContext;
import io.quarkiverse.multitenancy.http.runtime.validation.TenantIdValidator;

/**
 * HTTP tenant filter that resolves the current tenant using a strategy chain
 * driven by {@link HttpTenantConfig#strategy()}.
 *
 * <p>
 * Dispatch order:
 * <ol>
 * <li>User-defined (custom) {@link TenantResolver} beans — anything whose
 * {@link TenantResolver#name()} is blank or does not match a known
 * {@link HttpTenantStrategy}. Custom resolvers express explicit user
 * intent and run first, ordered by descending {@link Priority} and then by
 * implementation class name.</li>
 * <li>Built-in resolvers in the order declared by
 * {@link HttpTenantConfig#strategy()}.</li>
 * </ol>
 *
 * <p>
 * Each invocation produces a {@link TenantResolution} outcome:
 * <ul>
 * <li>{@link TenantResolution.Resolved} — validates the identifier against the
 * tenant-id policy, then sets the tenant context and stops the chain. A
 * syntactically invalid identifier aborts with the configured
 * {@link HttpTenantConfig.TenantIdConfig#rejectStatus()} (HTTP 400 by
 * default) — malformed request input, not an authentication failure.</li>
 * <li>{@link TenantResolution.NotApplicable} — the dispatcher tries the next
 * resolver/strategy.</li>
 * <li>{@link TenantResolution.Rejected} — aborts the request with 401. The
 * filter must <strong>not</strong> fall back to
 * {@link HttpTenantConfig#defaultTenant()}, since a present-but-invalid
 * credential cannot silently downgrade to the default tenant.</li>
 * </ul>
 *
 * <p>
 * If every resolver in the chain returns {@code NotApplicable}, the filter
 * falls back to {@link HttpTenantConfig#defaultTenant()}.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class TenantFilter implements ContainerRequestFilter {

    private static final Logger logger = Logger.getLogger(TenantFilter.class);

    private static final Set<String> BUILT_IN_NAMES = EnumSet.allOf(HttpTenantStrategy.class).stream()
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());

    @Inject
    TenantResolverRegistry resolverRegistry;

    @Inject
    TenantContext tenantContext;

    @Inject
    HttpTenantConfig config;

    @Inject
    TenantIdValidator tenantIdValidator;

    @Override
    public void filter(ContainerRequestContext requestContext) {

        if (!config.enabled()) {
            return;
        }

        logger.debugf("Resolving tenant for incoming %s request", requestContext.getMethod());

        HttpTenantResolutionContext ctx = new HttpTenantResolutionContext(requestContext);
        List<TenantResolver> resolvers = resolverRegistry.orderedResolvers();

        TenantResolution outcome = runCustomResolvers(ctx, resolvers);

        if (outcome instanceof TenantResolution.NotApplicable) {
            outcome = runConfiguredBuiltins(ctx, resolvers);
        }

        if (outcome instanceof TenantResolution.Resolved resolved) {
            Optional<String> rejection = tenantIdValidator.validate(resolved.tenantId());
            if (rejection.isPresent()) {
                int rejectStatus = config.tenantId().rejectStatus();
                logger.warnf("Rejecting resolved tenant id with status %d: %s", rejectStatus, rejection.get());
                requestContext.abortWith(Response.status(rejectStatus).build());
                return;
            }
            tenantContext.setTenantId(resolved.tenantId());
            logger.debugf("Tenant resolved: '%s'", TenantIdValidator.sanitizeForLog(resolved.tenantId()));
            return;
        }

        if (outcome instanceof TenantResolution.Rejected) {
            logger.debug("Tenant resolution rejected; returning HTTP 401");
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED).build());
            return;
        }

        // NotApplicable across the whole chain → fall back to the default tenant.
        String defaultTenant = config.defaultTenant();
        tenantContext.setTenantId(defaultTenant);
        logger.debugf("No resolver matched; falling back to default tenant '%s'", defaultTenant);
    }

    private TenantResolution runCustomResolvers(HttpTenantResolutionContext ctx, List<TenantResolver> resolvers) {
        for (TenantResolver r : resolvers) {
            if (isCustom(r)) {
                TenantResolution result = resolveWithDiagnostics(r, ctx);
                if (!(result instanceof TenantResolution.NotApplicable)) {
                    return result;
                }
            }
        }
        return TenantResolution.notApplicable();
    }

    private TenantResolution runConfiguredBuiltins(HttpTenantResolutionContext ctx, List<TenantResolver> resolvers) {
        for (String configured : config.strategy()) {
            // Explicit custom-only mode: do not invoke built-in resolvers.
            if ("none".equalsIgnoreCase(configured.trim())) {
                continue;
            }
            HttpTenantStrategy strategy = parseStrategy(configured);
            if (strategy == null) {
                continue;
            }
            Optional<TenantResolver> match = findBuiltin(strategy, resolvers);
            if (match.isEmpty()) {
                logger.debugf("No built-in resolver registered for strategy '%s'", strategy);
                continue;
            }
            TenantResolution result = resolveWithDiagnostics(match.get(), ctx);
            if (!(result instanceof TenantResolution.NotApplicable)) {
                return result;
            }
        }
        return TenantResolution.notApplicable();
    }

    private HttpTenantStrategy parseStrategy(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Optional<HttpTenantStrategy> strategy = HttpTenantStrategy.fromConfigValue(configured);
        if (strategy.isEmpty()) {
            // Unreachable in a normally booted app: StrategyStartupValidator
            // fails fast on unknown names. Kept as defensive filter hygiene for
            // config paths that bypass startup validation, hence DEBUG.
            logger.debugf("Unknown tenant strategy '%s' in quarkus.multi-tenant.http.strategy "
                    + "(expected one of %s); skipping.", configured, BUILT_IN_NAMES);
            return null;
        }
        return strategy.get();
    }

    private Optional<TenantResolver> findBuiltin(HttpTenantStrategy strategy, List<TenantResolver> resolvers) {
        String target = strategy.name();
        for (TenantResolver r : resolvers) {
            if (target.equals(r.name())) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    private TenantResolution resolveWithDiagnostics(TenantResolver resolver, HttpTenantResolutionContext ctx) {
        TenantResolution result = resolver.resolve(ctx);
        if (result instanceof TenantResolution.Resolved) {
            logger.debugf("Tenant resolved by %s", resolver.getClass().getSimpleName());
        } else if (result instanceof TenantResolution.Rejected) {
            logger.debugf("Tenant resolution rejected by %s", resolver.getClass().getSimpleName());
        }
        return result;
    }

    private boolean isCustom(TenantResolver r) {
        String n = r.name();
        return n == null || n.isBlank() || !BUILT_IN_NAMES.contains(n);
    }
}
