/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.vectorstore.weaviate.autoconfigure;

import io.weaviate.client.WeaviateClient;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Weaviate health indicator.
 *
 * @author DDINGJOO
 */
@AutoConfiguration(after = WeaviateVectorStoreAutoConfiguration.class)
@ConditionalOnClass({ WeaviateClient.class, HealthIndicator.class })
@ConditionalOnBean(WeaviateClient.class)
@ConditionalOnEnabledHealthIndicator("weaviate")
public class WeaviateVectorStoreHealthAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(name = "weaviateHealthIndicator")
	HealthIndicator weaviateHealthIndicator(WeaviateClient weaviateClient,
			ObjectProvider<WeaviateConnectionDetails> connectionDetailsProvider) {
		return () -> {
			@Nullable
			WeaviateConnectionDetails connectionDetails = connectionDetailsProvider.getIfAvailable();
			try {
				var response = weaviateClient.misc().liveChecker().run();
				Health.Builder builder = (response.hasErrors() || !Boolean.TRUE.equals(response.getResult())) ? Health.down()
						: Health.up();
				builder.withDetail("live", response.getResult()).withDetail("hasErrors", response.hasErrors());
				if (response.hasErrors() && response.getError() != null) {
					builder.withDetail("error", response.getError().toString());
				}
				addConnectionDetails(builder, connectionDetails);
				return builder.build();
			}
			catch (Exception exception) {
				Health.Builder builder = Health.down(exception);
				addConnectionDetails(builder, connectionDetails);
				return builder.build();
			}
		};
	}

	private static void addConnectionDetails(Health.Builder builder,
			@Nullable WeaviateConnectionDetails connectionDetails) {
		if (connectionDetails == null) {
			return;
		}
		builder.withDetail("host", connectionDetails.getHost());
	}

}
