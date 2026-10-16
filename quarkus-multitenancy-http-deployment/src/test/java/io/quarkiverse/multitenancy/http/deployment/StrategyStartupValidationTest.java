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
package io.quarkiverse.multitenancy.http.deployment;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;

/**
 * End-to-end check that an unknown strategy name aborts the whole application at
 * boot, exercising real CDI discovery and {@code StartupEvent} dispatch of
 * {@code StrategyStartupValidator} (not just its logic in isolation).
 */
class StrategyStartupValidationTest {

    @RegisterExtension
    static final QuarkusExtensionTest UNIT_TEST = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClasses(MultiTenantHttpProcessor.class))
            .overrideConfigKey("quarkus.multi-tenant.http.strategy", "heder,jwt")
            .assertException(throwable -> {
                Throwable root = throwable;
                while (root.getCause() != null && root.getCause() != root) {
                    root = root.getCause();
                }
                String message = root.getMessage();
                assertTrue(message != null
                        && message.contains("quarkus.multi-tenant.http.strategy")
                        && message.contains("heder"),
                        "Expected an unknown-strategy configuration error, but got: " + message);
            });

    @Test
    void shouldNotBoot() {
        fail("Application should have failed to start with an unknown strategy name");
    }
}
