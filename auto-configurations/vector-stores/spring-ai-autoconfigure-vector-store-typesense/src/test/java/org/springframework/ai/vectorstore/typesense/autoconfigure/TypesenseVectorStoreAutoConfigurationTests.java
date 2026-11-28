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

package org.springframework.ai.vectorstore.typesense.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.typesense.api.Client;
import org.typesense.api.Collection;
import org.typesense.model.CollectionResponse;
import org.typesense.model.Field;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.typesense.TypesenseVectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link TypesenseVectorStoreAutoConfiguration} sets the vector size
 * of the store.
 *
 * @author Dimitar Proynov
 */
class TypesenseVectorStoreAutoConfigurationTests {

	private final Client client = mock(Client.class);

	private final Collection collection = mock(Collection.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(TypesenseVectorStoreAutoConfiguration.class))
		.withBean(Client.class, () -> this.client)
		.withBean(EmbeddingModel.class, () -> this.embeddingModel);

	@BeforeEach
	void existingCollectionOfSize768() throws Exception {
		CollectionResponse schema = new CollectionResponse();
		schema.setFields(List.of(new Field().name(TypesenseVectorStore.EMBEDDING_FIELD_NAME).numDim(768)));
		when(this.client.collections(TypesenseVectorStore.DEFAULT_COLLECTION_NAME)).thenReturn(this.collection);
		when(this.collection.retrieve()).thenReturn(schema);
	}

	@Test
	void sizeIsReadFromAnExistingCollectionWhenNotConfigured() {
		this.contextRunner.run(context -> {
			TypesenseVectorStore store = context.getBean(TypesenseVectorStore.class);

			// The schema size, not the 1536 the property used to default to
			assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(768);
			verifyNoInteractions(this.embeddingModel);
		});
	}

	@Test
	void aConfiguredSizeIsUsedWithoutReadingTheSchema() {
		this.contextRunner.withPropertyValues("spring.ai.vectorstore.typesense.embedding-dimension=384")
			.run(context -> {
				TypesenseVectorStore store = context.getBean(TypesenseVectorStore.class);

				assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(384);
				verify(this.collection, never()).retrieve();
				verifyNoInteractions(this.embeddingModel);
			});
	}

}
