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

package org.springframework.ai.vectorstore.qdrant;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Metadata filters with parentheses and {@code NOT} against a real Qdrant, for search and
 * delete. Needs no {@code OPENAI_API_KEY}: the filters, not the vectors, decide the
 * result, so a {@link FixedDimensionEmbeddingModel} is enough.
 *
 * @author Rohit Behera
 */
@Testcontainers
class QdrantVectorStoreFilterIT {

	private static final String COLLECTION_NAME = "test_collection_filter";

	@Container
	static QdrantContainer qdrantContainer = new QdrantContainer(QdrantImage.DEFAULT_IMAGE);

	private QdrantClient client;

	private QdrantVectorStore vectorStore;

	@BeforeEach
	void createStore() throws Exception {
		this.client = new QdrantClient(
				QdrantGrpcClient.newBuilder(qdrantContainer.getHost(), qdrantContainer.getGrpcPort(), false).build());
		if (this.client.listCollectionsAsync().get().contains(COLLECTION_NAME)) {
			this.client.deleteCollectionAsync(COLLECTION_NAME).get();
		}
		this.vectorStore = QdrantVectorStore.builder(this.client, new FixedDimensionEmbeddingModel(4))
			.collectionName(COLLECTION_NAME)
			.initializeSchema(true)
			.build();
		this.vectorStore.afterPropertiesSet();
		this.vectorStore.add(List.of(document("bg-2021-drama", "BG", 2021, "drama"),
				document("bg-2018-comedy", "BG", 2018, "comedy"), document("nl-2022-comedy", "NL", 2022, "comedy"),
				document("us-2017-drama", "US", 2017, "drama"), document("us-2023-comedy", "US", 2023, "comedy"),
				document("bg-2022-comedy", "BG", 2022, "comedy")));
	}

	@AfterEach
	void closeClient() {
		this.client.close();
	}

	static Stream<Arguments> groupedFilters() {
		return Stream.of(
				Arguments.of("country == 'BG' && (year >= 2020 || genre == 'drama')",
						List.of("bg-2021-drama", "bg-2022-comedy")),
				Arguments.of("(country == 'BG' && year >= 2020) || genre == 'drama'",
						List.of("bg-2021-drama", "bg-2022-comedy", "us-2017-drama")),
				Arguments.of("genre == 'comedy' && (country == 'NL' || year < 2019)",
						List.of("bg-2018-comedy", "nl-2022-comedy")),
				Arguments.of("genre == 'comedy' && (country == 'NL' || (year < 2019 && country == 'BG'))",
						List.of("bg-2018-comedy", "nl-2022-comedy")),
				Arguments.of("NOT country == 'BG'", List.of("nl-2022-comedy", "us-2017-drama", "us-2023-comedy")),
				Arguments.of("NOT (country == 'BG' || genre == 'drama')", List.of("nl-2022-comedy", "us-2023-comedy")));
	}

	@ParameterizedTest
	@MethodSource("groupedFilters")
	void searchWithGroupedFilter(String filter, List<String> expected) {
		assertThat(names(filter)).as(filter).containsExactlyInAnyOrderElementsOf(expected);
	}

	@Test
	void deleteWithGroupedFilterRemovesOnlyMatchingDocuments() {
		this.vectorStore.delete("(country == 'BG' && year >= 2020) || genre == 'drama'");

		assertThat(names(null)).containsExactlyInAnyOrder("bg-2018-comedy", "nl-2022-comedy", "us-2023-comedy");
	}

	private List<String> names(String filter) {
		SearchRequest.Builder request = SearchRequest.builder().query("any").topK(100).similarityThresholdAll();
		if (filter != null) {
			request.filterExpression(filter);
		}
		return this.vectorStore.similaritySearch(request.build())
			.stream()
			.map(document -> (String) document.getMetadata().get("name"))
			.toList();
	}

	private static Document document(String name, String country, int year, String genre) {
		return Document.builder()
			.id(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)).toString())
			.text(name)
			.metadata(Map.of("name", name, "country", country, "year", year, "genre", genre))
			.build();
	}

}
