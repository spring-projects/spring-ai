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

package org.springframework.ai.vectorstore.mongodb.atlas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.mongodb.atlas.MongoDBAtlasVectorStore.SimilarityFunction;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MongoDBAtlasVectorStore.Builder}.
 */
@ExtendWith(MockitoExtension.class)
public class MongoDBAtlasVectorStoreBuilderTest {

	@Mock
	private MongoTemplate mongoTemplate;

	@Mock
	private EmbeddingModel embeddingModel;

	@Test
	public void defaultSimilarityFunctionIsCosine() {
		MongoDBAtlasVectorStore vectorStore = MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel)
			.build();

		assertThat(vectorStore.getSimilarityFunction()).isEqualTo(SimilarityFunction.COSINE);
		assertThat(vectorStore.getSimilarityFunction().getValue()).isEqualTo("cosine");
	}

	@Test
	public void customSimilarityFunctionDotProduct() {
		MongoDBAtlasVectorStore vectorStore = MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel)
			.similarityFunction(SimilarityFunction.DOT_PRODUCT)
			.build();

		assertThat(vectorStore.getSimilarityFunction()).isEqualTo(SimilarityFunction.DOT_PRODUCT);
		assertThat(vectorStore.getSimilarityFunction().getValue()).isEqualTo("dotProduct");
	}

	@Test
	public void customSimilarityFunctionEuclidean() {
		MongoDBAtlasVectorStore vectorStore = MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel)
			.similarityFunction(SimilarityFunction.EUCLIDEAN)
			.build();

		assertThat(vectorStore.getSimilarityFunction()).isEqualTo(SimilarityFunction.EUCLIDEAN);
		assertThat(vectorStore.getSimilarityFunction().getValue()).isEqualTo("euclidean");
	}

	@Test
	public void nullSimilarityFunctionThrowsException() {
		assertThatThrownBy(
				() -> MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel).similarityFunction(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("similarityFunction must not be null");
	}

}
