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
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import io.quarkiverse.multitenancy.orm.deployment.testmodel.audit.AuditEntity;
import io.quarkiverse.multitenancy.orm.deployment.testmodel.inventory.InventoryEntity;
import io.quarkiverse.multitenancy.orm.deployment.testmodel.users.UserEntity;
import io.quarkiverse.multitenancy.orm.runtime.adapter.OrmTenantResolverAdapter;
import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import io.quarkus.test.QuarkusExtensionTest;

class NamedPersistenceUnitTenantResolverTest {

    @RegisterExtension
    static final QuarkusExtensionTest UNIT_TEST = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(UserEntity.class, InventoryEntity.class, AuditEntity.class)
                    .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml"))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.multi-tenant.orm.named-persistence-units", "users,inventory")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".packages",
                    "io.quarkiverse.multitenancy.orm.deployment.testmodel.users")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".datasource", "users")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".multitenant", "DATABASE")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".active", "false")
            .overrideConfigKey("quarkus.datasource.\"users\".db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.\"users\".jdbc.url", "jdbc:h2:mem:users")
            .overrideConfigKey("quarkus.hibernate-orm.\"inventory\".packages",
                    "io.quarkiverse.multitenancy.orm.deployment.testmodel.inventory")
            .overrideConfigKey("quarkus.hibernate-orm.\"inventory\".datasource", "inventory")
            .overrideConfigKey("quarkus.hibernate-orm.\"inventory\".multitenant", "DATABASE")
            .overrideConfigKey("quarkus.hibernate-orm.\"inventory\".active", "false")
            .overrideConfigKey("quarkus.datasource.\"inventory\".db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.\"inventory\".jdbc.url", "jdbc:h2:mem:inventory")
            .overrideConfigKey("quarkus.hibernate-orm.\"audit\".packages",
                    "io.quarkiverse.multitenancy.orm.deployment.testmodel.audit")
            .overrideConfigKey("quarkus.hibernate-orm.\"audit\".datasource", "audit")
            .overrideConfigKey("quarkus.hibernate-orm.\"audit\".active", "false")
            .overrideConfigKey("quarkus.datasource.\"audit\".db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.\"audit\".jdbc.url", "jdbc:h2:mem:audit");

    @Inject
    TenantContext tenantContext;

    @Inject
    @PersistenceUnitExtension("users")
    TenantResolver usersResolver;

    @Inject
    @PersistenceUnitExtension("inventory")
    TenantResolver inventoryResolver;

    @Inject
    @PersistenceUnitExtension("audit")
    Instance<TenantResolver> auditResolver;

    @Test
    @ActivateRequestContext
    void selectedNamedPersistenceUnitsUseTheSharedTenantContext() {
        tenantContext.setTenantId("acme");

        assertEquals("acme", usersResolver.resolveTenantId());
        assertEquals("acme", inventoryResolver.resolveTenantId());
        assertEquals(OrmTenantResolverAdapter.BOOTSTRAP_TENANT, usersResolver.getDefaultTenantId());
        assertEquals(OrmTenantResolverAdapter.BOOTSTRAP_TENANT, inventoryResolver.getDefaultTenantId());
    }

    @Test
    void unselectedNonMultitenantPersistenceUnitHasNoAdapter() {
        assertTrue(auditResolver.isUnsatisfied());
    }
}
