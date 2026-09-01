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

package org.springframework.ai.vectorstore.pgvector.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PgVectorStoreHealthAutoConfiguration}.
 *
 * @author jiajingda
 */
class PgVectorStoreHealthAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PgVectorStoreHealthAutoConfiguration.class))
		.withUserConfiguration(MockBeansConfiguration.class);

	@Test
	void healthIndicatorRegisteredWhenPgVectorStorePresent() {
		this.contextRunner.run(context -> assertThat(context).hasSingleBean(PgVectorStoreHealthIndicator.class));
	}

	@Test
	void healthIndicatorReportsUpWhenVectorExtensionInstalled() {
		this.contextRunner.run(context -> {
			JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
			when(jdbcTemplate.queryForObject(isA(String.class), eq(String.class))).thenReturn("0.5.1");

			HealthIndicator indicator = context.getBean(HealthIndicator.class);
			Health health = indicator.health();

			assertThat(health.getStatus()).isEqualTo(Status.UP);
			assertThat(health.getDetails()).containsEntry("vectorExtensionVersion", "0.5.1");
			assertThat(health.getDetails()).containsEntry("database", "postgresql");
		});
	}

	@Test
	void healthIndicatorReportsDownWhenVectorExtensionMissing() {
		this.contextRunner.run(context -> {
			JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
			when(jdbcTemplate.queryForObject(isA(String.class), eq(String.class))).thenReturn(null);

			HealthIndicator indicator = context.getBean(HealthIndicator.class);
			Health health = indicator.health();

			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
			assertThat(health.getDetails()).containsEntry("reason", "PgVector extension not installed");
		});
	}

	@Test
	void healthIndicatorReportsDownOnQueryException() {
		this.contextRunner.run(context -> {
			JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
			when(jdbcTemplate.queryForObject(isA(String.class), eq(String.class)))
				.thenThrow(new RuntimeException("Connection refused"));

			HealthIndicator indicator = context.getBean(HealthIndicator.class);
			Health health = indicator.health();

			assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		});
	}

	@Test
	void healthIndicatorNotRegisteredWhenDisabled() {
		this.contextRunner.withPropertyValues("management.health.pgvector.enabled=false").run(context -> {
			assertThat(context).doesNotHaveBean(PgVectorStoreHealthIndicator.class);
			assertThat(context).doesNotHaveBean("pgvectorHealthIndicator");
		});
	}

	@Test
	void healthIndicatorBacksOffWhenCustomBeanProvided() {
		this.contextRunner.withUserConfiguration(CustomHealthIndicatorConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(HealthIndicator.class);
			assertThat(context).doesNotHaveBean(PgVectorStoreHealthIndicator.class);
			HealthIndicator indicator = context.getBean(HealthIndicator.class);
			assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class MockBeansConfiguration {

		@Bean
		PgVectorStore pgVectorStore() {
			return mock(PgVectorStore.class);
		}

		@Bean
		JdbcTemplate jdbcTemplate() {
			return mock(JdbcTemplate.class);
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomHealthIndicatorConfiguration {

		@Bean(name = "pgvectorHealthIndicator")
		HealthIndicator pgvectorHealthIndicator() {
			return () -> Health.unknown().build();
		}

	}

}
