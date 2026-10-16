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
package io.quarkiverse.multitenancy.orm.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import io.quarkiverse.multitenancy.orm.deployment.testmodel.inventory.InventoryEntity;
import io.quarkiverse.multitenancy.orm.deployment.testmodel.users.UserEntity;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import io.quarkus.test.QuarkusExtensionTest;

class ApplicationTenantResolverOverrideTest {

    @RegisterExtension
    static final QuarkusExtensionTest UNIT_TEST = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(UserEntity.class, InventoryEntity.class,
                    ApplicationTenantResolver.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.multi-tenant.orm.named-persistence-units", "users,inventory")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".packages",
                    "io.quarkiverse.multitenancy.orm.deployment.testmodel.users")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".datasource", "users")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".multitenant", "DATABASE")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".active", "false")
            .overrideConfigKey("quarkus.datasource.\"users\".db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.\"users\".jdbc.url", "jdbc:h2:mem:users-override")
            .overrideConfigKey("quarkus.hibernate-orm.\"inventory\".packages",
                    "io.quarkiverse.multitenancy.orm.deployment.testmodel.inventory")
            .overrideConfigKey("quarkus.hibernate-orm.\"inventory\".datasource", "inventory")
            .overrideConfigKey("quarkus.hibernate-orm.\"inventory\".multitenant", "DATABASE")
            .overrideConfigKey("quarkus.hibernate-orm.\"inventory\".active", "false")
            .overrideConfigKey("quarkus.datasource.\"inventory\".db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.\"inventory\".jdbc.url", "jdbc:h2:mem:inventory-default");

    @Inject
    TenantContext tenantContext;

    @Inject
    @PersistenceUnitExtension("users")
    TenantResolver applicationResolver;

    @Inject
    @PersistenceUnitExtension("inventory")
    TenantResolver defaultResolver;

    @Test
    @ActivateRequestContext
    void applicationResolverOverridesTheDefaultAdapterWithoutAmbiguity() {
        tenantContext.setTenantId("acme");

        assertEquals("application", applicationResolver.resolveTenantId());
        assertEquals("acme", defaultResolver.resolveTenantId());
    }

    @ApplicationScoped
    @PersistenceUnitExtension("users")
    public static class ApplicationTenantResolver implements TenantResolver {

        @Override
        public String getDefaultTenantId() {
            return "application";
        }

        @Override
        public String resolveTenantId() {
            return "application";
        }
    }
}
