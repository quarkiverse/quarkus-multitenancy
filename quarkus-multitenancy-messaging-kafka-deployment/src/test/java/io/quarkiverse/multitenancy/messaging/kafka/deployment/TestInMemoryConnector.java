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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;

import org.eclipse.microprofile.reactive.messaging.spi.Connector;

import io.smallrye.reactive.messaging.connector.InboundConnector;
import io.smallrye.reactive.messaging.connector.OutboundConnector;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

/**
 * Exposes the connector interfaces directly for Quarkus build-time discovery,
 * including SmallRye 5 where InMemoryConnector inherits them from TestingConnector.
 * Limits the bean types to avoid registration through both connector SPIs.
 */
@ApplicationScoped
@Typed({ TestInMemoryConnector.class, InMemoryConnector.class, InboundConnector.class, OutboundConnector.class })
@Connector(TestInMemoryConnector.CONNECTOR)
public class TestInMemoryConnector extends InMemoryConnector implements InboundConnector, OutboundConnector {

    public static final String CONNECTOR = "test-in-memory";
}
