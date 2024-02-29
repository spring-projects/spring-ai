/*
 * Copyright 2024 - 2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.postgresml;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.postgresml.PostgresMlEmbeddingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Utkarsh Srivastava
 */
@JdbcTest(properties = "logging.level.sql=TRACE")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Disabled("Disabled from automatic execution, as it requires an excessive amount of memory (over 9GB)!")
public class PostgresMlAutoConfigurationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("ghcr.io/postgresml/postgresml:2.8.1").asCompatibleSubstituteFor("postgres"))
		.withCommand("sleep", "infinity")
		.withLabel("org.springframework.boot.service-connection", "postgres")
		.withUsername("postgresml")
		.withPassword("postgresml")
		.withDatabaseName("postgresml")
		.waitingFor(new LogMessageWaitStrategy().withRegEx(".*Starting dashboard.*\\s")
			.withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS)));

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void embedding() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(JdbcTemplate.class, () -> jdbcTemplate)
			.withConfiguration(AutoConfigurations.of(PostgresMlAutoConfiguration.class));
		contextRunner.run(context -> {
			PostgresMlEmbeddingClient embeddingClient = context.getBean(PostgresMlEmbeddingClient.class);

			EmbeddingResponse embeddingResponse = embeddingClient
				.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(0).getIndex()).isZero();
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

			assertThat(embeddingClient.dimensions()).isEqualTo(768);
		});
	}

	@Test
	void embeddingActivation() {
		new ApplicationContextRunner().withBean(JdbcTemplate.class, () -> jdbcTemplate)
			.withConfiguration(AutoConfigurations.of(PostgresMlAutoConfiguration.class))
			.withPropertyValues("spring.ai.postgresml.embedding.enabled=false")
			.run(context -> {
				assertThat(context.getBeansOfType(PostgresMlEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(PostgresMlEmbeddingClient.class)).isEmpty();
			});

		new ApplicationContextRunner().withBean(JdbcTemplate.class, () -> jdbcTemplate)
			.withConfiguration(AutoConfigurations.of(PostgresMlAutoConfiguration.class))
			.withPropertyValues("spring.ai.postgresml.embedding.enabled=true")
			.run(context -> {
				assertThat(context.getBeansOfType(PostgresMlEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(PostgresMlEmbeddingClient.class)).isNotEmpty();
			});

		new ApplicationContextRunner().withBean(JdbcTemplate.class, () -> jdbcTemplate)
			.withConfiguration(AutoConfigurations.of(PostgresMlAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(PostgresMlEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(PostgresMlEmbeddingClient.class)).isNotEmpty();
			});

	}

}
