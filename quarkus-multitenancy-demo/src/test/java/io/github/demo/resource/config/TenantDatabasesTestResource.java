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
package io.github.demo.resource.config;

import java.util.Map;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class TenantDatabasesTestResource implements QuarkusTestResourceLifecycleManager {

    // Use the Docker Official Image mirror to avoid Docker Hub's anonymous pull limit in CI.
    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse("public.ecr.aws/docker/library/postgres:18")
            .asCompatibleSubstituteFor("postgres");

    private PostgreSQLContainer<?> tenant1;
    private PostgreSQLContainer<?> tenant2;

    @Override
    public Map<String, String> start() {
        tenant1 = postgres("tenant1", "user1", "pass1", "db/tenant1.sql");
        tenant2 = postgres("tenant2", "user2", "pass2", "db/tenant2.sql");
        tenant1.start();
        tenant2.start();

        return Map.of(
                "quarkus.datasource.__bootstrap.jdbc.url", tenant1.getJdbcUrl(),
                "quarkus.datasource.__bootstrap.username", tenant1.getUsername(),
                "quarkus.datasource.__bootstrap.password", tenant1.getPassword(),
                "demo.tenant-catalog.tenants.tenant1.jdbc-url", tenant1.getJdbcUrl(),
                "demo.tenant-catalog.tenants.tenant1.username", tenant1.getUsername(),
                "demo.tenant-catalog.tenants.tenant1.password", tenant1.getPassword(),
                "demo.tenant-catalog.tenants.tenant2.jdbc-url", tenant2.getJdbcUrl(),
                "demo.tenant-catalog.tenants.tenant2.username", tenant2.getUsername(),
                "demo.tenant-catalog.tenants.tenant2.password", tenant2.getPassword());
    }

    @Override
    public void stop() {
        if (tenant1 != null) {
            tenant1.stop();
        }
        if (tenant2 != null) {
            tenant2.stop();
        }
    }

    private PostgreSQLContainer<?> postgres(String database, String username, String password, String initScript) {
        return new PostgreSQLContainer<>(POSTGRES_IMAGE)
                .withDatabaseName(database)
                .withUsername(username)
                .withPassword(password)
                .withInitScript(initScript);
    }
}
