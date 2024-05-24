/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.postgresml;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel.VectorType;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Toshiaki Maki
 */
@JdbcTest(properties = "logging.level.sql=TRACE")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Disabled("Disabled from automatic execution, as it requires an excessive amount of memory (over 9GB)!")
class PostgresMlEmbeddingModelIT {

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

	@AfterEach
	void dropPgmlExtension() {
		this.jdbcTemplate.execute("DROP EXTENSION IF EXISTS pgml");
	}

	@Test
	void embed() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate);
		embeddingModel.afterPropertiesSet();

		List<Double> embed = embeddingModel.embed("Hello World!");

		assertThat(embed).hasSize(768);
	}

	@Test
	void embedWithPgVector() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder()
					.withTransformer("distilbert-base-uncased")
					.withVectorType(PostgresMlEmbeddingModel.VectorType.PG_VECTOR)
					.build());
		embeddingModel.afterPropertiesSet();

		List<Double> embed = embeddingModel.embed(new Document("Hello World!"));

		assertThat(embed).hasSize(768);
	}

	@Test
	void embedWithDifferentModel() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder().withTransformer("intfloat/e5-small").build());
		embeddingModel.afterPropertiesSet();

		List<Double> embed = embeddingModel.embed(new Document("Hello World!"));

		assertThat(embed).hasSize(384);
	}

	@Test
	void embedWithKwargs() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder()
					.withTransformer("distilbert-base-uncased")
					.withVectorType(PostgresMlEmbeddingModel.VectorType.PG_ARRAY)
					.withKwargs(Map.of("device", "cpu"))
					.withMetadataMode(MetadataMode.EMBED)
					.build());
		embeddingModel.afterPropertiesSet();

		List<Double> embed = embeddingModel.embed(new Document("Hello World!"));

		assertThat(embed).hasSize(768);
	}

	@ParameterizedTest
	@ValueSource(strings = { "PG_ARRAY", "PG_VECTOR" })
	void embedForResponse(String vectorType) {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder()
					.withTransformer("distilbert-base-uncased")
					.withVectorType(VectorType.valueOf(vectorType))
					.build());
		embeddingModel.afterPropertiesSet();

		EmbeddingResponse embeddingResponse = embeddingModel
			.embedForResponse(List.of("Hello World!", "Spring AI!", "LLM!"));

		assertThat(embeddingResponse).isNotNull();
		assertThat(embeddingResponse.getResults()).hasSize(3);
		assertThat(embeddingResponse.getMetadata()).containsExactlyInAnyOrderEntriesOf(
				Map.of("transformer", "distilbert-base-uncased", "vector-type", vectorType, "kwargs", "{}"));
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(768);
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(768);
		assertThat(embeddingResponse.getResults().get(2).getIndex()).isEqualTo(2);
		assertThat(embeddingResponse.getResults().get(2).getOutput()).hasSize(768);
	}

	@Test
	void embedCallWithRequestOptionsOverride() {

		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder()
					.withTransformer("distilbert-base-uncased")
					.withVectorType(VectorType.PG_VECTOR)
					.build());
		embeddingModel.afterPropertiesSet();

		var request1 = new EmbeddingRequest(List.of("Hello World!", "Spring AI!", "LLM!"), EmbeddingOptions.EMPTY);

		EmbeddingResponse embeddingResponse = embeddingModel.call(request1);

		assertThat(embeddingResponse).isNotNull();
		assertThat(embeddingResponse.getResults()).hasSize(3);
		assertThat(embeddingResponse.getMetadata()).containsExactlyInAnyOrderEntriesOf(Map.of("transformer",
				"distilbert-base-uncased", "vector-type", VectorType.PG_VECTOR.name(), "kwargs", "{}"));
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(768);
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(768);
		assertThat(embeddingResponse.getResults().get(2).getIndex()).isEqualTo(2);
		assertThat(embeddingResponse.getResults().get(2).getOutput()).hasSize(768);

		// Override the default options in the request
		var request2 = new EmbeddingRequest(List.of("Hello World!", "Spring AI!", "LLM!"),
				PostgresMlEmbeddingOptions.builder()
					.withTransformer("intfloat/e5-small")
					.withVectorType(VectorType.PG_ARRAY)
					.withMetadataMode(MetadataMode.EMBED)
					.withKwargs(Map.of("device", "cpu"))
					.build());

		embeddingResponse = embeddingModel.call(request2);

		assertThat(embeddingResponse).isNotNull();
		assertThat(embeddingResponse.getResults()).hasSize(3);
		assertThat(embeddingResponse.getMetadata()).containsExactlyInAnyOrderEntriesOf(Map.of("transformer",
				"intfloat/e5-small", "vector-type", VectorType.PG_ARRAY.name(), "kwargs", "{\"device\":\"cpu\"}"));

		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(384);
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(384);
		assertThat(embeddingResponse.getResults().get(2).getIndex()).isEqualTo(2);
		assertThat(embeddingResponse.getResults().get(2).getOutput()).hasSize(384);
	}

	@Test
	void dimensions() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate);
		embeddingModel.afterPropertiesSet();
		Assertions.assertThat(embeddingModel.dimensions()).isEqualTo(768);
		// cached
		Assertions.assertThat(embeddingModel.dimensions()).isEqualTo(768);
	}

	@SpringBootApplication
	public static class TestApplication {

	}

}