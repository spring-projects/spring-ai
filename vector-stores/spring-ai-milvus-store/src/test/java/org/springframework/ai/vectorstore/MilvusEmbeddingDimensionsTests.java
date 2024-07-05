/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vectorstore;

import io.milvus.client.MilvusServiceClient;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.MilvusVectorStore.MilvusVectorStoreConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 * @author Jiwoo Kim
 */
@ExtendWith(MockitoExtension.class)
public class MilvusEmbeddingDimensionsTests {

	@Mock
	private EmbeddingModel embeddingModel;

	@Mock
	private MilvusServiceClient milvusClient;

	@Test
	public void explicitlySetDimensions() {

		final int explicitDimensions = 696;

		MilvusVectorStoreConfig config = MilvusVectorStoreConfig.builder()
			.withEmbeddingDimension(explicitDimensions)
			.build();

		var dim = new MilvusVectorStore(milvusClient, embeddingModel, config, true).embeddingDimensions();

		assertThat(dim).isEqualTo(explicitDimensions);
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	public void embeddingModelDimensions() {
		when(embeddingModel.dimensions()).thenReturn(969);

		MilvusVectorStoreConfig config = MilvusVectorStoreConfig.builder().build();

		var dim = new MilvusVectorStore(milvusClient, embeddingModel, config ,true)
				.embeddingDimensions();

		assertThat(dim).isEqualTo(969);

		verify(embeddingModel, only()).dimensions();
	}

	@Test
	public void fallBackToDefaultDimensions() {

		when(embeddingModel.dimensions()).thenThrow(new RuntimeException());

		var dim = new MilvusVectorStore(milvusClient, embeddingModel,
				MilvusVectorStoreConfig.builder().build() ,true)
						.embeddingDimensions();

		assertThat(dim).isEqualTo(MilvusVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE);
		verify(embeddingModel, only()).dimensions();
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 32769 })
	public void invalidDimensionsThrowException(final int explicitDimensions) {
		// when
		ThrowableAssert.ThrowingCallable actual = () -> MilvusVectorStoreConfig.builder()
			.withEmbeddingDimension(explicitDimensions)
			.build();

		// then
		assertThatThrownBy(actual).isInstanceOf(IllegalArgumentException.class);
	}

}
