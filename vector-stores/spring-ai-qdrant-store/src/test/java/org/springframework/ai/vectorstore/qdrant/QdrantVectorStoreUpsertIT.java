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

import java.util.function.Consumer;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.test.vectorstore.AbstractVectorStoreUpsertTests;
import org.springframework.ai.test.vectorstore.FixedDimensionEmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/**
 * Upsert verification for {@link QdrantVectorStore}, running the shared
 * {@link AbstractVectorStoreUpsertTests} suite.
 * <p>
 * Unlike {@link QdrantVectorStoreIT}, this needs no {@code OPENAI_API_KEY}:
 * {@code doUpsert} never embeds, and read-back only needs a query vector, so a
 * {@link FixedDimensionEmbeddingModel} is wired instead. Only Testcontainers Qdrant is
 * required. Qdrant writes a batch as a single upsert call, so
 * {@code pairingAcrossBatches} exercises positional pairing across all entries rather
 * than across write-batch boundaries.
 *
 * @author Soby Chacko
 * @since 2.1.0
 */
@Testcontainers
public class QdrantVectorStoreUpsertIT extends AbstractVectorStoreUpsertTests {

	private static final int EMBEDDING_DIMENSIONS = 4;

	private static final String COLLECTION_NAME = "test_collection_upsert";

	private static final String CONFIGURED_COLLECTION_NAME = "test_collection_upsert_configured";

	@Container
	static QdrantContainer qdrantContainer = new QdrantContainer(QdrantImage.DEFAULT_IMAGE);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestApplication.class);

	@BeforeEach
	void cleanCollection() throws Exception {
		QdrantClient client = new QdrantClient(
				QdrantGrpcClient.newBuilder(qdrantContainer.getHost(), qdrantContainer.getGrpcPort(), false).build());
		try {
			if (client.listCollectionsAsync().get().contains(COLLECTION_NAME)) {
				client.deleteCollectionAsync(COLLECTION_NAME).get();
			}
		}
		finally {
			client.close();
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
		QdrantClient qdrantClient = schemaOwner.<QdrantClient>getNativeClient().orElseThrow();
		return QdrantVectorStore.builder(qdrantClient, embeddingModel).collectionName(COLLECTION_NAME).build();
	}

	@Override
	protected VectorStore createStoreWithConfiguredDimensions(VectorStore schemaOwner, EmbeddingModel embeddingModel,
			int dimensions) {
		QdrantClient qdrantClient = schemaOwner.<QdrantClient>getNativeClient().orElseThrow();
		try {
			if (qdrantClient.listCollectionsAsync().get().contains(CONFIGURED_COLLECTION_NAME)) {
				qdrantClient.deleteCollectionAsync(CONFIGURED_COLLECTION_NAME).get();
			}
			QdrantVectorStore store = QdrantVectorStore.builder(qdrantClient, embeddingModel)
				.collectionName(CONFIGURED_COLLECTION_NAME)
				.dimensions(dimensions)
				.initializeSchema(true)
				.build();
			store.afterPropertiesSet();
			return store;
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	@SpringBootConfiguration
	public static class TestApplication {

		@Bean
		public QdrantClient qdrantClient() {
			return new QdrantClient(
					QdrantGrpcClient.newBuilder(qdrantContainer.getHost(), qdrantContainer.getGrpcPort(), false)
						.build());
		}

		@Bean
		public VectorStore vectorStore(EmbeddingModel embeddingModel, QdrantClient qdrantClient) {
			return QdrantVectorStore.builder(qdrantClient, embeddingModel)
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
