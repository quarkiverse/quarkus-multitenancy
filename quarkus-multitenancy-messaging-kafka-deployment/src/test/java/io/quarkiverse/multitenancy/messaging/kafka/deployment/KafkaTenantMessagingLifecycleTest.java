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

import static io.restassured.RestAssured.given;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.channel.KafkaConnectorChannels;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.exception.KafkaTenantPropagationException;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.reactive.messaging.IncomingInterceptor;
import io.smallrye.reactive.messaging.OutgoingInterceptor;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;

class KafkaTenantMessagingLifecycleTest {

    private static final String HEADER = "tenant-key";
    private static final String INCOMING_CHANNEL = "tenant-in";
    private static final String OUTGOING_CHANNEL = "tenant-out";

    @RegisterExtension
    static final QuarkusUnitTest UNIT_TEST = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(MessagingProbe.class, SenderResource.class,
                    AllChannelsKafka.class, ApplicationIncomingInterceptor.class,
                    ApplicationOutgoingInterceptor.class, ApplicationMarker.class, TestInMemoryConnector.class))
            .overrideConfigKey("quarkus.kafka.devservices.enabled", "false")
            .overrideConfigKey("quarkus.messaging.request-scoped.enabled", "true")
            .overrideConfigKey("quarkus.multi-tenant.messaging.kafka.header-name", HEADER)
            .overrideConfigKey("mp.messaging.incoming." + INCOMING_CHANNEL + ".connector", TestInMemoryConnector.CONNECTOR)
            .overrideConfigKey("mp.messaging.incoming." + INCOMING_CHANNEL + ".run-on-vertx-context", "true")
            .overrideConfigKey("mp.messaging.outgoing." + OUTGOING_CHANNEL + ".connector", TestInMemoryConnector.CONNECTOR);

    @Inject
    @Connector(TestInMemoryConnector.CONNECTOR)
    InMemoryConnector connector;

    @Inject
    MessagingProbe probe;

    @BeforeEach
    void resetProbe() {
        probe.reset();
    }

    @Test
    void shouldStampTenantThroughTheOutgoingMessagingPipeline() throws Exception {
        given()
                .queryParam("tenant", "acme-out")
                .when().get("/send")
                .then().statusCode(200);

        InMemorySink<String> sink = connector.sink(OUTGOING_CHANNEL);
        Message<String> sent = sink.received().getFirst();
        OutgoingKafkaRecordMetadata<?> metadata = sent.getMetadata(OutgoingKafkaRecordMetadata.class).orElseThrow();
        assertEquals("acme-out", new String(metadata.getHeaders().lastHeader(HEADER).value(), UTF_8));
        assertTrue(sent.getMetadata(ApplicationMarker.class).isPresent());
    }

    @Test
    void shouldKeepIncomingTenantUntilAsyncAcknowledgmentAndNotLeakIt() throws Exception {
        InMemorySource<Message<String>> source = connector.source(INCOMING_CHANNEL);
        CompletableFuture<Void> firstAcknowledged = new CompletableFuture<>();

        source.send(incomingMessage("first", "acme-in", firstAcknowledged));

        assertEquals(Optional.of("acme-in"), probe.observedTenant().get(5, TimeUnit.SECONDS));
        assertTrue(probe.wasApplicationIncomingInterceptorCalled());
        assertFalse(firstAcknowledged.isDone());
        probe.completeProcessing();
        firstAcknowledged.get(5, TimeUnit.SECONDS);

        probe.reset();
        CompletableFuture<Void> secondAcknowledged = new CompletableFuture<>();
        source.send(incomingMessage("second", null, secondAcknowledged));

        assertEquals(Optional.empty(), probe.observedTenant().get(5, TimeUnit.SECONDS));
        probe.completeProcessing();
        secondAcknowledged.get(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldNackInvalidIncomingMessageAndContinueChannel() throws Exception {
        InMemorySource<Message<String>> source = connector.source(INCOMING_CHANNEL);
        CompletableFuture<Void> rejectedAck = new CompletableFuture<>();
        CompletableFuture<Throwable> rejectedNack = new CompletableFuture<>();

        source.send(incomingMessage("invalid", "not valid", rejectedAck, rejectedNack));

        Throwable rejection = rejectedNack.get(5, TimeUnit.SECONDS);
        assertTrue(rejection instanceof KafkaTenantPropagationException);
        assertFalse(rejectedAck.isDone());
        assertFalse(probe.hasObservedTenant());
        assertFalse(probe.wasApplicationIncomingInterceptorCalled());

        probe.reset();
        CompletableFuture<Void> validAck = new CompletableFuture<>();
        source.send(incomingMessage("valid", "acme-valid", validAck));

        assertEquals(Optional.of("acme-valid"), probe.observedTenant().get(5, TimeUnit.SECONDS));
        probe.completeProcessing();
        validAck.get(5, TimeUnit.SECONDS);
    }

    private static Message<String> incomingMessage(String payload, String tenantId,
            CompletableFuture<Void> acknowledged) {
        return incomingMessage(payload, tenantId, acknowledged, new CompletableFuture<>());
    }

    private static Message<String> incomingMessage(String payload, String tenantId,
            CompletableFuture<Void> acknowledged, CompletableFuture<Throwable> negativelyAcknowledged) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(INCOMING_CHANNEL, 0, 0L, "key", payload);
        if (tenantId != null) {
            record.headers().add(HEADER, tenantId.getBytes(UTF_8));
        }
        IncomingKafkaRecordMetadata<String, String> metadata = new IncomingKafkaRecordMetadata<>(record, INCOMING_CHANNEL);
        return Message.of(payload, () -> {
            acknowledged.complete(null);
            return CompletableFuture.completedFuture(null);
        }, failure -> {
            negativelyAcknowledged.complete(failure);
            return CompletableFuture.completedFuture(null);
        }).addMetadata(metadata);
    }

    @Alternative
    @Priority(1)
    @Dependent
    public static class AllChannelsKafka extends KafkaConnectorChannels {

        @Inject
        public AllChannelsKafka(Config config) {
            super(config);
        }

        @Override
        public boolean isIncomingKafka(String channelName) {
            return true;
        }

        @Override
        public boolean isOutgoingKafka(java.util.List<String> channelNames) {
            return true;
        }
    }

    public static final class ApplicationMarker {

        private ApplicationMarker() {
        }
    }

    @ApplicationScoped
    public static class ApplicationIncomingInterceptor implements IncomingInterceptor {

        @Inject
        MessagingProbe probe;

        @Override
        public Message<?> afterMessageReceive(Message<?> message) {
            probe.markApplicationIncomingInterceptorCalled();
            return message;
        }

        @Override
        public void onMessageAck(Message<?> message) {
        }

        @Override
        public void onMessageNack(Message<?> message, Throwable failure) {
        }
    }

    @ApplicationScoped
    public static class ApplicationOutgoingInterceptor implements OutgoingInterceptor {

        @Override
        public Message<?> beforeMessageSend(Message<?> message) {
            return message.addMetadata(new ApplicationMarker());
        }

        @Override
        public void onMessageAck(Message<?> message) {
        }

        @Override
        public void onMessageNack(Message<?> message, Throwable failure) {
        }
    }

    @Path("/send")
    public static class SenderResource {

        @Inject
        TenantContext tenantContext;

        @Inject
        MessagingProbe probe;

        @GET
        public CompletionStage<String> send(@QueryParam("tenant") String tenantId) {
            tenantContext.setTenantId(tenantId);
            return probe.send("payload").thenApply(ignored -> "sent");
        }
    }

    @ApplicationScoped
    public static class MessagingProbe {

        @Inject
        TenantContext tenantContext;

        @Inject
        @Channel(OUTGOING_CHANNEL)
        Emitter<String> emitter;

        private volatile CompletableFuture<Optional<String>> observedTenant;
        private volatile CompletableFuture<Void> processing;
        private volatile boolean applicationIncomingInterceptorCalled;

        void reset() {
            observedTenant = new CompletableFuture<>();
            processing = new CompletableFuture<>();
            applicationIncomingInterceptorCalled = false;
        }

        CompletableFuture<Optional<String>> observedTenant() {
            return observedTenant.orTimeout(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
        }

        boolean hasObservedTenant() {
            return observedTenant.isDone();
        }

        void markApplicationIncomingInterceptorCalled() {
            applicationIncomingInterceptorCalled = true;
        }

        boolean wasApplicationIncomingInterceptorCalled() {
            return applicationIncomingInterceptorCalled;
        }

        void completeProcessing() {
            processing.complete(null);
        }

        CompletionStage<Void> send(String payload) {
            return emitter.send(payload);
        }

        @Incoming(INCOMING_CHANNEL)
        CompletionStage<Void> receive(String payload) {
            observedTenant.complete(tenantContext.getTenantId());
            return processing;
        }
    }
}
