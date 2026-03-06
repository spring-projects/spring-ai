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

package org.springframework.ai.vectorstore.qdrant.autoconfigure;

import java.time.Duration;

import io.qdrant.client.QdrantClient;
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
 * {@link AutoConfiguration Auto-configuration} for Qdrant health indicator.
 *
 * @author DDINGJOO
 */
@AutoConfiguration(after = QdrantVectorStoreAutoConfiguration.class)
@ConditionalOnClass({ QdrantClient.class, HealthIndicator.class })
@ConditionalOnBean(QdrantClient.class)
@ConditionalOnEnabledHealthIndicator("qdrant")
public class QdrantVectorStoreHealthAutoConfiguration {

	private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(5);

	@Bean
	@ConditionalOnMissingBean(name = "qdrantHealthIndicator")
	HealthIndicator qdrantHealthIndicator(QdrantClient qdrantClient,
			ObjectProvider<QdrantConnectionDetails> connectionDetailsProvider) {
		return () -> {
			@Nullable
			QdrantConnectionDetails connectionDetails = connectionDetailsProvider.getIfAvailable();
			try {
				var response = qdrantClient.healthCheckAsync(HEALTH_CHECK_TIMEOUT).get();
				Health.Builder builder = Health.up()
					.withDetail("title", response.getTitle())
					.withDetail("version", response.getVersion());
				if (response.hasCommit()) {
					builder.withDetail("commit", response.getCommit());
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
			@Nullable QdrantConnectionDetails connectionDetails) {
		if (connectionDetails == null) {
			return;
		}
		builder.withDetail("host", connectionDetails.getHost()).withDetail("port", connectionDetails.getPort());
	}

}
