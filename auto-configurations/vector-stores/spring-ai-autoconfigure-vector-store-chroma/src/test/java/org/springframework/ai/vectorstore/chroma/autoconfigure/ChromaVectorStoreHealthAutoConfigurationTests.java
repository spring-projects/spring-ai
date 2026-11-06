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

package org.springframework.ai.vectorstore.chroma.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChromaVectorStoreHealthAutoConfiguration}.
 *
 * @author Prasad Anandappa
 */
class ChromaVectorStoreHealthAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChromaVectorStoreHealthAutoConfiguration.class))
		.withUserConfiguration(MockBeansConfiguration.class);

	@Test
	void healthIndicatorRegisteredWhenChromaVectorStorePresent() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(ChromaVectorStoreHealthIndicator.class));
	}

	@Test
	void healthIndicatorReportsUpWhenHeartbeatReturns() {
		this.contextRunner.run(context -> {
			ChromaApi chromaApi = context.getBean(ChromaApi.class);
			when(chromaApi.heartbeat()).thenReturn(123456789L);

			HealthIndicator indicator = context.getBean(HealthIndicator.class);
			Health health = indicator.health();

			assertThat(health.getStatus()).isEqualTo(Status.UP);
			assertThat(health.getDetails()).containsEntry("heartbeat", 123456789L);
		});
	}

	@Test
	void healthIndicatorReportsDownWhenHeartbeatMissing() {
		this.contextRunner.run(context -> {
			ChromaApi chromaApi = context.getBean(ChromaApi.class);
			when(chromaApi.heartbeat()).thenReturn(null);

			HealthIndicator indicator = context.getBean(HealthIndicator.class);
			Health health = indicator.health();

			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
			assertThat(health.getDetails()).containsEntry("reason", "Chroma heartbeat returned no response");
		});
	}

	@Test
	void healthIndicatorReportsDownOnHeartbeatException() {
		this.contextRunner.run(context -> {
			ChromaApi chromaApi = context.getBean(ChromaApi.class);
			when(chromaApi.heartbeat()).thenThrow(new RuntimeException("Connection refused"));

			HealthIndicator indicator = context.getBean(HealthIndicator.class);
			Health health = indicator.health();

			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		});
	}

	@Test
	void healthIndicatorNotRegisteredWhenDisabled() {
		this.contextRunner.withPropertyValues("management.health.chroma.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(ChromaVectorStoreHealthIndicator.class);
			assertThat(context).doesNotHaveBean("chromaHealthIndicator");
		});
	}

	@Test
	void healthIndicatorBacksOffWhenCustomBeanProvided() {
		this.contextRunner.withUserConfiguration(CustomHealthIndicatorConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(HealthIndicator.class);
			assertThat(context).doesNotHaveBean(ChromaVectorStoreHealthIndicator.class);
			HealthIndicator indicator = context.getBean(HealthIndicator.class);
			assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class MockBeansConfiguration {

		@Bean
		ChromaVectorStore chromaVectorStore() {
			return mock(ChromaVectorStore.class);
		}

		@Bean
		ChromaApi chromaApi() {
			return mock(ChromaApi.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomHealthIndicatorConfiguration {

		@Bean(name = "chromaHealthIndicator")
		HealthIndicator chromaHealthIndicator() {
			return () -> Health.unknown().build();
		}

	}

}
