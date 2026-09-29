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

package org.springframework.ai.vectorstore.elasticsearch;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Metadata filter coverage for {@link ElasticsearchVectorStore}, asserting that an
 * equality filter on a string field matches that value exactly rather than any value
 * analysis happens to break into overlapping tokens.
 * <p>
 * A {@link FixedDimensionEmbeddingModel} stands in for a hosted embedding model, so this
 * needs only Testcontainers: every search reads the documents back with
 * {@code similarityThresholdAll()}, and the filter rather than the ranking decides the
 * result.
 *
 * @author Xuhan Zhuang
 */
@Testcontainers
class ElasticsearchVectorStoreFilterIT {

	private static final int EMBEDDING_DIMENSIONS = 4;

	private static final Document TOLKIEN = new Document("1", "The Hobbit", Map.of("author", "Tolkien", "year", 2020,
			"active", true, "tags", List.of("fantasy", "epic fantasy"), "editor", Map.of("name", "Tolkien and Lewis")));

	private static final Document LOWERCASE_TOLKIEN = new Document("2", "Leaf by Niggle",
			Map.of("author", "tolkien", "active", false, "tags", List.of("essay")));

	private static final Document TOLKIEN_AND_LEWIS = new Document("3", "Letters",
			Map.of("author", "Tolkien and Lewis", "year", 2021, "active", true, "tags", List.of("letters")));

	@Container
	private static final ElasticsearchContainer CONTAINER = new ElasticsearchContainer(ElasticsearchImage.DEFAULT_IMAGE)
		.withEnv("xpack.security.enabled", "false");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeEach
	void cleanDatabase() {
		this.contextRunner.run(context -> {
			ElasticsearchClient client = context.getBean(ElasticsearchClient.class);
			List<String> indices = client.cat().indices().indices().stream().map(IndicesRecord::index).toList();
			if (!indices.isEmpty()) {
				client.indices().delete(del -> del.index(indices));
			}
		});
	}

	@Test
	void equalityFilterMatchesTheExactStringValue() {
		withIndexedDocuments(vectorStore -> {
			assertThat(idsMatching(vectorStore, "author == 'Tolkien'")).containsExactly("1");
			assertThat(idsMatching(vectorStore, "author == 'tolkien'")).containsExactly("2");
			assertThat(idsMatching(vectorStore, "author == 'Tolkien and Lewis'")).containsExactly("3");
			assertThat(idsMatching(vectorStore, "author == 'TOLKIEN'")).isEmpty();
			assertThat(idsMatching(vectorStore, "author == 'Lewis'")).isEmpty();
		});
	}

	@Test
	void deleteByFilterRemovesOnlyTheExactStringValue() {
		withIndexedDocuments(vectorStore -> {
			vectorStore.delete("author == 'Tolkien'");

			Awaitility.await()
				.atMost(Duration.ofSeconds(30))
				.untilAsserted(() -> assertThat(allIds(vectorStore)).containsExactlyInAnyOrder("2", "3"));
		});
	}

	@Test
	void filterOperatorsMatchTheKeywordMapping() {
		withIndexedDocuments(vectorStore -> {
			assertThat(idsMatching(vectorStore, "author in ['Tolkien', 'tolkien']")).containsExactlyInAnyOrder("1",
					"2");
			assertThat(idsMatching(vectorStore, "author nin ['Tolkien']")).containsExactlyInAnyOrder("2", "3");
			assertThat(idsMatching(vectorStore, "tags == 'epic fantasy'")).containsExactly("1");
			assertThat(idsMatching(vectorStore, "editor.name == 'Tolkien and Lewis'")).containsExactly("1");
			assertThat(idsMatching(vectorStore, "editor.name == 'Tolkien'")).isEmpty();
			assertThat(idsMatching(vectorStore, "active == true")).containsExactlyInAnyOrder("1", "3");
			assertThat(idsMatching(vectorStore, "year == 2020")).containsExactly("1");
			assertThat(idsMatching(vectorStore, "year >= 2021")).containsExactly("3");
			assertThat(idsMatching(vectorStore, "year IS NULL")).containsExactly("2");
			assertThat(idsMatching(vectorStore, "year IS NOT NULL")).containsExactlyInAnyOrder("1", "3");
		});
	}

	@Test
	void addAcceptsAMetadataStringTooLongToIndexAsKeyword() {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			// Longer than the Lucene term limit, so it is stored but not indexed.
			String tooLong = "x".repeat(40000);

			vectorStore.add(List.of(new Document("1", "The Silmarillion", Map.of("author", tooLong))));

			Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
				List<Document> documents = vectorStore
					.similaritySearch(SearchRequest.builder().query("book").topK(10).similarityThresholdAll().build());
				assertThat(documents).singleElement()
					.extracting(document -> document.getMetadata().get("author"))
					.isEqualTo(tooLong);
			});
		});
	}

	private void withIndexedDocuments(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			vectorStore.add(List.of(TOLKIEN, LOWERCASE_TOLKIEN, TOLKIEN_AND_LEWIS));

			Awaitility.await()
				.atMost(Duration.ofSeconds(30))
				.untilAsserted(() -> assertThat(allIds(vectorStore)).hasSize(3));

			testFunction.accept(vectorStore);
		});
	}

	private List<String> allIds(VectorStore vectorStore) {
		return ids(vectorStore, SearchRequest.builder().query("book").topK(10).similarityThresholdAll());
	}

	private List<String> idsMatching(VectorStore vectorStore, String filterExpression) {
		return ids(vectorStore,
				SearchRequest.builder()
					.query("book")
					.topK(10)
					.similarityThresholdAll()
					.filterExpression(filterExpression));
	}

	private List<String> ids(VectorStore vectorStore, SearchRequest.Builder request) {
		return vectorStore.similaritySearch(request.build()).stream().map(Document::getId).toList();
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public ElasticsearchVectorStore vectorStore(EmbeddingModel embeddingModel, Rest5Client restClient) {
			ElasticsearchVectorStoreOptions options = new ElasticsearchVectorStoreOptions();
			options.setDimensions(EMBEDDING_DIMENSIONS);
			return ElasticsearchVectorStore.builder(restClient, embeddingModel)
				.initializeSchema(true)
				.options(options)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

		@Bean
		Rest5Client restClient() throws URISyntaxException {
			return Rest5Client.builder(HttpHost.create(CONTAINER.getHttpHostAddress())).build();
		}

		@Bean
		ElasticsearchClient elasticsearchClient(Rest5Client restClient) {
			JsonMapper jsonMapper = JsonMapper.builder()
				.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
			return new ElasticsearchClient(new Rest5ClientTransport(restClient, new Jackson3JsonpMapper(jsonMapper)));
		}

	}

}
