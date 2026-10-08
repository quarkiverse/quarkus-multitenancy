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

import io.quarkiverse.multitenancy.http.runtime.filter.TenantFilter;
import io.quarkiverse.multitenancy.http.runtime.resolver.CookieTenantResolver;
import io.quarkiverse.multitenancy.http.runtime.resolver.HeaderTenantResolver;
import io.quarkiverse.multitenancy.http.runtime.resolver.JwtTenantResolver;
import io.quarkiverse.multitenancy.http.runtime.resolver.PathTenantResolver;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

public class MultiTenantHttpProcessor {

    private static final String FEATURE = "quarkus-multitenancy-http";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void registerHttpBeans(BuildProducer<AdditionalBeanBuildItem> beans) {
        beans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClass(TenantFilter.class)
                .addBeanClass(HeaderTenantResolver.class)
                .addBeanClass(JwtTenantResolver.class)
                .addBeanClass(CookieTenantResolver.class)
                .addBeanClass(PathTenantResolver.class)
                .build());
    }
}
