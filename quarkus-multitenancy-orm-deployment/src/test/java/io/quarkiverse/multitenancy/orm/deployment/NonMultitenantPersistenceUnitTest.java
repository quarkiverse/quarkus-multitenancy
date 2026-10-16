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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.multitenancy.orm.deployment.testmodel.users.UserEntity;
import io.quarkus.test.QuarkusExtensionTest;

class NonMultitenantPersistenceUnitTest {

    @RegisterExtension
    static final QuarkusExtensionTest UNIT_TEST = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClass(UserEntity.class))
            .overrideConfigKey("quarkus.datasource.devservices.enabled", "false")
            .overrideConfigKey("quarkus.multi-tenant.orm.named-persistence-units", "users")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".packages",
                    "io.quarkiverse.multitenancy.orm.deployment.testmodel.users")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".datasource", "users")
            .overrideConfigKey("quarkus.hibernate-orm.\"users\".active", "false")
            .overrideConfigKey("quarkus.datasource.\"users\".db-kind", "h2")
            .overrideConfigKey("quarkus.datasource.\"users\".jdbc.url", "jdbc:h2:mem:users-invalid")
            .assertException(throwable -> {
                String messages = allMessages(throwable);
                assertTrue(messages.contains("quarkus.multi-tenant.orm.named-persistence-units is invalid"));
                assertTrue(messages.contains("Hibernate ORM persistence unit 'users' is not configured for multitenancy"));
                assertTrue(messages.contains("quarkus.hibernate-orm.\"users\".multitenant"));
            });

    @Test
    void shouldNotBoot() {
        fail("Application should have failed to start for a non-multitenant persistence unit");
    }

    private static String allMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null && current.getCause() != current; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }
}
