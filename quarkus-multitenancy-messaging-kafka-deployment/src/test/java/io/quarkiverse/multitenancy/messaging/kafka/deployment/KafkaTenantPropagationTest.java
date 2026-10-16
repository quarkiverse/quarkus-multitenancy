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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.exception.KafkaTenantPropagationException;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.interceptor.KafkaTenantIncomingInterceptor;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.interceptor.KafkaTenantOutgoingInterceptor;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.validation.KafkaTenantValidator;
import io.quarkus.test.QuarkusExtensionTest;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;

class KafkaTenantPropagationTest {

    private static final String HEADER = "tenant-key";

    @RegisterExtension
    static final QuarkusExtensionTest UNIT_TEST = new QuarkusExtensionTest()
            .withApplicationRoot(jar -> jar.addClass(UnknownTenantValidator.class))
            .overrideConfigKey("quarkus.kafka.devservices.enabled", "false")
            .overrideConfigKey("quarkus.multi-tenant.messaging.kafka.header-name", HEADER);

    @Inject
    TenantContext tenantContext;

    @Inject
    @Any
    KafkaTenantOutgoingInterceptor outgoing;

    @Inject
    @Any
    KafkaTenantIncomingInterceptor incoming;

    @AfterEach
    @ActivateRequestContext
    void clearTenant() {
        tenantContext.clear();
    }

    @Test
    @ActivateRequestContext
    void shouldPropagateTenantRoundTripAndClearAfterAck() {
        tenantContext.setTenantId("acme-1");

        Message<?> stamped = outgoing.beforeMessageSend(Message.of("payload"));
        Headers outgoingHeaders = stamped.getMetadata(OutgoingKafkaRecordMetadata.class)
                .orElseThrow()
                .getHeaders();
        assertEquals("acme-1", new String(outgoingHeaders.lastHeader(HEADER).value(), UTF_8));

        tenantContext.clear();
        CompletableFuture<Void> connectorAck = new CompletableFuture<>();
        Message<?> received = incoming.afterMessageReceive(incomingMessage(outgoingHeaders, connectorAck));

        assertEquals("acme-1", tenantContext.getTenantId().orElseThrow());

        CompletableFuture<Void> acknowledgment = received.ack().toCompletableFuture();
        assertTrue(tenantContext.getTenantId().isEmpty());
        assertFalse(acknowledgment.isDone());

        connectorAck.complete(null);
        acknowledgment.join();
    }

    @Test
    @ActivateRequestContext
    void shouldRetainExplicitOutgoingTenantHeader() {
        tenantContext.setTenantId("context-tenant");
        Headers headers = new RecordHeaders().add(HEADER, "explicit-tenant".getBytes(UTF_8));
        OutgoingKafkaRecordMetadata<?> metadata = OutgoingKafkaRecordMetadata.builder()
                .withHeaders(headers)
                .build();
        Message<String> original = Message.of("payload").addMetadata(metadata);

        Message<?> intercepted = outgoing.beforeMessageSend(original);

        assertSame(original, intercepted);
        assertEquals("explicit-tenant", new String(intercepted.getMetadata(OutgoingKafkaRecordMetadata.class)
                .orElseThrow()
                .getHeaders()
                .lastHeader(HEADER)
                .value(), UTF_8));
    }

    @Test
    @ActivateRequestContext
    void shouldPreserveExistingKafkaMetadataWhenAddingTenantHeader() {
        tenantContext.setTenantId("context-tenant");
        Headers headers = new RecordHeaders().add("correlation-id", "123".getBytes(UTF_8));
        OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String> builder()
                .withTopic("custom-topic")
                .withKey("record-key")
                .withPartition(2)
                .withHeaders(headers)
                .build();

        Message<?> intercepted = outgoing.beforeMessageSend(Message.of("payload").addMetadata(metadata));
        OutgoingKafkaRecordMetadata<?> result = intercepted.getMetadata(OutgoingKafkaRecordMetadata.class)
                .orElseThrow();

        assertEquals("custom-topic", result.getTopic());
        assertEquals("record-key", result.getKey());
        assertEquals(2, result.getPartition());
        assertEquals("123", new String(result.getHeaders().lastHeader("correlation-id").value(), UTF_8));
        assertEquals("context-tenant", new String(result.getHeaders().lastHeader(HEADER).value(), UTF_8));
    }

    @Test
    @ActivateRequestContext
    void shouldLeaveOutgoingMessageUnchangedWhenNoTenantIsBound() {
        tenantContext.clear();
        Message<String> original = Message.of("payload");

        assertSame(original, outgoing.beforeMessageSend(original));
    }

    @Test
    @ActivateRequestContext
    void shouldExposeNoTenantForMissingHeaderAndRestorePreviousTenantAfterAck() {
        tenantContext.setTenantId("previous");
        Message<?> received = incoming.afterMessageReceive(incomingMessage(new RecordHeaders(),
                CompletableFuture.completedFuture(null)));

        assertTrue(tenantContext.getTenantId().isEmpty());

        received.ack().toCompletableFuture().join();
        assertEquals("previous", tenantContext.getTenantId().orElseThrow());
    }

    @Test
    @ActivateRequestContext
    void shouldRestorePreviousTenantAfterNack() {
        tenantContext.setTenantId("previous");
        Message<?> received = incoming.afterMessageReceive(incomingMessage(
                new RecordHeaders().add(HEADER, "message-tenant".getBytes(UTF_8)),
                CompletableFuture.completedFuture(null)));

        assertEquals("message-tenant", tenantContext.getTenantId().orElseThrow());

        received.nack(new IllegalStateException("boom")).toCompletableFuture().join();
        assertEquals("previous", tenantContext.getTenantId().orElseThrow());
    }

    @Test
    @ActivateRequestContext
    void shouldRejectDisallowedTenantId() {
        Message<?> message = incomingMessage(
                new RecordHeaders().add(HEADER, "acme;DROP".getBytes(UTF_8)),
                CompletableFuture.completedFuture(null));

        KafkaTenantPropagationException failure = assertThrows(KafkaTenantPropagationException.class,
                () -> incoming.afterMessageReceive(message));
        assertTrue(failure.getMessage().contains("does not match"));
        assertTrue(tenantContext.getTenantId().isEmpty());
    }

    @Test
    @ActivateRequestContext
    void shouldRejectMalformedUtf8TenantId() {
        Message<?> message = incomingMessage(
                new RecordHeaders().add(new RecordHeader(HEADER, new byte[] { (byte) 0xC3, 0x28 })),
                CompletableFuture.completedFuture(null));

        KafkaTenantPropagationException failure = assertThrows(KafkaTenantPropagationException.class,
                () -> incoming.afterMessageReceive(message));
        assertTrue(failure.getMessage().contains("not valid UTF-8"));
    }

    @Test
    @ActivateRequestContext
    void shouldRejectBlankTenantId() {
        Message<?> message = incomingMessage(
                new RecordHeaders().add(HEADER, "   ".getBytes(UTF_8)),
                CompletableFuture.completedFuture(null));

        KafkaTenantPropagationException failure = assertThrows(KafkaTenantPropagationException.class,
                () -> incoming.afterMessageReceive(message));
        assertTrue(failure.getMessage().contains("is blank"));
    }

    @Test
    @ActivateRequestContext
    void shouldRejectUnknownTenantThroughApplicationValidator() {
        Message<?> message = incomingMessage(
                new RecordHeaders().add(HEADER, "unknown".getBytes(UTF_8)),
                CompletableFuture.completedFuture(null));

        KafkaTenantPropagationException failure = assertThrows(KafkaTenantPropagationException.class,
                () -> incoming.afterMessageReceive(message));
        assertTrue(failure.getMessage().contains("not registered"));
    }

    private static Message<String> incomingMessage(Headers headers, CompletableFuture<Void> acknowledgment) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("tenant-in", 0, 0L, "key", "payload");
        headers.forEach(record.headers()::add);
        IncomingKafkaRecordMetadata<String, String> metadata = new IncomingKafkaRecordMetadata<>(record, "tenant-in");
        return Message.of("payload", () -> acknowledgment).addMetadata(metadata);
    }

    @ApplicationScoped
    public static class UnknownTenantValidator implements KafkaTenantValidator {
        @Override
        public Optional<String> validate(String tenantId) {
            return "unknown".equals(tenantId)
                    ? Optional.of("tenant is not registered")
                    : Optional.empty();
        }
    }
}
