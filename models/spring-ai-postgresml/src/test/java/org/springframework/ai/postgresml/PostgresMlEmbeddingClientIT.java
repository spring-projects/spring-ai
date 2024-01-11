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
import org.springframework.ai.embedding.EmbeddingResponse;
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
class PostgresMlEmbeddingClientIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
			DockerImageName.parse("ghcr.io/postgresml/postgresml:2.7.3").asCompatibleSubstituteFor("postgres"))
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
		PostgresMlEmbeddingClient embeddingClient = new PostgresMlEmbeddingClient(this.jdbcTemplate);
		embeddingClient.afterPropertiesSet();
		List<Double> embed = embeddingClient.embed("Hello World!");
		assertThat(embed).hasSize(768);
		// embeddingClient.dropPgmlExtension();
	}

	@Test
	void embedWithPgVector() {
		PostgresMlEmbeddingClient embeddingClient = new PostgresMlEmbeddingClient(this.jdbcTemplate,
				"distilbert-base-uncased", PostgresMlEmbeddingClient.VectorType.PG_VECTOR);
		embeddingClient.afterPropertiesSet();
		List<Double> embed = embeddingClient.embed(new Document("Hello World!"));
		assertThat(embed).hasSize(768);
		// embeddingClient.dropPgmlExtension();
	}

	@Test
	void embedWithDifferentModel() {
		PostgresMlEmbeddingClient embeddingClient = new PostgresMlEmbeddingClient(this.jdbcTemplate,
				"intfloat/e5-small");
		embeddingClient.afterPropertiesSet();
		List<Double> embed = embeddingClient.embed(new Document("Hello World!"));
		assertThat(embed).hasSize(384);
		// embeddingClient.dropPgmlExtension();
	}

	@Test
	void embedWithKwargs() {
		PostgresMlEmbeddingClient embeddingClient = new PostgresMlEmbeddingClient(this.jdbcTemplate,
				"distilbert-base-uncased", PostgresMlEmbeddingClient.VectorType.PG_ARRAY, Map.of("device", "cpu"),
				MetadataMode.EMBED);
		embeddingClient.afterPropertiesSet();
		List<Double> embed = embeddingClient.embed(new Document("Hello World!"));
		assertThat(embed).hasSize(768);
		// embeddingClient.dropPgmlExtension();
	}

	@ParameterizedTest
	@ValueSource(strings = { "PG_ARRAY", "PG_VECTOR" })
	void embedForResponse(String vectorType) {
		PostgresMlEmbeddingClient embeddingClient = new PostgresMlEmbeddingClient(this.jdbcTemplate,
				"distilbert-base-uncased", PostgresMlEmbeddingClient.VectorType.valueOf(vectorType));
		embeddingClient.afterPropertiesSet();
		EmbeddingResponse embeddingResponse = embeddingClient
			.embedForResponse(List.of("Hello World!", "Spring AI!", "LLM!"));
		assertThat(embeddingResponse).isNotNull();
		assertThat(embeddingResponse.getData()).hasSize(3);
		assertThat(embeddingResponse.getMetadata()).containsExactlyEntriesOf(
				Map.of("transformer", "distilbert-base-uncased", "vector-type", vectorType, "kwargs", "{}"));
		assertThat(embeddingResponse.getData().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getData().get(0).getEmbedding()).hasSize(768);
		assertThat(embeddingResponse.getData().get(1).getIndex()).isEqualTo(1);
		assertThat(embeddingResponse.getData().get(1).getEmbedding()).hasSize(768);
		assertThat(embeddingResponse.getData().get(2).getIndex()).isEqualTo(2);
		assertThat(embeddingResponse.getData().get(2).getEmbedding()).hasSize(768);
		// embeddingClient.dropPgmlExtension();
	}

	@Test
	void dimensions() {
		PostgresMlEmbeddingClient embeddingClient = new PostgresMlEmbeddingClient(this.jdbcTemplate);
		embeddingClient.afterPropertiesSet();
		Assertions.assertThat(embeddingClient.dimensions()).isEqualTo(768);
		// cached
		Assertions.assertThat(embeddingClient.dimensions()).isEqualTo(768);
	}

	@SpringBootApplication
	public static class TestApplication {

	}

}