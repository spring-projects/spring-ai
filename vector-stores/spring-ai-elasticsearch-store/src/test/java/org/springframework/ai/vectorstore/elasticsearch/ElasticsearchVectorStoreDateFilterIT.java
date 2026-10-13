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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.json.jackson.Jackson3JsonpMapper;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
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
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that ISO-8601 date filters produce valid Elasticsearch query strings and match
 * documents whose metadata holds dates. Needs no {@code OPENAI_API_KEY}: a
 * {@link FixedDimensionEmbeddingModel} is used, so only Testcontainers Elasticsearch is
 * required.
 */
@Testcontainers
class ElasticsearchVectorStoreDateFilterIT {

	private static final int EMBEDDING_DIMENSIONS = 8;

	@Container
	private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
			ElasticsearchImage.DEFAULT_IMAGE)
		.withEnv("xpack.security.enabled", "false");

	private final Document jan7 = new Document("jan7", "first", Map.of("activationDate", "2024-01-07T14:29:12Z"));

	private final Document jan8 = new Document("jan8", "second", Map.of("activationDate", "2024-01-08T00:00:00Z"));

	private final Document jan9 = new Document("jan9", "third", Map.of("activationDate", "2024-01-09T08:15:30Z"));

	private ApplicationContextRunner getContextRunner() {
		return new ApplicationContextRunner().withUserConfiguration(TestApplication.class);
	}

	@BeforeEach
	void cleanDatabase() {
		getContextRunner().run(context -> {
			ElasticsearchClient client = context.getBean(ElasticsearchClient.class);
			List<String> indices = client.cat().indices().indices().stream().map(IndicesRecord::index).toList();
			if (!indices.isEmpty()) {
				client.indices().delete(del -> del.index(indices));
			}
		});
	}

	private void withStore(Consumer<ElasticsearchVectorStore> test) {
		getContextRunner().run(context -> {
			ElasticsearchVectorStore vectorStore = context.getBean(ElasticsearchVectorStore.class);
			ElasticsearchClient client = context.getBean(ElasticsearchClient.class);
			vectorStore.add(List.of(this.jan7, this.jan8, this.jan9));
			client.indices().refresh(r -> r.index("spring-ai-document-index"));
			test.accept(vectorStore);
		});
	}

	private static List<String> ids(List<Document> documents) {
		return documents.stream().map(Document::getId).toList();
	}

	private static SearchRequest.Builder request() {
		return SearchRequest.builder().query("anything").topK(10).similarityThresholdAll();
	}

	@Test
	void equalsWithIsoDateString() {
		withStore(store -> {
			List<Document> results = store
				.similaritySearch(request().filterExpression("activationDate == '2024-01-07T14:29:12Z'").build());

			assertThat(ids(results)).containsExactly("jan7");
		});
	}

	@Test
	void greaterThanOrEqualWithIsoDateString() {
		withStore(store -> {
			FilterExpressionBuilder b = new FilterExpressionBuilder();
			List<Document> results = store.similaritySearch(
					request().filterExpression(b.gte("activationDate", "2024-01-08T00:00:00Z").build()).build());

			assertThat(ids(results)).containsExactlyInAnyOrder("jan8", "jan9");
		});
	}

	@Test
	void inWithIsoDateStrings() {
		withStore(store -> {
			List<Document> results = store.similaritySearch(
					request().filterExpression("activationDate in ['2024-01-07T14:29:12Z', '2024-01-09T08:15:30Z']")
						.build());

			assertThat(ids(results)).containsExactlyInAnyOrder("jan7", "jan9");
		});
	}

	@Test
	void deleteWithIsoDateFilter() {
		withStore(store -> {
			store.delete("activationDate == '2024-01-08T00:00:00Z'");
			getContextRunner().run(context -> context.getBean(ElasticsearchClient.class)
				.indices()
				.refresh(r -> r.index("spring-ai-document-index")));

			List<Document> results = store.similaritySearch(request().build());
			assertThat(ids(results)).containsExactlyInAnyOrder("jan7", "jan9");
		});
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
			return Rest5Client.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress())).build();
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
