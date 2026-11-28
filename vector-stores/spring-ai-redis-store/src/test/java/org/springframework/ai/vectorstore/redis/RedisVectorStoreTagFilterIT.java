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

package org.springframework.ai.vectorstore.redis;

import java.util.List;
import java.util.Map;

import com.redis.testcontainers.RedisStackContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.RedisClient;

import org.springframework.ai.document.Document;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAG filter values with punctuation against a real Redis Stack, for search and delete.
 * Needs no hosted embedding provider: the filters, not the vectors, decide the result, so
 * a {@link FixedDimensionEmbeddingModel} is enough.
 *
 * @author Rohit Behera
 */
@Testcontainers
class RedisVectorStoreTagFilterIT {

	private static final List<String> SOURCES = List.of("report.pdf", "report pdf", "a,b", "a b", "v1.2-rc",
			"jane@example.com", "https://example.com/a?b=1", "C:\\docs\\f.txt", "user:42", "x;y", "50%", "~home",
			"\"quoted\"", "1+1=2", "a&b!", "#tag", "a<b>", "a/b", "q?", "`tick`", "^up", "under_score");

	@Container
	static RedisStackContainer redisContainer = new RedisStackContainer(
			RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG));

	private RedisClient client;

	private RedisVectorStore vectorStore;

	@BeforeEach
	void createStore() throws Exception {
		this.client = RedisClient.builder()
			.hostAndPort(redisContainer.getHost(), redisContainer.getFirstMappedPort())
			.build();
		this.client.flushAll();
		this.vectorStore = RedisVectorStore.builder(this.client, new FixedDimensionEmbeddingModel(4))
			.metadataFields(MetadataField.tag("source"))
			.initializeSchema(true)
			.build();
		this.vectorStore.afterPropertiesSet();
		this.vectorStore.add(SOURCES.stream()
			.map(source -> Document.builder()
				.id("doc-" + SOURCES.indexOf(source))
				.text(source)
				.metadata(Map.of("source", source))
				.build())
			.toList());
	}

	@AfterEach
	void closeClient() {
		this.client.close();
	}

	static List<String> sources() {
		return SOURCES;
	}

	@ParameterizedTest
	@MethodSource("sources")
	void searchMatchesOnlyTheExactTagValue(String source) {
		SearchRequest request = SearchRequest.builder()
			.query("any")
			.topK(100)
			.similarityThresholdAll()
			.filterExpression(new FilterExpressionBuilder().eq("source", source).build())
			.build();

		assertThat(sources(this.vectorStore.similaritySearch(request))).containsExactly(source);
	}

	@ParameterizedTest
	@MethodSource("sources")
	void deleteRemovesOnlyTheExactTagValue(String source) {
		this.vectorStore.delete(new FilterExpressionBuilder().eq("source", source).build());

		assertThat(sources(this.vectorStore
			.similaritySearch(SearchRequest.builder().query("any").topK(100).similarityThresholdAll().build())))
			.containsExactlyInAnyOrderElementsOf(SOURCES.stream().filter(s -> !s.equals(source)).toList());
	}

	@Test
	void inMatchesOnlyTheListedTagValues() {
		SearchRequest request = SearchRequest.builder()
			.query("any")
			.topK(100)
			.similarityThresholdAll()
			.filterExpression(new FilterExpressionBuilder().in("source", "report.pdf", "jane@example.com").build())
			.build();

		assertThat(sources(this.vectorStore.similaritySearch(request))).containsExactlyInAnyOrder("report.pdf",
				"jane@example.com");
	}

	@Test
	void deleteWithNotEqualKeepsOnlyTheExcludedTagValue() {
		this.vectorStore.delete(new FilterExpressionBuilder().ne("source", "report.pdf").build());

		assertThat(sources(this.vectorStore
			.similaritySearch(SearchRequest.builder().query("any").topK(100).similarityThresholdAll().build())))
			.containsExactly("report.pdf");
	}

	private static List<String> sources(List<Document> documents) {
		return documents.stream().map(document -> (String) document.getMetadata().get("source")).toList();
	}

}
