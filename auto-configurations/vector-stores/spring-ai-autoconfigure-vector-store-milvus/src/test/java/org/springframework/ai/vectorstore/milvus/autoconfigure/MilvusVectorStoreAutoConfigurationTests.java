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

package org.springframework.ai.vectorstore.milvus.autoconfigure;

import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link MilvusVectorStoreAutoConfiguration} sets the vector size of
 * the store.
 *
 * @author lejuho
 */
class MilvusVectorStoreAutoConfigurationTests {

	private final MilvusServiceClient milvusClient = mock(MilvusServiceClient.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(MilvusVectorStoreAutoConfiguration.class))
		.withBean(MilvusServiceClient.class, () -> this.milvusClient)
		.withBean(EmbeddingModel.class, () -> this.embeddingModel);

	@BeforeEach
	void embeddingModelOfSize768() {
		when(this.embeddingModel.dimensions()).thenReturn(768);
	}

	@Test
	void sizeIsTakenFromTheEmbeddingModelWhenNotConfigured() {
		this.contextRunner.run(context -> {
			MilvusVectorStore store = context.getBean(MilvusVectorStore.class);

			// The model size, not the 1536 the property used to default to
			assertThat(embeddingDimensions(store)).isEqualTo(768);
		});
	}

	@Test
	void aConfiguredSizeIsUsed() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.milvus.embedding-dimension=384").run(context -> {
			MilvusVectorStore store = context.getBean(MilvusVectorStore.class);

			assertThat(embeddingDimensions(store)).isEqualTo(384);
		});
	}

	private static Integer embeddingDimensions(MilvusVectorStore store) {
		Integer dimensions = ReflectionTestUtils.invokeMethod(store, "embeddingDimensions");
		assertThat(dimensions).isNotNull();
		return dimensions;
	}

}
