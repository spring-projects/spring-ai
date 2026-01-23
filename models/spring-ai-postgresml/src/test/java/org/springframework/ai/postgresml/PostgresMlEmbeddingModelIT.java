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

package org.springframework.ai.postgresml;

import java.util.List;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel.VectorType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Toshiaki Maki
 * @author Eddú Meléndez
 */
@JdbcTest(properties = "logging.level.sql=TRACE")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Disabled("Disabled from automatic execution, as it pulls a very large image file (over 9GB)!")
class PostgresMlEmbeddingModelIT {

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

	@BeforeEach
	void dropPgmlExtension() {
		this.jdbcTemplate.execute("DROP EXTENSION IF EXISTS pgml");
	}

	@Test
	void embed() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder().build(), true);
		embeddingModel.afterPropertiesSet();

		float[] embed = embeddingModel.embed("Hello World!");

		assertThat(embed).hasSize(768);
	}

	@Test
	void embedWithPgVector() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder()
					.transformer("distilbert-base-uncased")
					.vectorType(PostgresMlEmbeddingModel.VectorType.PG_VECTOR)
					.build(),
				true);
		embeddingModel.afterPropertiesSet();

		float[] embed = embeddingModel.embed(new Document("Hello World!"));

		assertThat(embed).hasSize(768);
	}

	@Test
	void embedWithDifferentModel() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder().transformer("intfloat/e5-small").build(), true);
		embeddingModel.afterPropertiesSet();

		float[] embed = embeddingModel.embed(new Document("Hello World!"));

		assertThat(embed).hasSize(384);
	}

	@Test
	void embedWithKwargs() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder()
					.transformer("distilbert-base-uncased")
					.vectorType(PostgresMlEmbeddingModel.VectorType.PG_ARRAY)
					.kwargs(Map.of("device", "cpu"))
					.metadataMode(MetadataMode.EMBED)
					.build(),
				true);
		embeddingModel.afterPropertiesSet();

		float[] embed = embeddingModel.embed(new Document("Hello World!"));

		assertThat(embed).hasSize(768);
	}

	@ParameterizedTest
	@ValueSource(strings = { "PG_ARRAY", "PG_VECTOR" })
	void embedForResponse(String vectorType) {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder()
					.transformer("distilbert-base-uncased")
					.vectorType(VectorType.valueOf(vectorType))
					.build(),
				true);
		embeddingModel.afterPropertiesSet();

		EmbeddingResponse embeddingResponse = embeddingModel
			.embedForResponse(List.of("Hello World!", "Spring AI!", "LLM!"));

		assertThat(embeddingResponse).isNotNull();
		assertThat(embeddingResponse.getResults()).hasSize(3);

		EmbeddingResponseMetadata metadata = embeddingResponse.getMetadata();
		assertThat(metadata.keySet()).as("Metadata should contain exactly the expected keys")
			.containsExactlyInAnyOrder("transformer", "vector-type", "kwargs");

		assertThat(metadata.get("transformer").toString())
			.as("Transformer in metadata should be 'distilbert-base-uncased'")
			.isEqualTo("distilbert-base-uncased");

		assertThat(metadata.get("vector-type").toString())
			.as("Vector type in metadata should match expected vector type")
			.isEqualTo(vectorType);

		assertThat(metadata.get("kwargs").toString()).as("kwargs in metadata should be '{}'").isEqualTo("{}");

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
					.transformer("distilbert-base-uncased")
					.vectorType(VectorType.PG_VECTOR)
					.build(),
				true);
		embeddingModel.afterPropertiesSet();

		var request1 = new EmbeddingRequest(List.of("Hello World!", "Spring AI!", "LLM!"),
				EmbeddingOptions.builder().build());

		EmbeddingResponse embeddingResponse = embeddingModel.call(request1);

		assertThat(embeddingResponse).isNotNull();
		assertThat(embeddingResponse.getResults()).hasSize(3);

		EmbeddingResponseMetadata metadata = embeddingResponse.getMetadata();

		assertThat(metadata.keySet()).as("Metadata should contain exactly the expected keys")
			.containsExactlyInAnyOrder("transformer", "vector-type", "kwargs");

		assertThat(metadata.get("transformer").toString())
			.as("Transformer in metadata should be 'distilbert-base-uncased'")
			.isEqualTo("distilbert-base-uncased");

		assertThat(metadata.get("vector-type").toString())
			.as("Vector type in metadata should match expected vector type")
			.isEqualTo(VectorType.PG_VECTOR.name());

		assertThat(metadata.get("kwargs").toString()).as("kwargs in metadata should be '{}'").isEqualTo("{}");

		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(768);
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(768);
		assertThat(embeddingResponse.getResults().get(2).getIndex()).isEqualTo(2);
		assertThat(embeddingResponse.getResults().get(2).getOutput()).hasSize(768);

		// Override the default options in the request
		var request2 = new EmbeddingRequest(List.of("Hello World!", "Spring AI!", "LLM!"),
				PostgresMlEmbeddingOptions.builder()
					.transformer("intfloat/e5-small")
					.vectorType(VectorType.PG_ARRAY)
					.metadataMode(MetadataMode.EMBED)
					.kwargs(Map.of("device", "cpu"))
					.build());

		embeddingResponse = embeddingModel.call(request2);

		assertThat(embeddingResponse).isNotNull();
		assertThat(embeddingResponse.getResults()).hasSize(3);

		metadata = embeddingResponse.getMetadata();

		assertThat(metadata.keySet()).as("Metadata should contain exactly the expected keys")
			.containsExactlyInAnyOrder("transformer", "vector-type", "kwargs");

		assertThat(metadata.get("transformer").toString()).as("Transformer in metadata should be 'intfloat/e5-small'")
			.isEqualTo("intfloat/e5-small");

		assertThat(metadata.get("vector-type").toString()).as("Vector type in metadata should be PG_ARRAY")
			.isEqualTo(VectorType.PG_ARRAY.name());

		assertThat(metadata.get("kwargs").toString()).as("kwargs in metadata should be '{\"device\":\"cpu\"}'")
			.isEqualTo("{\"device\":\"cpu\"}");

		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(384);
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).hasSize(384);
		assertThat(embeddingResponse.getResults().get(2).getIndex()).isEqualTo(2);
		assertThat(embeddingResponse.getResults().get(2).getOutput()).hasSize(384);
	}

	@Test
	void dimensions() {
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(this.jdbcTemplate,
				PostgresMlEmbeddingOptions.builder().build(), true);
		embeddingModel.afterPropertiesSet();
		Assertions.assertThat(embeddingModel.dimensions()).isEqualTo(768);
		// cached
		Assertions.assertThat(embeddingModel.dimensions()).isEqualTo(768);
	}

	@SpringBootApplication
	public static class TestApplication {

	}

}
