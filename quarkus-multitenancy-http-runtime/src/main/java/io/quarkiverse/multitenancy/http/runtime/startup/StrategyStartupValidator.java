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
package io.quarkiverse.multitenancy.http.runtime.startup;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkiverse.multitenancy.http.runtime.config.HttpTenantConfig;
import io.quarkiverse.multitenancy.http.runtime.config.HttpTenantStrategy;
import io.quarkus.runtime.StartupEvent;

/**
 * Fails fast at startup when {@code quarkus.multi-tenant.http.strategy} contains
 * an unknown built-in strategy name.
 *
 * <p>
 * Tenant resolution is security-sensitive: a misspelled strategy (for example
 * {@code heder,jwt}) was previously skipped silently, so the application started
 * with a different resolution chain than the operator intended. Surfacing the
 * typo at boot turns a hard-to-diagnose runtime gap into an immediate
 * configuration error.
 *
 * <p>
 * The check runs only when the extension is {@link HttpTenantConfig#enabled()
 * enabled}. Blank entries are ignored (a trailing comma is not a
 * misconfiguration) and an empty chain is allowed, since an application may rely
 * solely on custom {@code TenantResolver} beans plus the default tenant.
 */
@ApplicationScoped
public class StrategyStartupValidator {

    private static final String STRATEGY_PROPERTY = "quarkus.multi-tenant.http.strategy";

    private static final String SUPPORTED_VALUES = EnumSet.allOf(HttpTenantStrategy.class).stream()
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    @Inject
    HttpTenantConfig config;

    void onStart(@Observes StartupEvent event) {
        if (!config.enabled()) {
            return;
        }

        List<String> unknown = new ArrayList<>();
        boolean none = false;
        int namedStrategies = 0;
        for (String configured : config.strategy()) {
            if (configured == null || configured.isBlank()) {
                continue;
            }
            namedStrategies++;
            if ("none".equalsIgnoreCase(configured.trim())) {
                none = true;
                continue;
            }
            if (HttpTenantStrategy.fromConfigValue(configured).isEmpty()) {
                unknown.add(configured.trim());
            }
        }

        if (none && namedStrategies != 1) {
            throw new IllegalStateException("Tenant strategy 'none' must be used alone in " + STRATEGY_PROPERTY);
        }

        if (!unknown.isEmpty()) {
            throw new IllegalStateException(
                    "Unknown tenant strategy " + unknown + " configured in " + STRATEGY_PROPERTY
                            + ". Supported values are: " + SUPPORTED_VALUES + ".");
        }
    }
}
