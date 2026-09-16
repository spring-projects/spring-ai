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

package org.springframework.ai.test.vectorstore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

/**
 * Shared verification suite for {@link VectorStore#upsert(List)} implementations, a
 * sibling to {@link BaseVectorStoreTests}. A concrete store test extends this class,
 * provides a configured store through {@link #executeTest(Consumer)}, and reports the
 * embedding dimension its store expects through {@link #embeddingDimensions()} so the
 * suite can build correctly sized caller vectors.
 * <p>
 * The read-back assertions rely on {@link SearchRequest.Builder#similarityThresholdAll()}
 * to return every stored row regardless of ranking, so they do not depend on the actual
 * embedding values.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
public abstract class AbstractVectorStoreUpsertTests {

	/**
	 * Number of distinct entries upserted by {@link #pairingAcrossBatches()}. Concrete
	 * store tests should configure their store with a smaller batch size than this so the
	 * upsert genuinely spans multiple write batches.
	 */
	protected static final int MULTI_BATCH_ENTRY_COUNT = 20;

	/**
	 * Execute a test function with a configured {@link VectorStore} instance. This method
	 * is responsible for providing a properly initialized store within the appropriate
	 * Spring application context for testing.
	 * @param testFunction the consumer that executes test operations on the store
	 */
	protected abstract void executeTest(Consumer<VectorStore> testFunction);

	/**
	 * The embedding dimension the store under test expects, so the suite can build
	 * correctly sized caller vectors. This is the single hook a concrete store test must
	 * supply.
	 * @return the embedding dimension
	 */
	protected abstract int embeddingDimensions();

	@Test
	protected void replaceById() {
		executeTest(vectorStore -> {
			String id = UUID.randomUUID().toString();

			vectorStore.upsert(List.of(embeddedDocument(id, "first version", Map.of("tag", "v1"))));
			vectorStore.upsert(List.of(embeddedDocument(id, "second version", Map.of("tag", "v2"))));

			await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
				List<Document> results = readAll(vectorStore, 10);
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(id);
				assertThat(results.get(0).getText()).isEqualTo("second version");
				assertThat(normalizeValue(results.get(0).getMetadata().get("tag"))).isEqualTo("v2");
			});
		});
	}

	@Test
	protected void pairingAcrossBatches() {
		executeTest(vectorStore -> {
			List<EmbeddedDocument> entries = new ArrayList<>();
			Map<String, String> expectedTextById = new HashMap<>();
			Map<String, String> expectedTagById = new HashMap<>();
			for (int i = 0; i < MULTI_BATCH_ENTRY_COUNT; i++) {
				String id = UUID.randomUUID().toString();
				String text = "content-" + i;
				String tag = "tag-" + i;
				entries.add(embeddedDocument(id, text, Map.of("tag", tag)));
				expectedTextById.put(id, text);
				expectedTagById.put(id, tag);
			}

			vectorStore.upsert(entries);

			await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
				List<Document> results = readAll(vectorStore, MULTI_BATCH_ENTRY_COUNT);
				assertThat(results).hasSize(MULTI_BATCH_ENTRY_COUNT);

				Map<String, Document> resultsById = results.stream().collect(Collectors.toMap(Document::getId, d -> d));
				assertThat(resultsById.keySet()).containsExactlyInAnyOrderElementsOf(expectedTextById.keySet());

				resultsById.forEach((id, result) -> {
					assertThat(result.getText()).isEqualTo(expectedTextById.get(id));
					assertThat(normalizeValue(result.getMetadata().get("tag"))).isEqualTo(expectedTagById.get(id));
				});
			});
		});
	}

	@Test
	protected void retryIdempotent() {
		executeTest(vectorStore -> {
			List<EmbeddedDocument> batch = List.of(
					embeddedDocument(UUID.randomUUID().toString(), "alpha", Map.of("tag", "a")),
					embeddedDocument(UUID.randomUUID().toString(), "beta", Map.of("tag", "b")));

			vectorStore.upsert(batch);
			vectorStore.upsert(batch);

			await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
				List<Document> results = readAll(vectorStore, 10);
				assertThat(results).hasSize(2);
				assertThat(results.stream().map(Document::getText)).containsExactlyInAnyOrder("alpha", "beta");
			});
		});
	}

	@Test
	protected void wrongDimensionRejectedBeforeWrite() {
		executeTest(vectorStore -> {
			String baselineId = UUID.randomUUID().toString();
			vectorStore.upsert(List.of(embeddedDocument(baselineId, "baseline", Map.of("tag", "base"))));

			await().atMost(5, TimeUnit.SECONDS)
				.pollInterval(Duration.ofMillis(500))
				.untilAsserted(() -> assertThat(readAll(vectorStore, 10)).hasSize(1));

			// Valid entry first, wrong-dimension entry second. A store that wrote while
			// iterating would have persisted the valid one before hitting the bad one.
			String wouldBeWrittenId = UUID.randomUUID().toString();
			List<EmbeddedDocument> badBatch = List.of(
					embeddedDocument(wouldBeWrittenId, "should not be written", Map.of("tag", "nope")),
					new EmbeddedDocument(new Document(UUID.randomUUID().toString(), "wrong dimension", new HashMap<>()),
							vectorOfLength(embeddingDimensions() + 1)));

			assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> vectorStore.upsert(badBatch));

			// Nothing from the rejected batch was written: only the baseline row remains.
			List<Document> results = readAll(vectorStore, 10);
			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(baselineId);
		});
	}

	/**
	 * Build an {@link EmbeddedDocument} with a deterministic, correctly sized vector
	 * derived from the id so distinct ids get distinct vectors.
	 * @param id the document id
	 * @param content the document text
	 * @param metadata the document metadata
	 * @return the embedded document
	 */
	protected EmbeddedDocument embeddedDocument(String id, String content, Map<String, Object> metadata) {
		return new EmbeddedDocument(new Document(id, content, metadata), vectorFor(id));
	}

	private float[] vectorFor(String id) {
		int dimensions = embeddingDimensions();
		float[] vector = new float[dimensions];
		int hash = id.hashCode();
		for (int i = 0; i < dimensions; i++) {
			vector[i] = ((hash >> (i % Integer.SIZE)) & 1) == 0 ? 0.1f * (i + 1) : 0.2f * (i + 1);
		}
		return vector;
	}

	private float[] vectorOfLength(int length) {
		float[] vector = new float[length];
		for (int i = 0; i < length; i++) {
			vector[i] = 0.1f;
		}
		return vector;
	}

	private List<Document> readAll(VectorStore vectorStore, int topK) {
		return vectorStore
			.similaritySearch(SearchRequest.builder().query("read back").topK(topK).similarityThresholdAll().build());
	}

	private @Nullable String normalizeValue(@Nullable Object value) {
		if (value == null) {
			return null;
		}
		return value.toString().replaceAll("^\"|\"$", "").trim();
	}

}
