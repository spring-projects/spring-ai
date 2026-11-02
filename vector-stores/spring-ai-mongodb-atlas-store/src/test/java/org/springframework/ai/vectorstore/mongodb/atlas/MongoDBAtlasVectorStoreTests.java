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

import java.util.List;
import java.util.Map;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCollection;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link MongoDBAtlasVectorStore} resolves the vector size from the
 * search index.
 *
 * @author Soby Chacko
 */
class MongoDBAtlasVectorStoreTests {

	private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final MongoConverter converter = mock(MongoConverter.class);

	private final MongoCollection<org.bson.Document> collection = mockCollection();

	private final AggregateIterable<org.bson.Document> listResult = mockAggregate();

	MongoDBAtlasVectorStoreTests() {
		when(this.mongoTemplate.getCollection(anyString())).thenReturn(this.collection);
		when(this.mongoTemplate.getConverter()).thenReturn(this.converter);
		when(this.collection.aggregate(anyList())).thenReturn(this.listResult);
	}

	@Test
	void sizeIsReadFromTheVectorFieldOfTheConfiguredPath() {
		when(this.listResult.first()).thenReturn(
				index(field("vector", "other", 8), field("filter", "embedding", 0), field("vector", "embedding", 4)));
		MongoDBAtlasVectorStore store = store();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verify(this.embeddingModel, never()).dimensions();
	}

	@Test
	void aKnownSizeIsCachedAndCheckedBeforeWriting() {
		when(this.listResult.first()).thenReturn(index(field("vector", "embedding", 4)));
		MongoDBAtlasVectorStore store = store();

		store.upsert(List.of(entry(4)));
		store.upsert(List.of(entry(4)));
		assertThatIllegalArgumentException().isThrownBy(() -> store.upsert(List.of(entry(4), entry(3))))
			.withMessageContaining("Embedding at index 1");

		verify(this.listResult, times(1)).first();
		verify(this.mongoTemplate, times(2)).save(any(org.bson.Document.class), eq("vector_store"));
		verify(this.embeddingModel, never()).dimensions();
	}

	@Test
	void upsertIsRefusedWhenTheIndexHasNoVectorFieldForThePath() {
		when(this.listResult.first()).thenReturn(index(field("vector", "other", 4)));
		MongoDBAtlasVectorStore store = store();

		assertThatIllegalStateException().isThrownBy(() -> store.upsert(List.of(entry(4))))
			.withMessageContaining("Could not determine the vector size");
		verify(this.mongoTemplate, never()).save(any(org.bson.Document.class), anyString());
	}

	@Test
	void theVectorIsWrittenAtTheConfiguredPathNestedWhenItIsDotted() {
		when(this.listResult.first()).thenReturn(index(field("vector", "vectors.embedding", 4)));
		MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel)
			.pathName("vectors.embedding")
			.build();
		float[] embedding = { 1f, 0f, 0f, 0f };

		store.upsert(List.of(new EmbeddedDocument(new Document("id", "text", Map.of()), embedding)));

		ArgumentCaptor<org.bson.Document> saved = ArgumentCaptor.forClass(org.bson.Document.class);
		verify(this.mongoTemplate).save(saved.capture(), eq("vector_store"));
		assertThat(saved.getValue()).doesNotContainKey("vectors.embedding");
		assertThat(saved.getValue().get("vectors", org.bson.Document.class)).containsEntry("embedding", null);
	}

	@Test
	void aDottedPathThatClashesWithAnotherFieldIsRejected() {
		when(this.listResult.first()).thenReturn(index(field("vector", "metadata.embedding", 4)));
		MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel)
			.pathName("metadata.embedding")
			.build();
		// The converter mock writes nothing, so give the stored document a scalar there
		doAnswer(invocation -> {
			invocation.<org.bson.Document>getArgument(1).put("metadata", "not a document");
			return null;
		}).when(this.converter).write(any(), any(Bson.class));

		assertThatIllegalStateException().isThrownBy(() -> store.upsert(List.of(entry(4))))
			.withMessageContaining("clashes");
		verify(this.mongoTemplate, never()).save(any(org.bson.Document.class), anyString());
	}

	@Test
	void upsertIsRefusedWhenThereIsNoIndex() {
		when(this.listResult.first()).thenReturn(null);

		assertThatIllegalStateException().isThrownBy(() -> store().upsert(List.of(entry(4))))
			.withMessageContaining("Could not determine the vector size");
		verify(this.mongoTemplate, never()).save(any(org.bson.Document.class), anyString());
	}

	@Test
	void aFailedReadIsNotCachedAndTheIndexIsReadAgain() {
		when(this.listResult.first()).thenThrow(new DataAccessResourceFailureException("connection lost"))
			.thenReturn(index(field("vector", "embedding", 4)));
		MongoDBAtlasVectorStore store = store();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isNull();
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verify(this.embeddingModel, never()).dimensions();
	}

	@Test
	void startingOverAnExistingIndexDoesNotAskTheModelOrCreateAnIndex() throws Exception {
		when(this.mongoTemplate.collectionExists("vector_store")).thenReturn(true);
		when(this.listResult.first()).thenReturn(index(field("vector", "embedding", 4)));
		MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel)
			.initializeSchema(true)
			.build();

		store.afterPropertiesSet();

		verify(this.embeddingModel, never()).dimensions();
		verify(this.mongoTemplate, never()).executeCommand(any(org.bson.Document.class));
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verify(this.listResult, times(1)).first();
	}

	@Test
	void startingWhenTheIndexCannotBeListedFailsWithoutCreatingItOrAskingTheModel() {
		when(this.mongoTemplate.collectionExists("vector_store")).thenReturn(true);
		DataAccessResourceFailureException failure = new DataAccessResourceFailureException("connection lost");
		when(this.listResult.first()).thenThrow(failure);
		MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel)
			.initializeSchema(true)
			.build();

		assertThatThrownBy(store::afterPropertiesSet).isSameAs(failure);

		verifyNoInteractions(this.embeddingModel);
		verify(this.mongoTemplate, never()).executeCommand(any(org.bson.Document.class));
	}

	@Test
	void startingWithoutAnIndexCreatesItWithTheModelSizeAndReadsTheSizeBack() throws Exception {
		when(this.mongoTemplate.collectionExists("vector_store")).thenReturn(true);
		when(this.embeddingModel.dimensions()).thenReturn(7);
		// Missing at first, then another process' index of a different size is found
		when(this.listResult.first()).thenReturn(null).thenReturn(index(field("vector", "embedding", 5)));
		MongoDBAtlasVectorStore store = MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel)
			.initializeSchema(true)
			.build();

		store.afterPropertiesSet();

		verify(this.mongoTemplate).executeCommand(any(org.bson.Document.class));
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(5);
	}

	private MongoDBAtlasVectorStore store() {
		return MongoDBAtlasVectorStore.builder(this.mongoTemplate, this.embeddingModel).build();
	}

	private static EmbeddedDocument entry(int dimensions) {
		float[] embedding = new float[dimensions];
		embedding[0] = 1f;
		return new EmbeddedDocument(new Document("id-" + dimensions, "text", Map.of()), embedding);
	}

	private static org.bson.Document field(String type, String path, int numDimensions) {
		return new org.bson.Document("type", type).append("path", path).append("numDimensions", numDimensions);
	}

	private static org.bson.Document index(org.bson.Document... fields) {
		return new org.bson.Document("name", "vector_index").append("latestDefinition",
				new org.bson.Document("fields", List.of(fields)));
	}

	@SuppressWarnings("unchecked")
	private static MongoCollection<org.bson.Document> mockCollection() {
		return mock(MongoCollection.class);
	}

	@SuppressWarnings("unchecked")
	private static AggregateIterable<org.bson.Document> mockAggregate() {
		return mock(AggregateIterable.class);
	}

}
