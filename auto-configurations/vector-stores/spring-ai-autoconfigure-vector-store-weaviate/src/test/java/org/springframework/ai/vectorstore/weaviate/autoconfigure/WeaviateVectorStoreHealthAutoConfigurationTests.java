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
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.misc.Misc;
import io.weaviate.client.v1.misc.api.LiveChecker;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WeaviateVectorStoreHealthAutoConfiguration}.
 *
 * @author DDINGJOO
 */
class WeaviateVectorStoreHealthAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(WeaviateVectorStoreHealthAutoConfiguration.class));

	@Test
	void healthIndicatorIsUp() {
		WeaviateClient weaviateClient = mock(WeaviateClient.class);
		Misc misc = mock(Misc.class);
		LiveChecker liveChecker = mock(LiveChecker.class);
		@SuppressWarnings("unchecked")
		Result<Boolean> result = mock(Result.class);

		when(weaviateClient.misc()).thenReturn(misc);
		when(misc.liveChecker()).thenReturn(liveChecker);
		when(liveChecker.run()).thenReturn(result);
		when(result.hasErrors()).thenReturn(false);
		when(result.getResult()).thenReturn(true);

		this.contextRunner.withBean(WeaviateClient.class, () -> weaviateClient)
			.withBean(WeaviateConnectionDetails.class, TestWeaviateConnectionDetails::new)
			.run(context -> {
				assertThat(context).hasSingleBean(HealthIndicator.class);
				assertThat(context).hasBean("weaviateHealthIndicator");

				var health = context.getBean("weaviateHealthIndicator", HealthIndicator.class).health();
				assertThat(health.getStatus()).isEqualTo(Status.UP);
				assertThat(health.getDetails()).containsEntry("host", "localhost:8080")
					.containsEntry("live", true)
					.containsEntry("hasErrors", false);
			});
	}

	@Test
	void healthIndicatorIsDownWhenWeaviateReturnsErrors() {
		WeaviateClient weaviateClient = mock(WeaviateClient.class);
		Misc misc = mock(Misc.class);
		LiveChecker liveChecker = mock(LiveChecker.class);
		@SuppressWarnings("unchecked")
		Result<Boolean> result = mock(Result.class);

		when(weaviateClient.misc()).thenReturn(misc);
		when(misc.liveChecker()).thenReturn(liveChecker);
		when(liveChecker.run()).thenReturn(result);
		when(result.hasErrors()).thenReturn(true);
		when(result.getResult()).thenReturn(true);

		this.contextRunner.withBean(WeaviateClient.class, () -> weaviateClient).run(context -> {
			assertThat(context).hasSingleBean(HealthIndicator.class);

			var health = context.getBean("weaviateHealthIndicator", HealthIndicator.class).health();
			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		});
	}

	@Test
	void healthIndicatorIsDownWhenWeaviateIsUnavailable() {
		WeaviateClient weaviateClient = mock(WeaviateClient.class);
		Misc misc = mock(Misc.class);
		LiveChecker liveChecker = mock(LiveChecker.class);

		when(weaviateClient.misc()).thenReturn(misc);
		when(misc.liveChecker()).thenReturn(liveChecker);
		when(liveChecker.run()).thenThrow(new IllegalStateException("Weaviate unavailable"));

		this.contextRunner.withBean(WeaviateClient.class, () -> weaviateClient)
			.withBean(WeaviateConnectionDetails.class, TestWeaviateConnectionDetails::new)
			.run(context -> {
				assertThat(context).hasSingleBean(HealthIndicator.class);

				var health = context.getBean("weaviateHealthIndicator", HealthIndicator.class).health();
				assertThat(health.getStatus()).isEqualTo(Status.DOWN);
				assertThat(health.getDetails()).containsEntry("host", "localhost:8080");
			});
	}

	@Test
	void healthIndicatorIsDisabled() {
		this.contextRunner.withPropertyValues("management.health.weaviate.enabled=false")
			.withBean(WeaviateClient.class, () -> mock(WeaviateClient.class))
			.run(context -> assertThat(context).doesNotHaveBean("weaviateHealthIndicator"));
	}

	@Test
	void customHealthIndicatorIsPreserved() {
		this.contextRunner.withBean(WeaviateClient.class, () -> mock(WeaviateClient.class))
			.withBean("weaviateHealthIndicator", HealthIndicator.class, () -> () -> Health.up().build())
			.run(context -> assertThat(context).hasBean("weaviateHealthIndicator").hasSingleBean(HealthIndicator.class));
	}

	private static final class TestWeaviateConnectionDetails implements WeaviateConnectionDetails {

		@Override
		public String getHost() {
			return "localhost:8080";
		}

	}

}
