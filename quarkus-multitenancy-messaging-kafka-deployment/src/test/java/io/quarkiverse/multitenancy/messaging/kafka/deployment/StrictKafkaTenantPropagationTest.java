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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.multitenancy.core.runtime.context.TenantContext;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.channel.KafkaConnectorChannels;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.exception.KafkaTenantPropagationException;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.interceptor.KafkaTenantIncomingInterceptor;
import io.quarkiverse.multitenancy.messaging.kafka.runtime.interceptor.KafkaTenantOutgoingInterceptor;
import io.quarkus.test.QuarkusUnitTest;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;

class StrictKafkaTenantPropagationTest {

    private static final String OUTGOING_CHANNEL = "strict-tenant-out";

    @RegisterExtension
    static final QuarkusUnitTest UNIT_TEST = new QuarkusUnitTest()
            .withApplicationRoot(
                    jar -> jar.addClasses(SenderResource.class, AllChannelsKafka.class, TestInMemoryConnector.class))
            .overrideConfigKey("quarkus.kafka.devservices.enabled", "false")
            .overrideConfigKey("quarkus.multi-tenant.messaging.kafka.fail-on-missing-incoming-tenant", "true")
            .overrideConfigKey("quarkus.multi-tenant.messaging.kafka.fail-on-missing-outgoing-tenant", "true")
            .overrideConfigKey("mp.messaging.outgoing." + OUTGOING_CHANNEL + ".connector", TestInMemoryConnector.CONNECTOR);

    @Inject
    TenantContext tenantContext;

    @Inject
    @Any
    KafkaTenantOutgoingInterceptor outgoing;

    @Inject
    @Any
    KafkaTenantIncomingInterceptor incoming;

    @Inject
    @Connector(TestInMemoryConnector.CONNECTOR)
    InMemoryConnector connector;

    @Test
    @ActivateRequestContext
    void shouldRejectMissingIncomingTenantInStrictMode() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("tenant-in", 0, 0L, "key", "payload");
        IncomingKafkaRecordMetadata<String, String> metadata = new IncomingKafkaRecordMetadata<>(record, "tenant-in");
        Message<String> message = Message.of("payload", () -> CompletableFuture.completedFuture(null))
                .addMetadata(metadata);

        assertThrows(KafkaTenantPropagationException.class, () -> incoming.afterMessageReceive(message));
    }

    @Test
    @ActivateRequestContext
    void shouldRejectMissingOutgoingTenantInStrictMode() {
        tenantContext.clear();

        assertThrows(KafkaTenantPropagationException.class,
                () -> outgoing.beforeMessageSend(Message.of("payload")));
    }

    @Test
    void shouldFailOnlyTheStrictSendAndKeepOutgoingChannelAlive() {
        given()
                .when().get("/strict-send")
                .then().statusCode(500);

        given()
                .queryParam("tenant", "acme-valid")
                .when().get("/strict-send")
                .then().statusCode(200);

        InMemorySink<String> sink = connector.sink(OUTGOING_CHANNEL);
        assertEquals(1, sink.received().size());
        OutgoingKafkaRecordMetadata<?> metadata = sink.received().getFirst()
                .getMetadata(OutgoingKafkaRecordMetadata.class)
                .orElseThrow();
        assertEquals("acme-valid", new String(metadata.getHeaders().lastHeader("X-Tenant").value(), UTF_8));
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
        public boolean isOutgoingKafka(List<String> channelNames) {
            return true;
        }
    }

    @Path("/strict-send")
    public static class SenderResource {

        @Inject
        TenantContext tenantContext;

        @Inject
        @Channel(OUTGOING_CHANNEL)
        Emitter<String> emitter;

        @GET
        public CompletionStage<String> send(@QueryParam("tenant") String tenantId) {
            tenantContext.setTenantId(tenantId);
            return emitter.send("payload").thenApply(ignored -> "sent");
        }
    }
}
