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

package org.springframework.ai.vectorstore.pgvector;

import java.util.List;
import java.util.Map;
import java.util.Random;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgVectorType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests covering the non-default pgvector column types ({@code halfvec},
 * {@code bit}, {@code sparsevec}) and their supported distance metrics.
 */
@Testcontainers
class PgVectorStoreExtendedTypesIT {

	private static final int DIMENSIONS = 64;

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(PgVectorImage.DEFAULT_IMAGE)
		.withUsername("postgres")
		.withPassword("postgres");

	private final HikariDataSource dataSource = dataSource();

	private final JdbcTemplate jdbcTemplate = new JdbcTemplate(this.dataSource);

	private final DeterministicEmbeddingModel embeddingModel = new DeterministicEmbeddingModel(DIMENSIONS);

	@AfterEach
	void cleanup() {
		this.jdbcTemplate.execute("DROP TABLE IF EXISTS vector_store");
		this.dataSource.close();
	}

	static List<Arguments> combinations() {
		return List.of(Arguments.of(PgVectorType.VECTOR, PgDistanceType.COSINE_DISTANCE),
				Arguments.of(PgVectorType.VECTOR, PgDistanceType.L1_DISTANCE),
				Arguments.of(PgVectorType.HALFVEC, PgDistanceType.COSINE_DISTANCE),
				Arguments.of(PgVectorType.HALFVEC, PgDistanceType.EUCLIDEAN_DISTANCE),
				Arguments.of(PgVectorType.HALFVEC, PgDistanceType.NEGATIVE_INNER_PRODUCT),
				Arguments.of(PgVectorType.HALFVEC, PgDistanceType.L1_DISTANCE),
				Arguments.of(PgVectorType.SPARSEVEC, PgDistanceType.COSINE_DISTANCE),
				Arguments.of(PgVectorType.SPARSEVEC, PgDistanceType.L1_DISTANCE),
				Arguments.of(PgVectorType.BIT, PgDistanceType.HAMMING_DISTANCE),
				Arguments.of(PgVectorType.BIT, PgDistanceType.JACCARD_DISTANCE));
	}

	@ParameterizedTest(name = "{0} + {1}")
	@MethodSource("combinations")
	void addAndSearchWithType(PgVectorType vectorType, PgDistanceType distanceType) {
		PgVectorStore store = PgVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.vectorType(vectorType)
			.distanceType(distanceType)
			.dimensions(DIMENSIONS)
			.initializeSchema(true)
			.removeExistingVectorStoreTable(true)
			.build();
		store.afterPropertiesSet();

		Document d1 = new Document("alpha", Map.of("tag", "a"));
		Document d2 = new Document("beta", Map.of("tag", "b"));
		Document d3 = new Document("gamma", Map.of("tag", "c"));
		store.add(List.of(d1, d2, d3));

		// The underlying SQL applies `WHERE distance < (1 - similarityThreshold)`, and
		// distance ranges differ by metric (cosine 0..2, L1/Hamming unbounded,
		// Jaccard 0..1). We can't reliably make the 3-doc corpus all slip under a
		// cutoff of 1 for every metric, so assert only that the exact-match query
		// returns the queried doc as the top hit, and that the DDL uses the right
		// column type.
		List<Document> results = store.similaritySearch(SearchRequest.builder().query("alpha").topK(3).build());
		assertThat(results).isNotEmpty();
		assertThat(results.get(0).getId()).isEqualTo(d1.getId());

		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT udt_name FROM information_schema.columns WHERE table_name = 'vector_store' AND column_name = 'embedding'",
				String.class))
			.isEqualTo(vectorType.name().toLowerCase());

		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM vector_store", Integer.class)).isEqualTo(3);
	}

	private HikariDataSource dataSource() {
		HikariDataSource ds = new HikariDataSource();
		ds.setJdbcUrl(postgresContainer.getJdbcUrl());
		ds.setUsername(postgresContainer.getUsername());
		ds.setPassword(postgresContainer.getPassword());
		return ds;
	}

	static class DeterministicEmbeddingModel extends AbstractEmbeddingModel {

		private final int dimensions;

		DeterministicEmbeddingModel(int dimensions) {
			this.dimensions = dimensions;
		}

		@Override
		public int dimensions() {
			return this.dimensions;
		}

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			List<Embedding> embeddings = request.getInstructions()
				.stream()
				.map(this::embedText)
				.map(v -> new Embedding(v, 0))
				.toList();
			return new EmbeddingResponse(embeddings);
		}

		@Override
		public float[] embed(Document document) {
			return embedText(document.getText());
		}

		private float[] embedText(String text) {
			Random rnd = new Random(text.hashCode());
			float[] v = new float[this.dimensions];
			double norm = 0;
			for (int i = 0; i < this.dimensions; i++) {
				v[i] = (float) (rnd.nextGaussian());
				norm += v[i] * v[i];
			}
			float inv = (float) (1.0 / Math.sqrt(norm));
			for (int i = 0; i < this.dimensions; i++) {
				v[i] *= inv;
			}
			return v;
		}

	}

}
