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
package io.quarkiverse.multitenancy.messaging.kafka.deployment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.interceptor.KafkaTenantOutgoingInterceptor;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.validation.KafkaTenantValidator;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;

class KafkaOutgoingTenantPolicyTest {

    private static final String HEADER = "tenant-key";

    @RegisterExtension
    static final QuarkusExtensionTest UNIT_TEST = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClass(RejectDotTenantValidator.class))
            .overrideConfigKey("quarkus.kafka.devservices.enabled", "false")
            .overrideConfigKey("quarkus.multi-tenant.messaging.kafka.header-name", HEADER);

    @Inject
    TenantContext tenantContext;

    @Inject
    @Any
    KafkaTenantOutgoingInterceptor outgoing;

    @Test
    @ActivateRequestContext
    void shouldPropagateLocalTenantWithoutApplyingIncomingTrustBoundaryPolicy() {
        tenantContext.setTenantId("acme.co");

        Message<?> intercepted = outgoing.beforeMessageSend(Message.of("payload"));
        String propagated = new String(intercepted.getMetadata(OutgoingKafkaRecordMetadata.class)
                .orElseThrow()
                .getHeaders()
                .lastHeader(HEADER)
                .value(), UTF_8);

        assertEquals("acme.co", propagated);
    }

    @ApplicationScoped
    public static class RejectDotTenantValidator implements KafkaTenantValidator {
        @Override
        public Optional<String> validate(String tenantId) {
            return tenantId.contains(".") ? Optional.of("contains a dot") : Optional.empty();
        }
    }
}
