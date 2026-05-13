/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore.observation.autoconfigure;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.CompositeHealthContributor;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for {@link VectorStoreHealthIndicator}.
 * <p>
 * Registers a {@link HealthContributor} named {@code vectorStoreHealthContributor} for
 * every {@link VectorStore} bean present in the application context. When there is a
 * single store the contributor is a plain {@link HealthIndicator}; when multiple stores
 * are present a {@link CompositeHealthContributor} is created, keyed by Spring bean name,
 * so each store is individually visible under
 * {@code /actuator/health/vectorStore/<beanName>}. In the composite case each leaf
 * indicator also reports the store's {@link VectorStore#getName()} as a detail value,
 * which may differ from the bean name used as the path segment.
 * <p>
 * The contributor is only registered when the {@code vectorStore} health indicator is
 * enabled (controlled via {@code management.health.vectorStore.enabled=true/false},
 * defaulting to {@code true}).
 * <p>
 * To override this auto-configuration, register a bean named
 * {@code vectorStoreHealthContributor}.
 *
 * @author Surya Teja Gorre
 * @since 2.0.0
 */
@AutoConfiguration
@AutoConfigureAfter(VectorStoreObservationAutoConfiguration.class)
@ConditionalOnClass({ VectorStore.class, HealthIndicator.class })
@ConditionalOnBean(VectorStore.class)
@ConditionalOnEnabledHealthIndicator("vectorStore")
public class VectorStoreHealthContributorAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "vectorStoreHealthContributor")
	public HealthContributor vectorStoreHealthContributor(Map<String, VectorStore> vectorStores) {
		if (vectorStores.size() == 1) {
			return new VectorStoreHealthIndicator(vectorStores.values().iterator().next());
		}
		Map<String, HealthContributor> contributors = new LinkedHashMap<>();
		vectorStores.forEach((name, store) -> contributors.put(name, new VectorStoreHealthIndicator(store)));
		return CompositeHealthContributor.fromMap(contributors);
	}

}
