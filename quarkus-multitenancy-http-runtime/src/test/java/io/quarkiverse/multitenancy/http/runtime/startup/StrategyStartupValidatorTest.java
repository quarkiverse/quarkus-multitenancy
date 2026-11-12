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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.multitenancy.http.runtime.config.HttpTenantConfig;

/**
 * Unit-tests the strategy-name check of {@link StrategyStartupValidator} without
 * spinning up a full Quarkus application per scenario, mirroring
 * {@code RejectStatusStartupValidatorTest}.
 */
class StrategyStartupValidatorTest {

    @Test
    void acceptsAllKnownStrategies() {
        assertDoesNotThrow(() -> validatorWith("header", "jwt", "cookie", "path").onStart(null));
    }

    @Test
    void acceptsMixedCaseAndWhitespace() {
        assertDoesNotThrow(() -> validatorWith("Header", "JWT", " cookie ").onStart(null));
    }

    @Test
    void ignoresBlankEntries() {
        // A trailing/embedded comma yields a blank entry, which is not a typo.
        assertDoesNotThrow(() -> validatorWith("header", "", "  ", "cookie").onStart(null));
    }

    @Test
    void allowsEmptyChain() {
        // No built-in strategies is valid: custom resolvers + default tenant.
        assertDoesNotThrow(() -> validatorWith().onStart(null));
    }

    @Test
    void acceptsCustomOnlyMode() {
        assertDoesNotThrow(() -> validatorWith("none").onStart(null));
        assertDoesNotThrow(() -> validatorWith(" NONE ").onStart(null));
    }

    @Test
    void rejectsNoneCombinedWithBuiltin() {
        assertThrows(IllegalStateException.class, () -> validatorWith("none", "header").onStart(null));
        assertThrows(IllegalStateException.class, () -> validatorWith("cookie", "none").onStart(null));
    }

    @Test
    void rejectsDuplicateNone() {
        assertThrows(IllegalStateException.class, () -> validatorWith("none", "none").onStart(null));
    }

    @Test
    void acceptsDuplicateStrategies() {
        assertDoesNotThrow(() -> validatorWith("header", "header").onStart(null));
    }

    @Test
    void rejectsSingleUnknownStrategy() {
        assertThrows(IllegalStateException.class, () -> validatorWith("heder", "jwt").onStart(null));
    }

    @Test
    void rejectsWhenExtensionEnabledWithUnknownStrategy() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> validatorWith("heder").onStart(null));

        String message = thrown.getMessage();
        assertTrue(message.contains("quarkus.multi-tenant.http.strategy"));
        assertTrue(message.contains("heder"));
        // The error must list the supported values to guide the operator.
        assertTrue(message.contains("header"));
        assertTrue(message.contains("jwt"));
        assertTrue(message.contains("cookie"));
        assertTrue(message.contains("path"));
        assertTrue(message.contains("none"));
    }

    @Test
    void reportsAllUnknownStrategiesAtOnce() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> validatorWith("heder", "jwt", "cooky").onStart(null));

        String message = thrown.getMessage();
        assertTrue(message.contains("heder"), "should name the first unknown value");
        assertTrue(message.contains("cooky"), "should name the second unknown value");
    }

    @Test
    void doesNothingWhenExtensionDisabled() {
        // An unknown strategy must not fail boot when the filter is off entirely.
        assertDoesNotThrow(() -> newValidator(fakeConfig(false, List.of("heder"))).onStart(null));
    }

    private static StrategyStartupValidator validatorWith(String... strategies) {
        return newValidator(fakeConfig(true, Arrays.asList(strategies)));
    }

    private static StrategyStartupValidator newValidator(HttpTenantConfig httpConfig) {
        StrategyStartupValidator v = new StrategyStartupValidator();
        v.config = httpConfig;
        return v;
    }

    private static HttpTenantConfig fakeConfig(boolean enabled, List<String> strategy) {
        return new HttpTenantConfig() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public List<String> strategy() {
                return strategy;
            }

            @Override
            public String headerName() {
                return "X-Tenant";
            }

            @Override
            public String jwtClaimName() {
                return "tenant";
            }

            @Override
            public String cookieName() {
                return "tenant_cookie";
            }

            @Override
            public String defaultTenant() {
                return "public";
            }

            @Override
            public String pathPattern() {
                return "^/t/([^/]+)(?:/|$)";
            }

            @Override
            public int pathGroup() {
                return 1;
            }

            @Override
            public JwtConfig jwt() {
                return () -> false;
            }

            @Override
            public TenantIdConfig tenantId() {
                return new TenantIdConfig() {
                    @Override
                    public boolean validationEnabled() {
                        return true;
                    }

                    @Override
                    public int maxLength() {
                        return 64;
                    }

                    @Override
                    public String pattern() {
                        return "[A-Za-z0-9_-]+";
                    }

                    @Override
                    public int rejectStatus() {
                        return 400;
                    }
                };
            }
        };
    }
}
