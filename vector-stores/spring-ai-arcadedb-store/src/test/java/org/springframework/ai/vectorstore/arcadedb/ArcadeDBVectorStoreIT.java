/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.arcadedb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.ExpressionType;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.Filter.Value;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ArcadeDBVectorStore}. Uses an embedded ArcadeDB
 * database — no Docker or external services required.
 *
 * @author Luca Garulli
 */
class ArcadeDBVectorStoreIT {

	private static final int DIMENSIONS = 128;

	private Path tempDbPath;

	private ArcadeDBVectorStore vectorStore;

	private final EmbeddingModel testEmbeddingModel = new TestEmbeddingModel(
			DIMENSIONS);

	@BeforeEach
	void setUp() throws IOException {
		tempDbPath = Files.createTempDirectory("arcadedb-vectorstore-test");
		vectorStore = ArcadeDBVectorStore.builder(testEmbeddingModel)
				.databasePath(tempDbPath.toString())
				.embeddingDimension(DIMENSIONS)
				.build();
		vectorStore.afterPropertiesSet();
	}

	@AfterEach
	void tearDown() throws IOException {
		if (vectorStore != null) {
			vectorStore.close();
		}
		if (tempDbPath != null) {
			deleteDirectory(tempDbPath);
		}
	}

	@Test
	void addAndSearchDocuments() {
		Document doc1 = Document.builder()
				.id("1")
				.text("Spring AI is great")
				.metadata(Map.of("category", "ai"))
				.build();
		Document doc2 = Document.builder()
				.id("2")
				.text("ArcadeDB is an embedded database")
				.metadata(Map.of("category", "database"))
				.build();

		vectorStore.add(List.of(doc1, doc2));

		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder().query("AI framework").topK(10).build());
		assertThat(results).isNotEmpty();
		assertThat(results).extracting(Document::getId).contains("1", "2");
	}

	@Test
	void searchWithSimilarityThreshold() {
		Document doc = Document.builder()
				.id("1")
				.text("test document")
				.build();
		vectorStore.add(List.of(doc));

		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query("test")
						.topK(10)
						.similarityThreshold(0.9999)
						.build());
		assertThat(results.size()).isLessThanOrEqualTo(1);
	}

	@Test
	void searchWithMetadataFilter() {
		Document doc1 = Document.builder()
				.id("1")
				.text("AI document")
				.metadata(Map.of("category", "ai", "priority", 1))
				.build();
		Document doc2 = Document.builder()
				.id("2")
				.text("DB document")
				.metadata(Map.of("category", "database", "priority", 2))
				.build();

		vectorStore.add(List.of(doc1, doc2));

		Expression filter = new Expression(ExpressionType.EQ,
				new Key("category"), new Value("ai"));
		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder()
						.query("document")
						.topK(10)
						.filterExpression(filter)
						.build());

		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo("1");
	}

	@Test
	void deleteById() {
		Document doc = Document.builder()
				.id("to-delete")
				.text("delete me")
				.build();
		vectorStore.add(List.of(doc));

		vectorStore.delete(List.of("to-delete"));

		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder().query("delete").topK(10).build());
		assertThat(results).isEmpty();
	}

	@Test
	void deleteByFilter() {
		Document doc1 = Document.builder()
				.id("1")
				.text("keep this")
				.metadata(Map.of("status", "active"))
				.build();
		Document doc2 = Document.builder()
				.id("2")
				.text("remove this")
				.metadata(Map.of("status", "archived"))
				.build();

		vectorStore.add(List.of(doc1, doc2));

		Expression filter = new Expression(ExpressionType.EQ,
				new Key("status"), new Value("archived"));
		vectorStore.delete(filter);

		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder().query("this").topK(10).build());
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getId()).isEqualTo("1");
	}

	@Test
	void upsertDocument() {
		Document original = Document.builder()
				.id("upsert-id")
				.text("original content")
				.build();
		vectorStore.add(List.of(original));

		Document updated = Document.builder()
				.id("upsert-id")
				.text("updated content")
				.build();
		vectorStore.add(List.of(updated));

		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder().query("content").topK(10).build());
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getText()).isEqualTo("updated content");
	}

	@Test
	void getNativeClient() {
		assertThat(vectorStore
				.<com.arcadedb.database.Database>getNativeClient()).isPresent();
		assertThat(vectorStore
				.<com.arcadedb.database.Database>getNativeClient()
				.get().isOpen()).isTrue();
	}

	private static void deleteDirectory(Path path) throws IOException {
		if (Files.exists(path)) {
			Files.walk(path)
					.sorted(Comparator.reverseOrder())
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						}
						catch (IOException ex) {
							// best effort cleanup
						}
					});
		}
	}

}
