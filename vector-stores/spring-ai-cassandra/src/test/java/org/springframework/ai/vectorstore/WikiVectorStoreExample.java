/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.vectorstore;

import java.util.List;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.type.DataTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.transformers.TransformersEmbeddingClient;
import org.springframework.ai.vectorstore.CassandraVectorStoreConfig.SchemaColumn;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Example integration-test to use against the schema and full wiki datasets in sstable
 * format available from https://github.com/datastax-labs/colbert-wikipedia-data
 *
 * Use `mvn failsafe:integration-test -Dit.test=WikiVectorStoreExample`
 *
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@Testcontainers
class WikiVectorStoreExample {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@Test
	void ensureBeanGetsCreated() {
		this.contextRunner.run(context -> {
			CassandraVectorStore store = context.getBean(CassandraVectorStore.class);
			Assertions.assertNotNull(store);
			store.checkSchemaValid();

			store.similaritySearch(SearchRequest.query("Spring").withTopK(1));
		});
	}

	@Test
	void search() {
		this.contextRunner.run(context -> {
			CassandraVectorStore store = context.getBean(CassandraVectorStore.class);
			Assertions.assertNotNull(store);
			store.checkSchemaValid();

			var results = store.similaritySearch(SearchRequest.query("Spring").withTopK(1));
			assertThat(results).hasSize(1);
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration(exclude = { DataSourceAutoConfiguration.class })
	public static class TestApplication {

		@Bean
		public CassandraVectorStore store(CqlSession cqlSession, EmbeddingClient embeddingClient) {

			CassandraVectorStoreConfig conf = CassandraVectorStoreConfig.builder()
				.withCqlSession(cqlSession)
				.withKeyspaceName("wikidata")
				.withTableName("articles")

				.withPartitionKeys(List.of(new SchemaColumn("wiki", DataTypes.TEXT),
						new SchemaColumn("language", DataTypes.TEXT), new SchemaColumn("title", DataTypes.TEXT)))

				.withClusteringKeys(List.of(new SchemaColumn("chunk_no", DataTypes.INT),
						new SchemaColumn("bert_embedding_no", DataTypes.INT)))

				.withContentColumnName("body")
				.withEmbeddingColumnName("all_minilm_l6_v2_embedding")
				.withIndexName("all_minilm_l6_v2_ann")
				.disallowSchemaChanges()

				.addMetadataColumn(new SchemaColumn("revision", DataTypes.INT), new SchemaColumn("id", DataTypes.INT))

				.withPrimaryKeyTranslator((List<Object> primaryKeys) -> {
					if (primaryKeys.isEmpty()) {
						return "test§¶0";
					}
					return format("%s§¶%s", primaryKeys.get(2), primaryKeys.get(3));
				})

				.withDocumentIdTranslator((id) -> {
					String[] parts = id.split("§¶");
					String title = parts[0];
					int chunk_no = 0 < parts.length ? Integer.parseInt(parts[1]) : 0;
					return List.of("simplewiki", "en", title, chunk_no, 0);
				})
				.build();

			return new CassandraVectorStore(conf, embeddingClient());
		}

		@Bean
		public EmbeddingClient embeddingClient() {
			// default is ONNX all-MiniLM-L6-v2 which is what we want
			return new TransformersEmbeddingClient();
		}

		@Bean
		public CqlSession cqlSession() {
			return new CqlSessionBuilder()
				// presumes a local C* cluster is running
				.build();
		}

	}

}
