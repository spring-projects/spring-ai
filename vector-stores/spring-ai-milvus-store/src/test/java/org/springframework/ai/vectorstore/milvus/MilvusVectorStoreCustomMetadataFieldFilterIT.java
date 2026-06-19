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

package org.springframework.ai.vectorstore.milvus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Taewoong Kim
 */
@Testcontainers
class MilvusVectorStoreCustomMetadataFieldFilterIT {

	@Container
	private static final MilvusContainer milvusContainer = new MilvusContainer(MilvusImage.DEFAULT_IMAGE);

	@Test
	void shouldFilterWithCustomMetadataFieldName() throws Exception {
		MilvusServiceClient milvusClient = new MilvusServiceClient(ConnectParam.newBuilder()
			.withAuthorization("minioadmin", "minioadmin")
			.withUri(milvusContainer.getEndpoint())
			.build());

		String collectionName = "test_custom_metadata_filter_" + UUID.randomUUID().toString().replace("-", "");
		MilvusVectorStore vectorStore = MilvusVectorStore.builder(milvusClient, new TestEmbeddingModel())
			.collectionName(collectionName)
			.databaseName(MilvusVectorStore.DEFAULT_DATABASE_NAME)
			.indexType(IndexType.IVF_FLAT)
			.metricType(MetricType.COSINE)
			.embeddingDimension(3)
			.metadataFieldName("meta")
			.batchingStrategy(new TokenCountBatchingStrategy())
			.initializeSchema(true)
			.build();

		boolean collectionCreated = false;
		try {
			vectorStore.afterPropertiesSet();
			collectionCreated = true;

			Document matchingDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("age", 35, "country", "NL"));
			Document filteredDocument = new Document("The World is Big and Salvation Lurks Around the Corner",
					Map.of("age", 20, "country", "BG"));
			vectorStore.add(List.of(matchingDocument, filteredDocument));

			List<Document> results = vectorStore.similaritySearch(SearchRequest.builder()
				.query("The World")
				.topK(5)
				.similarityThresholdAll()
				.filterExpression("age > 30")
				.build());

			assertThat(results).hasSize(1);
			assertThat(results.get(0).getId()).isEqualTo(matchingDocument.getId());
		}
		finally {
			if (collectionCreated) {
				vectorStore.dropCollection();
			}
			milvusClient.close(1);
		}
	}

	private static final class TestEmbeddingModel implements EmbeddingModel {

		@Override
		public float[] embed(Document document) {
			return embed(document.getText());
		}

		@Override
		public float[] embed(String text) {
			return new float[] { 0.1f, 0.2f, 0.3f };
		}

		@Override
		public EmbeddingResponse call(EmbeddingRequest request) {
			List<Embedding> embeddings = new ArrayList<>();
			for (int i = 0; i < request.getInstructions().size(); i++) {
				embeddings.add(new Embedding(new float[] { 0.1f, 0.2f, 0.3f }, i));
			}
			return new EmbeddingResponse(embeddings);
		}

		@Override
		public int dimensions() {
			return 3;
		}

	}

}
