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

import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.QdrantOuterClass;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QdrantVectorStoreHealthAutoConfiguration}.
 *
 * @author DDINGJOO
 */
class QdrantVectorStoreHealthAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(QdrantVectorStoreHealthAutoConfiguration.class));

	@Test
	void healthIndicatorIsUp() {
		QdrantClient qdrantClient = mock(QdrantClient.class);
		when(qdrantClient.healthCheckAsync(any())).thenReturn(Futures.immediateFuture(
				QdrantOuterClass.HealthCheckReply.newBuilder().setTitle("qdrant").setVersion("1.13.0").build()));

		this.contextRunner.withBean(QdrantClient.class, () -> qdrantClient)
			.withBean(QdrantConnectionDetails.class, TestQdrantConnectionDetails::new)
			.run(context -> {
				assertThat(context).hasSingleBean(HealthIndicator.class);
				assertThat(context).hasBean("qdrantHealthIndicator");

				var health = context.getBean("qdrantHealthIndicator", HealthIndicator.class).health();
				assertThat(health.getStatus()).isEqualTo(Status.UP);
				assertThat(health.getDetails()).containsEntry("host", "localhost")
					.containsEntry("port", 6334)
					.containsEntry("title", "qdrant")
					.containsEntry("version", "1.13.0");
			});
	}

	@Test
	void healthIndicatorIsDownWhenQdrantIsUnavailable() {
		QdrantClient qdrantClient = mock(QdrantClient.class);
		when(qdrantClient.healthCheckAsync(any()))
			.thenReturn(Futures.immediateFailedFuture(new IllegalStateException("Qdrant unavailable")));

		this.contextRunner.withBean(QdrantClient.class, () -> qdrantClient)
			.withBean(QdrantConnectionDetails.class, TestQdrantConnectionDetails::new)
			.run(context -> {
				assertThat(context).hasSingleBean(HealthIndicator.class);

				var health = context.getBean("qdrantHealthIndicator", HealthIndicator.class).health();
				assertThat(health.getStatus()).isEqualTo(Status.DOWN);
				assertThat(health.getDetails()).containsEntry("host", "localhost").containsEntry("port", 6334);
			});
	}

	@Test
	void healthIndicatorIsDisabled() {
		this.contextRunner.withPropertyValues("management.health.qdrant.enabled=false")
			.withBean(QdrantClient.class, () -> mock(QdrantClient.class))
			.run(context -> assertThat(context).doesNotHaveBean("qdrantHealthIndicator"));
	}

	@Test
	void customHealthIndicatorIsPreserved() {
		this.contextRunner.withBean(QdrantClient.class, () -> mock(QdrantClient.class))
			.withBean("qdrantHealthIndicator", HealthIndicator.class, () -> () -> Health.up().build())
			.run(context -> assertThat(context).hasBean("qdrantHealthIndicator").hasSingleBean(HealthIndicator.class));
	}

	private static final class TestQdrantConnectionDetails implements QdrantConnectionDetails {

		@Override
		public String getHost() {
			return "localhost";
		}

		@Override
		public int getPort() {
			return 6334;
		}

		@Override
		public String getApiKey() {
			return null;
		}

	}

}
