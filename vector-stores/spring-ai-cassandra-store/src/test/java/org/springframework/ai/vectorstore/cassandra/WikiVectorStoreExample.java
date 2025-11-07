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

package org.springframework.ai.vectorstore.cassandra;

import java.util.List;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.type.DataTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore.SchemaColumn;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example integration-test to use against the schema and full wiki datasets in stable
 * format available from https://github.com/datastax-labs/colbert-wikipedia-data
 *
 * Use `mvn failsafe:integration-test -Dit.test=WikiVectorStoreExample`
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@Testcontainers
@Disabled("This is an example, not a really a test as it requires external setup")
class WikiVectorStoreExample {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@Test
	void ensureBeanGetsCreated() {
		this.contextRunner.run(context -> {
			CassandraVectorStore store = context.getBean(CassandraVectorStore.class);
			Assertions.assertNotNull(store);
			store.checkSchemaValid();

			store.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
		});
	}

	@Test
	void search() {
		this.contextRunner.run(context -> {
			CassandraVectorStore store = context.getBean(CassandraVectorStore.class);
			Assertions.assertNotNull(store);
			store.checkSchemaValid();

			var results = store.similaritySearch(SearchRequest.builder().query("Spring").topK(1).build());
			assertThat(results).hasSize(1);
		});
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public CassandraVectorStore store(CqlSession cqlSession, EmbeddingModel embeddingModel) {

			List<SchemaColumn> partitionColumns = List.of(new SchemaColumn("wiki", DataTypes.TEXT),
					new SchemaColumn("language", DataTypes.TEXT), new SchemaColumn("title", DataTypes.TEXT));

			List<SchemaColumn> clusteringColumns = List.of(new SchemaColumn("chunk_no", DataTypes.INT),
					new SchemaColumn("bert_embedding_no", DataTypes.INT));

			List<SchemaColumn> extraColumns = List.of(new SchemaColumn("revision", DataTypes.INT),
					new SchemaColumn("id", DataTypes.INT));

			return CassandraVectorStore.builder(embeddingModel)
				.session(cqlSession)
				.keyspace("wikidata")
				.table("articles")
				.partitionKeys(partitionColumns)
				.clusteringKeys(clusteringColumns)
				.contentColumnName("body")
				.embeddingColumnName("all_minilm_l6_v2_embedding")
				.indexName("all_minilm_l6_v2_ann")
				.initializeSchema(false)
				.addMetadataColumns(extraColumns)
				.primaryKeyTranslator((List<Object> primaryKeys) -> {
					// the deliminator used to join fields together into the document's id
					// is arbitrary, here "§¶" is used
					if (primaryKeys.isEmpty()) {
						return "test§¶0";
					}
					return String.format("%s§¶%s", primaryKeys.get(2), primaryKeys.get(3));
				})
				.documentIdTranslator(id -> {
					String[] parts = id.split("§¶");
					String title = parts[0];
					int chunk_no = 0 < parts.length ? Integer.parseInt(parts[1]) : 0;
					return List.of("simplewiki", "en", title, chunk_no, 0);
				})
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			// default is ONNX all-MiniLM-L6-v2 which is what we want
			return new TransformersEmbeddingModel();
		}

		@Bean
		public CqlSession cqlSession() {
			return new CqlSessionBuilder()
				// presumes a local C* cluster is running
				.build();
		}

	}

}
