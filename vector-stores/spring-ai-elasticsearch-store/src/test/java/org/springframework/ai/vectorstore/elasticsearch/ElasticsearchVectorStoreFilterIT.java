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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.document.Document;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ElasticsearchVectorStoreFilterIT {

	@Container
	private static final ElasticsearchContainer elasticsearchContainer = new ElasticsearchContainer(
			ElasticsearchImage.DEFAULT_IMAGE)
		.withEnv("xpack.security.enabled", "false");

	@Test
	void notEqualWithPlainString() throws IOException, URISyntaxException {
		withStore(store -> {
			var b = new FilterExpressionBuilder();
			assertThat(search(store, b.ne("label", "second").build())).extracting(Document::getId)
				.containsExactlyInAnyOrder("first", "third");
		});
	}

	@Test
	void notEqualIncludesMissingMetadataField() throws IOException, URISyntaxException {
		withStore(store -> {
			store.add(List.of(new Document("missing", "anything", Map.of("year", 2018))));
			ElasticsearchClient client = store.<ElasticsearchClient>getNativeClient().orElseThrow();
			try {
				client.indices().refresh();
			}
			catch (IOException ex) {
				throw new RuntimeException(ex);
			}
			var b = new FilterExpressionBuilder();
			// NE is the complement of equality, as with NIN and MongoDB's $ne.
			assertThat(search(store, b.ne("label", "second").build())).extracting(Document::getId)
				.containsExactlyInAnyOrder("first", "third", "missing");
			assertThat(search(store, b.nin("label", "second").build())).extracting(Document::getId)
				.containsExactlyInAnyOrder("first", "third", "missing");
		});
	}

	@Test
	void notEqualWithNumberAndAnd() throws IOException, URISyntaxException {
		withStore(store -> {
			var b = new FilterExpressionBuilder();
			assertThat(search(store, b.ne("year", 2021).build())).extracting(Document::getId)
				.containsExactlyInAnyOrder("first", "third");
			assertThat(search(store, b.and(b.eq("a", 1), b.ne("label", "second")).build())).extracting(Document::getId)
				.containsExactly("first");
		});
	}

	@Test
	void notEqualWithOrAndGroups() throws IOException, URISyntaxException {
		withStore(store -> {
			var b = new FilterExpressionBuilder();
			// The excluded label must still match the other side of the disjunction.
			assertThat(search(store, b.or(b.ne("label", "second"), b.gt("year", 2020)).build()))
				.extracting(Document::getId)
				.containsExactlyInAnyOrder("first", "second", "third");
			assertThat(search(store, b.or(b.gt("year", 2020), b.ne("label", "second")).build()))
				.extracting(Document::getId)
				.containsExactlyInAnyOrder("first", "second", "third");
			assertThat(search(store,
					b.and(b.group(b.or(b.ne("label", "second"), b.gt("year", 2020))), b.eq("a", 1)).build()))
				.extracting(Document::getId)
				.containsExactlyInAnyOrder("first", "second");
		});
	}

	@Test
	void notEqualWithNestedNot() throws IOException, URISyntaxException {
		withStore(store -> {
			var b = new FilterExpressionBuilder();
			assertThat(search(store, b.not(b.group(b.eq("label", "second"))).build())).extracting(Document::getId)
				.containsExactlyInAnyOrder("first", "third");
		});
	}

	private List<Document> search(ElasticsearchVectorStore store, Filter.Expression expression) {
		return store.similaritySearch(SearchRequest.builder()
			.query("anything")
			.topK(10)
			.similarityThresholdAll()
			.filterExpression(expression)
			.build());
	}

	private void withStore(Consumer<ElasticsearchVectorStore> test) throws IOException, URISyntaxException {
		try (Rest5Client restClient = Rest5Client.builder(HttpHost.create(elasticsearchContainer.getHttpHostAddress()))
			.build()) {
			var options = new ElasticsearchVectorStoreOptions();
			options.setIndexName("ne-filter-" + UUID.randomUUID());
			options.setDimensions(4);
			var store = ElasticsearchVectorStore.builder(restClient, new FixedDimensionEmbeddingModel(4))
				.options(options)
				.initializeSchema(true)
				.build();
			store.afterPropertiesSet();
			ElasticsearchClient client = store.<ElasticsearchClient>getNativeClient().orElseThrow();
			try {
				store.add(List.of(new Document("first", "anything", Map.of("label", "first", "year", 2019, "a", 1)),
						new Document("second", "anything", Map.of("label", "second", "year", 2021, "a", 1)),
						new Document("third", "anything", Map.of("label", "third", "year", 2022, "a", 2))));
				client.indices().refresh(r -> r.index(options.getIndexName()));
				test.accept(store);
			}
			finally {
				client.indices().delete(r -> r.index(options.getIndexName()));
			}
		}
	}

}
