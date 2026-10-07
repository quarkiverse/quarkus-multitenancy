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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.smallrye.reactive.messaging.memory.InMemorySource;

class NonKafkaChannelIsolationTest {

    private static final String HEADER = "tenant-key";
    private static final String INCOMING_CHANNEL = "non-kafka-in";
    private static final String OUTGOING_CHANNEL = "non-kafka-out";

    @RegisterExtension
    static final QuarkusUnitTest UNIT_TEST = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(Probe.class, SenderResource.class, TestInMemoryConnector.class))
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
    Probe probe;

    @BeforeEach
    void resetProbe() {
        probe.reset();
    }

    @Test
    void shouldNotAddKafkaMetadataToNonKafkaOutgoingChannel() {
        given()
                .queryParam("tenant", "acme-out")
                .when().get("/non-kafka-send")
                .then().statusCode(200);

        InMemorySink<String> sink = connector.sink(OUTGOING_CHANNEL);
        Message<String> sent = sink.received().getFirst();
        assertTrue(sent.getMetadata(OutgoingKafkaRecordMetadata.class).isEmpty());
    }

    @Test
    void shouldIgnoreKafkaMetadataOnNonKafkaIncomingChannel() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(INCOMING_CHANNEL, 0, 0L, "key", "payload");
        record.headers().add(HEADER, "must-not-propagate".getBytes(UTF_8));
        IncomingKafkaRecordMetadata<String, String> metadata = new IncomingKafkaRecordMetadata<>(record, INCOMING_CHANNEL);
        CompletableFuture<Void> acknowledged = new CompletableFuture<>();
        Message<String> message = Message.of("payload", () -> {
            acknowledged.complete(null);
            return CompletableFuture.completedFuture(null);
        }).addMetadata(metadata);

        InMemorySource<Message<String>> source = connector.source(INCOMING_CHANNEL);
        source.send(message);

        assertEquals(Optional.empty(), probe.observedTenant().get(5, TimeUnit.SECONDS));
        acknowledged.get(5, TimeUnit.SECONDS);
    }

    @Path("/non-kafka-send")
    public static class SenderResource {

        @Inject
        TenantContext tenantContext;

        @Inject
        Probe probe;

        @GET
        public CompletionStage<String> send(@QueryParam("tenant") String tenantId) {
            tenantContext.setTenantId(tenantId);
            return probe.send("payload").thenApply(ignored -> "sent");
        }
    }

    @ApplicationScoped
    public static class Probe {

        @Inject
        TenantContext tenantContext;

        @Inject
        @Channel(OUTGOING_CHANNEL)
        Emitter<String> emitter;

        private volatile CompletableFuture<Optional<String>> observedTenant;

        void reset() {
            observedTenant = new CompletableFuture<>();
        }

        CompletableFuture<Optional<String>> observedTenant() {
            return observedTenant;
        }

        CompletionStage<Void> send(String payload) {
            return emitter.send(payload);
        }

        @Incoming(INCOMING_CHANNEL)
        void receive(String payload) {
            observedTenant.complete(tenantContext.getTenantId());
        }
    }
}
