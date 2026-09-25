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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

/**
 * Shared verification suite for {@link VectorStore#upsert(List)} implementations, a
 * sibling to {@link BaseVectorStoreTests}. It also covers
 * {@link VectorStore#similaritySearch(float[], SearchRequest)}, since only vectors the
 * caller controls give a search result the test can predict. A concrete store test
 * extends this class, provides a configured store through {@link #executeTest(Consumer)},
 * and reports the embedding dimension its store expects through
 * {@link #embeddingDimensions()} so the suite can build correctly sized caller vectors.
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

	/**
	 * Build a second store over the schema that the store from
	 * {@link #executeTest(Consumer)} has already created, the way an application that
	 * only writes pre-computed vectors would: schema initialization off, no configured
	 * dimension, and the given embedding model. The suite passes a model that counts its
	 * calls, to prove {@code upsert} learns the vector size from the existing schema
	 * without contacting the model.
	 * <p>
	 * The default returns {@code null}, which skips
	 * {@link #upsertIntoExistingSchemaWithoutEmbeddingModel()}.
	 * @param schemaOwner the store that created the schema
	 * @param embeddingModel the embedding model the new store must be built with
	 * @return a store over the existing schema, or {@code null} if not supported
	 */
	protected @Nullable VectorStore createStoreOverExistingSchema(VectorStore schemaOwner,
			EmbeddingModel embeddingModel) {
		return null;
	}

	/**
	 * Build and initialize a store over a new schema of its own, separate from the one
	 * used by {@link #executeTest(Consumer)}, with schema initialization on and the given
	 * dimension configured. The suite passes a model that counts its calls, to prove that
	 * a store with a configured dimension creates its schema and upserts without
	 * contacting the model.
	 * <p>
	 * The default returns {@code null}, which skips
	 * {@link #upsertWithConfiguredDimensionsWithoutEmbeddingModel()}.
	 * @param schemaOwner the store from {@link #executeTest(Consumer)}, for access to its
	 * client
	 * @param embeddingModel the embedding model the new store must be built with
	 * @param dimensions the dimension to configure on the new store
	 * @return an initialized store, or {@code null} if not supported
	 */
	protected @Nullable VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner,
			EmbeddingModel embeddingModel, int dimensions) {
		return null;
	}

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

	@Test
	protected void upsertIntoExistingSchemaWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(embeddingDimensions());
			VectorStore writer = createStoreOverExistingSchema(vectorStore, embeddingModel);
			Assumptions.assumeTrue(writer != null, "store does not support building over an existing schema");

			String id = UUID.randomUUID().toString();
			writer.upsert(List.of(embeddedDocument(id, "written without a model", Map.of("tag", "w"))));
			assertWrongDimensionRejected(writer);
			assertThat(embeddingModel.calls()).as("embedding model calls during upsert").isZero();

			await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
				List<Document> results = readAll(vectorStore, 10);
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(id);
			});
		});
	}

	@Test
	protected void upsertWithConfiguredDimensionsWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(embeddingDimensions());
			VectorStore store = createStoreWithConfiguredDimensions(vectorStore, embeddingModel, embeddingDimensions());
			Assumptions.assumeTrue(store != null, "store does not support a configured dimension");

			String id = UUID.randomUUID().toString();
			store.upsert(List.of(embeddedDocument(id, "configured dimension", Map.of("tag", "c"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).as("embedding model calls during schema creation and upsert").isZero();

			// Reading back embeds the query, so the model is used from here on.
			await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
				List<Document> results = readAll(store, 10);
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(id);
			});
		});
	}

	@Test
	protected void searchWithQueryEmbeddingRanksNearestFirst() {
		Assumptions.assumeTrue(embeddingDimensions() >= 3, "needs at least three dimensions for three distinct rows");
		executeTest(vectorStore -> {
			List<String> ids = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString(),
					UUID.randomUUID().toString());
			List<EmbeddedDocument> entries = new ArrayList<>();
			for (int i = 0; i < ids.size(); i++) {
				entries.add(new EmbeddedDocument(new Document(ids.get(i), "row " + i, Map.of("tag", "t" + i)),
						pointingAt(i)));
			}
			vectorStore.upsert(entries);

			// Each query vector points the same way as exactly one stored row.
			for (int i = 0; i < ids.size(); i++) {
				String expectedId = ids.get(i);
				float[] queryEmbedding = pointingAt(i);
				await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
					List<Document> results = vectorStore.similaritySearch(queryEmbedding,
							SearchRequest.builder().topK(3).similarityThresholdAll().build());
					assertThat(results).isNotEmpty();
					assertThat(results.get(0).getId()).isEqualTo(expectedId);
				});
			}
		});
	}

	@Test
	protected void searchWithQueryEmbeddingAppliesFilter() {
		Assumptions.assumeTrue(embeddingDimensions() >= 2, "needs at least two dimensions for two distinct rows");
		executeTest(vectorStore -> {
			String nearestId = UUID.randomUUID().toString();
			String filteredId = UUID.randomUUID().toString();
			vectorStore.upsert(List.of(
					new EmbeddedDocument(new Document(nearestId, "nearest", Map.of("tag", "a")), pointingAt(0)),
					new EmbeddedDocument(new Document(filteredId, "filtered", Map.of("tag", "b")), pointingAt(1))));

			// The query is nearest to the "a" row, but the filter only admits "b".
			SearchRequest request = SearchRequest.builder()
				.topK(3)
				.similarityThresholdAll()
				.filterExpression(new FilterExpressionBuilder().eq("tag", "b").build())
				.build();
			await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
				List<Document> results = vectorStore.similaritySearch(pointingAt(0), request);
				assertThat(results).extracting(Document::getId).containsExactly(filteredId);
			});
		});
	}

	@Test
	protected void queryEmbeddingOfWrongDimensionRejected() {
		executeTest(vectorStore -> assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> vectorStore.similaritySearch(vectorOfLength(embeddingDimensions() + 1),
					SearchRequest.builder().topK(3).build())));
	}

	@Test
	protected void searchWithQueryEmbeddingWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(embeddingDimensions());
			VectorStore store = createStoreOverExistingSchema(vectorStore, embeddingModel);
			Assumptions.assumeTrue(store != null, "store does not support building over an existing schema");

			String id = UUID.randomUUID().toString();
			store
				.upsert(List.of(new EmbeddedDocument(new Document(id, "no model", Map.of("tag", "n")), pointingAt(0))));

			await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
				List<Document> results = store.similaritySearch(pointingAt(0),
						SearchRequest.builder().topK(3).similarityThresholdAll().build());
				assertThat(results).extracting(Document::getId).containsExactly(id);
			});
			assertThat(embeddingModel.calls()).as("embedding model calls during upsert and search").isZero();
		});
	}

	/**
	 * Assert that the store's own pre-write check rejects a wrong-sized vector. Only that
	 * check throws {@link IllegalArgumentException}; a database rejecting the vector
	 * fails differently, and Redis does not fail at all. So a pass proves the store knew
	 * the right size up front.
	 * @param vectorStore the store to write to
	 */
	protected void assertWrongDimensionRejected(VectorStore vectorStore) {
		EmbeddedDocument wrongSize = new EmbeddedDocument(
				new Document(UUID.randomUUID().toString(), "wrong dimension", new HashMap<>()),
				vectorOfLength(embeddingDimensions() + 1));
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> vectorStore.upsert(List.of(wrongSize)));
	}

	@Test
	protected void contentRefRoundTrip() {
		executeTest(vectorStore -> {
			String id = UUID.randomUUID().toString();
			String pointer = "s3://media/video-42.mp4#t=4711";

			// A reference row: no content of its own, just a pointer to where the content
			// really lives. It has to go through upsert, because add embeds the text and
			// there is nothing there to embed.
			Document reference = Document.builder()
				.id(id)
				.text("")
				.metadata(DocumentMetadata.CONTENT_REF.value(), pointer)
				.build();

			vectorStore.upsert(List.of(new EmbeddedDocument(reference, vectorFor(id))));

			await().atMost(5, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
				List<Document> results = readAll(vectorStore, 10);
				assertThat(results).hasSize(1);
				assertThat(results.get(0).getId()).isEqualTo(id);
				assertThat(normalizeValue(results.get(0).getMetadata().get(DocumentMetadata.CONTENT_REF.value())))
					.isEqualTo(pointer);
			});
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

	/**
	 * A vector that points mostly along one axis, so vectors for different axes are
	 * clearly apart while staying similar enough to pass any similarity threshold.
	 * @param axis the axis to point along; must be less than
	 * {@link #embeddingDimensions()}
	 * @return the vector
	 */
	private float[] pointingAt(int axis) {
		float[] vector = new float[embeddingDimensions()];
		for (int i = 0; i < vector.length; i++) {
			vector[i] = (i == axis) ? 1.0f : 0.1f;
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

	/**
	 * An embedding model that counts every call made to it, for asserting that a code
	 * path never contacts the model. It returns a constant vector of the given size, so a
	 * store built with it can still be searched once the counted part is over.
	 */
	protected static final class CallCountingEmbeddingModel implements EmbeddingModel {

		private final AtomicInteger calls = new AtomicInteger();

		private final FixedDimensionEmbeddingModel delegate;

		public CallCountingEmbeddingModel(int dimensions) {
			this.delegate = new FixedDimensionEmbeddingModel(dimensions);
		}

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			this.calls.incrementAndGet();
			return this.delegate.call(request);
		}

		@Override
		public float[] embed(Document document) {
			this.calls.incrementAndGet();
			return this.delegate.embed(document);
		}

		@Override
		public float[] embed(String text) {
			this.calls.incrementAndGet();
			return this.delegate.embed(text);
		}

		@Override
		public int dimensions() {
			this.calls.incrementAndGet();
			return this.delegate.dimensions();
		}

		/**
		 * Returns the number of calls made to this model so far.
		 * @return the call count
		 */
		public int calls() {
			return this.calls.get();
		}

	}

}
