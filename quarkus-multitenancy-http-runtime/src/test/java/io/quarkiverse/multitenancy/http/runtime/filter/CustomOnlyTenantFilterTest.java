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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Request-level coverage for custom-only tenant resolution (issue #95).
 * Reuses the custom resolver from TenantFilterDispatchTest.
 */
@QuarkusTest
@TestProfile(CustomOnlyTenantFilterTest.CustomOnlyProfile.class)
class CustomOnlyTenantFilterTest {

    @Test
    void customResolverWinsAndBuiltInHeaderIsIgnored() {
        given()
                .header("X-Custom-Tenant", "custom-tenant")
                .header("X-Tenant", "ignored-builtin")
                .when().get("/tenant")
                .then().statusCode(200)
                .body(is("Optional[custom-tenant]"));
    }

    @Test
    void notApplicableFallsBackToDefaultEvenWithBuiltInHeaderPresent() {
        given()
                .header("X-Tenant", "ignored-builtin")
                .when().get("/tenant")
                .then().statusCode(200)
                .body(is("Optional[public]"));
    }

    @Test
    void rejectedCustomNeverFallsBackToDefault() {
        given()
                .header("X-Bad-Tenant", "reject")
                .when().get("/tenant")
                .then().statusCode(401);
    }

    public static class CustomOnlyProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.multi-tenant.http.strategy", "none");
        }
    }
}
