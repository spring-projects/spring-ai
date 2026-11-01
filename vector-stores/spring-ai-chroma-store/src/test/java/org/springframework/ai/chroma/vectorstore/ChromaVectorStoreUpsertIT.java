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

package org.springframework.ai.chroma.vectorstore;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.ai.chroma.ChromaImage;
import org.springframework.ai.chroma.vectorstore.common.ChromaApiConstants;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.AbstractVectorStoreUpsertTests;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Upsert verification for {@link ChromaVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * Unlike {@link ChromaVectorStoreIT}, this needs no {@code OPENAI_API_KEY}:
 * {@code doUpsert} never embeds, and read-back only needs a query vector, so a
 * {@link FixedDimensionEmbeddingModel} is wired instead. Only Testcontainers Chroma is
 * required. Chroma has no configured vector size: it fixes the size of a collection from
 * the first embedding written, so the configured-dimension test of the suite is skipped.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
public class ChromaVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	private static final String COLLECTION_NAME = "UpsertCollection";

	@Container
	static ChromaDBContainer chromaContainer = new ChromaDBContainer(ChromaImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeEach
	void cleanDatabase() {
		ChromaApi chromaApi = chromaApi();
		if (chromaApi.getCollection(ChromaApiConstants.DEFAULT_TENANT_NAME, ChromaApiConstants.DEFAULT_DATABASE_NAME,
				COLLECTION_NAME) != null) {
			chromaApi.deleteCollection(ChromaApiConstants.DEFAULT_TENANT_NAME, ChromaApiConstants.DEFAULT_DATABASE_NAME,
					COLLECTION_NAME);
		}
	}

	@Override
	protected void executeTest(Consumer<VectorStore> testFunction) {
		this.contextRunner.run(context -> {
			VectorStore vectorStore = context.getBean(VectorStore.class);
			testFunction.accept(vectorStore);
		});
	}

	@Override
	protected int embeddingDimensions() {
		return EMBEDDING_DIMENSIONS;
	}

	@Override
	protected VectorStore createStoreOverExistingSchema(VectorStore schemaOwner, EmbeddingModel embeddingModel) {
		// The store has to read the size from the collection.
		return existingCollectionStore(embeddingModel, false);
	}

	@Override
	@Test
	protected void queryEmbeddingOfWrongDimensionRejected() {
		// A new collection has no size yet, and Chroma would adopt the size of the first
		// query, so write once to fix the size before checking that a wrong one fails.
		executeTest(vectorStore -> {
			vectorStore.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "seed", Map.of("tag", "s"))));

			assertThatThrownBy(() -> vectorStore.similaritySearch(new float[EMBEDDING_DIMENSIONS + 1],
					SearchRequest.builder().topK(3).build()))
				.isInstanceOf(IllegalArgumentException.class);
		});
	}

	@Test
	void emptyCollectionLeavesTheSizeUnknownUntilTheFirstWrite() {
		executeTest(vectorStore -> {
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			ChromaVectorStore store = existingCollectionStore(embeddingModel, false);

			// Nothing has been written, so the collection has no size yet and the store
			// does not check. The model must not have stood in for the collection.
			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "first", Map.of("tag", "f"))));

			// The first write fixed the size, which the store now reads and applies
			EmbeddedDocument tooShort = new EmbeddedDocument(
					new Document(UUID.randomUUID().toString(), "short", Map.of()), new float[] { 1f, 0f, 0f });
			assertThatThrownBy(() -> store.upsert(List.of(tooShort))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("dimension " + (EMBEDDING_DIMENSIONS - 1))
				.hasMessageContaining("dimension " + EMBEDDING_DIMENSIONS);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void initializingOverAnExistingCollectionWithoutEmbeddingModel() {
		executeTest(vectorStore -> {
			vectorStore.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "seed", Map.of("tag", "s"))));
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			ChromaVectorStore store = existingCollectionStore(embeddingModel, true);

			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "existing", Map.of("tag", "e"))));
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	@Test
	void failedCollectionReadLeavesTheSizeUnknownAndIsReadAgainLater() {
		executeTest(vectorStore -> {
			vectorStore.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "seed", Map.of("tag", "s"))));
			AtomicBoolean failCollectionReads = new AtomicBoolean();
			RestClient.Builder restClientBuilder = RestClient.builder()
				.requestFactory(new SimpleClientHttpRequestFactory())
				.requestInterceptor((request, body, execution) -> {
					if (failCollectionReads.get() && request.getMethod() == HttpMethod.GET
							&& request.getURI().getPath().endsWith("/collections/" + COLLECTION_NAME)) {
						throw new IOException("Simulated collection read failure");
					}
					return execution.execute(request, body);
				});
			ChromaApi chromaApi = ChromaApi.builder()
				.baseUrl(chromaContainer.getEndpoint())
				.restClientBuilder(restClientBuilder)
				.build();
			CallCountingEmbeddingModel embeddingModel = new CallCountingEmbeddingModel(EMBEDDING_DIMENSIONS + 3);
			ChromaVectorStore store = ChromaVectorStore.builder(chromaApi, embeddingModel)
				.collectionName(COLLECTION_NAME)
				.build();
			try {
				store.afterPropertiesSet();
			}
			catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
			EmbeddedDocument wrongSize = new EmbeddedDocument(
					new Document(UUID.randomUUID().toString(), "wrong size", Map.of()), new float[] { 1f, 0f, 0f });

			// The size cannot be read, so the store still writes without checking it and
			// Chroma rejects the wrong-sized vector instead
			failCollectionReads.set(true);
			store.upsert(List.of(embeddedDocument(UUID.randomUUID().toString(), "during outage", Map.of("tag", "o"))));
			assertThatThrownBy(() -> store.upsert(List.of(wrongSize)))
				.hasMessageContaining("expecting embedding with dimension of " + EMBEDDING_DIMENSIONS);

			// Once the collection can be read again its size is read and applies
			failCollectionReads.set(false);
			assertWrongDimensionRejected(store);
			assertThat(embeddingModel.calls()).isZero();
		});
	}

	private static ChromaVectorStore existingCollectionStore(EmbeddingModel embeddingModel, boolean initializeSchema) {
		ChromaVectorStore store = ChromaVectorStore.builder(chromaApi(), embeddingModel)
			.collectionName(COLLECTION_NAME)
			.initializeSchema(initializeSchema)
			.build();
		try {
			store.afterPropertiesSet();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
		return store;
	}

	private static ChromaApi chromaApi() {
		return ChromaApi.builder()
			.baseUrl(chromaContainer.getEndpoint())
			.restClientBuilder(RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory()))
			.build();
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public ChromaApi chromaApi() {
			return ChromaVectorStoreUpsertIT.chromaApi();
		}

		@Bean
		public VectorStore chromaVectorStore(EmbeddingModel embeddingModel, ChromaApi chromaApi) {
			return ChromaVectorStore.builder(chromaApi, embeddingModel)
				.collectionName(COLLECTION_NAME)
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new FixedDimensionEmbeddingModel(EMBEDDING_DIMENSIONS);
		}

	}

}
