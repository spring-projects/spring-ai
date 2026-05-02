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

package org.springframework.ai.vectorstore.oracle;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Anders Swanson
 */
class OracleVectorStoreBuilderTests {

	private final JdbcTemplate jdbcTemplate = new JdbcTemplate();

	private final EmbeddingModel embeddingModel = new TestEmbeddingModel();

	@Test
	void shouldUseDefaultIndexConfiguration() {
		OracleVectorStore vectorStore = OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel).build();

		assertThat(vectorStore.getIndexType()).isEqualTo(OracleVectorStore.DEFAULT_INDEX_TYPE);
		assertThat(vectorStore.getHnswNeighbors()).isEqualTo(OracleVectorStore.DEFAULT_HNSW_NEIGHBORS);
		assertThat(vectorStore.getHnswEfConstruction()).isEqualTo(OracleVectorStore.DEFAULT_HNSW_EF_CONSTRUCTION);
		assertThat(vectorStore.getIvfNeighborPartitions()).isEqualTo(OracleVectorStore.DEFAULT_IVF_NEIGHBOR_PARTITIONS);
		assertThat(vectorStore.getIvfSamplePerPartition())
			.isEqualTo(OracleVectorStore.DEFAULT_IVF_SAMPLE_PER_PARTITION);
		assertThat(vectorStore.getIvfMinVectorsPerPartition())
			.isEqualTo(OracleVectorStore.DEFAULT_IVF_MIN_VECTORS_PER_PARTITION);
	}

	@Test
	void shouldValidateHnswNeighbors() {
		assertThatThrownBy(
				() -> OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel).hnswNeighbors(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("HNSW neighbors must be greater than 0");
	}

	@Test
	void shouldValidateHnswEfConstruction() {
		assertThatThrownBy(
				() -> OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel).hnswEfConstruction(0).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("HNSW efConstruction must be greater than 0");
	}

	@Test
	void shouldValidateIvfNeighborPartitions() {
		assertThatThrownBy(() -> OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.ivfNeighborPartitions(0)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("IVF neighbor partitions must be greater than 0");
	}

	@Test
	void shouldAllowIvfOptionalSentinelValues() {
		OracleVectorStore vectorStore = OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.ivfSamplePerPartition(OracleVectorStore.DEFAULT_IVF_SAMPLE_PER_PARTITION)
			.ivfMinVectorsPerPartition(OracleVectorStore.DEFAULT_IVF_MIN_VECTORS_PER_PARTITION)
			.build();

		assertThat(vectorStore.getIvfSamplePerPartition())
			.isEqualTo(OracleVectorStore.DEFAULT_IVF_SAMPLE_PER_PARTITION);
		assertThat(vectorStore.getIvfMinVectorsPerPartition())
			.isEqualTo(OracleVectorStore.DEFAULT_IVF_MIN_VECTORS_PER_PARTITION);
	}

	@Test
	void shouldGenerateHnswIndexSql() {
		OracleVectorStore vectorStore = OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.tableName("custom_vectors")
			.indexType(OracleVectorStore.OracleVectorStoreIndexType.HNSW)
			.distanceType(OracleVectorStore.OracleVectorStoreDistanceType.COSINE)
			.hnswNeighbors(64)
			.hnswEfConstruction(300)
			.build();

		String sql = invokePrivateSqlMethod(vectorStore, "createHnswIndexSql");

		assertThat(sql).contains("type HNSW, neighbors 64, efconstruction 300");
		assertThat(sql).contains("with target accuracy 95");
		assertThat(sql).contains("organization inmemory neighbor graph");
		assertThat(sql).contains("vector_index_custom_vectors");
	}

	@Test
	void shouldGenerateIvfIndexSqlWithOptionalParameters() {
		OracleVectorStore vectorStore = OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.tableName("custom_vectors")
			.indexType(OracleVectorStore.OracleVectorStoreIndexType.IVF)
			.distanceType(OracleVectorStore.OracleVectorStoreDistanceType.COSINE)
			.ivfNeighborPartitions(32)
			.ivfSamplePerPartition(16)
			.ivfMinVectorsPerPartition(8)
			.build();

		String sql = invokePrivateSqlMethod(vectorStore, "createIvfIndexSql");

		assertThat(sql)
			.contains("type IVF, neighbor partitions 32, sample_per_partition 16, min_vectors_per_partition 8");
		assertThat(sql).contains("with target accuracy 95");
		assertThat(sql).contains("organization neighbor partitions");
	}

	@Test
	void shouldGenerateIvfIndexSqlWithoutOptionalParameters() {
		OracleVectorStore vectorStore = OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.tableName("custom_vectors")
			.indexType(OracleVectorStore.OracleVectorStoreIndexType.IVF)
			.distanceType(OracleVectorStore.OracleVectorStoreDistanceType.COSINE)
			.ivfNeighborPartitions(32)
			.build();

		String sql = invokePrivateSqlMethod(vectorStore, "createIvfIndexSql");

		assertThat(sql).contains("type IVF, neighbor partitions 32");
		assertThat(sql).doesNotContain("sample_per_partition");
		assertThat(sql).doesNotContain("min_vectors_per_partition");
	}

	@Test
	void shouldValidateIvfSamplePerPartition() {
		assertThatThrownBy(() -> OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.ivfSamplePerPartition(0)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("IVF sample per partition must be greater than 0");

		assertThatThrownBy(() -> OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.ivfSamplePerPartition(-2)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("IVF sample per partition must be greater than 0");
	}

	@Test
	void shouldValidateIvfMinVectorsPerPartition() {
		assertThatThrownBy(() -> OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.ivfMinVectorsPerPartition(0)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("IVF min vectors per partition must be greater than 0");

		assertThatThrownBy(() -> OracleVectorStore.builder(this.jdbcTemplate, this.embeddingModel)
			.ivfMinVectorsPerPartition(-2)
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("IVF min vectors per partition must be greater than 0");
	}

	private static String invokePrivateSqlMethod(OracleVectorStore vectorStore, String methodName) {
		try {
			Method method = OracleVectorStore.class.getDeclaredMethod(methodName);
			method.setAccessible(true);
			return (String) method.invoke(vectorStore);
		}
		catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	private static final class TestEmbeddingModel implements EmbeddingModel {

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			List<Embedding> embeddings = request.getInstructions()
				.stream()
				.map(text -> new Embedding(new float[] { 1.0f }, 0))
				.toList();
			return new EmbeddingResponse(embeddings);
		}

		@Override
		public float[] embed(Document document) {
			return new float[] { 1.0f };
		}

		@Override
		public int dimensions() {
			return 1;
		}

	}

}
