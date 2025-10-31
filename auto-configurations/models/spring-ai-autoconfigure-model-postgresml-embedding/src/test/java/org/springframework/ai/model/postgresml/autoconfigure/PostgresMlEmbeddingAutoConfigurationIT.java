/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.postgresml.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
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
public class PostgresMlEmbeddingAutoConfigurationIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("ghcr.io/postgresml/postgresml:2.8.1").asCompatibleSubstituteFor("postgres"))
		.withCommand("sleep", "infinity")
		.withUsername("postgresml")
		.withPassword("postgresml")
		.withDatabaseName("postgresml")
		.waitingFor(Wait.forLogMessage(".*Starting dashboard.*\\s", 1));

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void embedding() {
		ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withBean(JdbcTemplate.class, () -> this.jdbcTemplate)
			.withConfiguration(AutoConfigurations.of(PostgresMlEmbeddingAutoConfiguration.class));
		contextRunner.run(context -> {
			PostgresMlEmbeddingModel embeddingModel = context.getBean(PostgresMlEmbeddingModel.class);

			EmbeddingResponse embeddingResponse = embeddingModel
				.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(0).getIndex()).isZero();
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

			assertThat(embeddingModel.dimensions()).isEqualTo(768);
		});
	}

	@Test
	void embeddingActivation() {
		new ApplicationContextRunner().withBean(JdbcTemplate.class, () -> this.jdbcTemplate)
			.withConfiguration(AutoConfigurations.of(PostgresMlEmbeddingAutoConfiguration.class))
			.withPropertyValues("spring.ai.model.embedding=none")
			.run(context -> {
				assertThat(context.getBeansOfType(PostgresMlEmbeddingProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(PostgresMlEmbeddingModel.class)).isEmpty();
			});

		new ApplicationContextRunner().withBean(JdbcTemplate.class, () -> this.jdbcTemplate)
			.withPropertyValues("spring.ai.model.embedding=postgresml")
			.run(context -> {
				assertThat(context.getBeansOfType(PostgresMlEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(PostgresMlEmbeddingModel.class)).isNotEmpty();
			});

		new ApplicationContextRunner().withBean(JdbcTemplate.class, () -> this.jdbcTemplate)
			.withConfiguration(AutoConfigurations.of(PostgresMlEmbeddingAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(PostgresMlEmbeddingProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(PostgresMlEmbeddingModel.class)).isNotEmpty();
			});

	}

}
