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

package org.springframework.ai.vectorstore.typesense;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.typesense.api.Client;
import org.typesense.api.Collection;
import org.typesense.api.Collections;
import org.typesense.api.Documents;
import org.typesense.api.MultiSearch;
import org.typesense.api.exceptions.ObjectAlreadyExists;
import org.typesense.api.exceptions.ObjectNotFound;
import org.typesense.api.exceptions.RequestForbidden;
import org.typesense.model.CollectionResponse;
import org.typesense.model.CollectionSchema;
import org.typesense.model.Field;
import org.typesense.model.ImportDocumentsParameters;
import org.typesense.model.MultiSearchResult;
import org.typesense.model.MultiSearchResultItem;
import org.typesense.model.MultiSearchSearchesParameter;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link TypesenseVectorStore} resolves the vector size from the
 * collection schema, writes with upsert and searches with a query embedding.
 *
 * @author Soby Chacko
 */
class TypesenseVectorStoreTests {

	private final Client client = mock(Client.class);

	private final Collection collection = mock(Collection.class);

	private final Collections collections = mock(Collections.class);

	private final Documents documents = mock(Documents.class);

	private final MultiSearch multiSearch = mock(MultiSearch.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	TypesenseVectorStoreTests() {
		when(this.client.collections("vector_store")).thenReturn(this.collection);
		when(this.client.collections()).thenReturn(this.collections);
		when(this.collection.documents()).thenReturn(this.documents);
		this.client.multiSearch = this.multiSearch;
	}

	@Test
	void sizeIsReadFromTheEmbeddingFieldOfTheSchema() throws Exception {
		when(this.collection.retrieve()).thenReturn(schema(field("content", null), field("embedding", 4)));

		assertThat(store().createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void aConfiguredSizeIsUsedWithoutReadingTheSchema() throws Exception {
		TypesenseVectorStore store = TypesenseVectorStore.builder(this.client, this.embeddingModel)
			.embeddingDimension(3)
			.build();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(3);
		verify(this.collection, never()).retrieve();
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void aFailedReadIsNotCachedAndTheSchemaIsReadAgain() throws Exception {
		when(this.collection.retrieve()).thenThrow(new IllegalStateException("connection lost"))
			.thenReturn(schema(field("embedding", 4)));
		TypesenseVectorStore store = store();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isNull();
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verify(this.collection, times(2)).retrieve();
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void startingOverAnExistingCollectionDoesNotAskTheModelOrCreateIt() throws Exception {
		when(this.collection.retrieve()).thenReturn(schema(field("embedding", 4)));
		TypesenseVectorStore store = initializingStore();

		store.afterPropertiesSet();

		verifyNoInteractions(this.embeddingModel);
		verify(this.collections, never()).create(any(CollectionSchema.class));
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verify(this.collection, times(1)).retrieve();
	}

	@Test
	void startingWithoutACollectionCreatesItWithTheModelSizeAndReadsTheSizeBack() throws Exception {
		when(this.embeddingModel.dimensions()).thenReturn(7);
		// Missing at first, then another process' collection of a different size is
		// found
		when(this.collection.retrieve()).thenThrow(new ObjectNotFound("Not Found", 404))
			.thenReturn(schema(field("embedding", 5)));
		when(this.collections.create(any(CollectionSchema.class)))
			.thenThrow(new ObjectAlreadyExists("Already exists", 409));
		TypesenseVectorStore store = initializingStore();

		store.afterPropertiesSet();

		verify(this.collections).create(any(CollectionSchema.class));
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(5);
	}

	@Test
	void startingWhenTheCollectionCannotBeCreatedFails() throws Exception {
		when(this.embeddingModel.dimensions()).thenReturn(4);
		when(this.collection.retrieve()).thenThrow(new ObjectNotFound("Not Found", 404));
		RequestForbidden failure = new RequestForbidden("Forbidden", 403);
		when(this.collections.create(any(CollectionSchema.class))).thenThrow(failure);
		TypesenseVectorStore store = initializingStore();

		assertThatIllegalStateException().isThrownBy(store::afterPropertiesSet).withCause(failure);
	}

	@Test
	void startingWhenTheCollectionCannotBeReadFailsWithoutCreatingItOrAskingTheModel() throws Exception {
		java.net.ConnectException failure = new java.net.ConnectException("connection refused");
		when(this.collection.retrieve()).thenThrow(failure);
		TypesenseVectorStore store = initializingStore();

		assertThatIllegalStateException().isThrownBy(store::afterPropertiesSet).withCause(failure);

		verifyNoInteractions(this.embeddingModel);
		verify(this.collections, never()).create(any(CollectionSchema.class));
	}

	@Test
	void aKnownSizeIsCheckedBeforeAnythingIsWritten() throws Exception {
		when(this.collection.retrieve()).thenReturn(schema(field("embedding", 4)));

		assertThatIllegalArgumentException().isThrownBy(() -> store().upsert(List.of(entry("a", 4), entry("b", 3))))
			.withMessageContaining("Embedding at index 1");
		verify(this.documents, never()).import_(any(java.util.Collection.class), any(ImportDocumentsParameters.class));
	}

	@Test
	void aDocumentRejectedByTypesenseFailsTheUpsert() throws Exception {
		// The size is unknown, so the check is left to Typesense
		when(this.collection.retrieve()).thenThrow(new IllegalStateException("connection lost"));
		when(this.documents.import_(any(java.util.Collection.class), any(ImportDocumentsParameters.class)))
			.thenReturn("{\"success\":true}\n{\"code\":400,\"error\":\"Field `embedding` must have 4 dimensions.\","
					+ "\"success\":false}");

		assertThatIllegalStateException().isThrownBy(() -> store().upsert(List.of(entry("a", 4), entry("b", 3))))
			.withMessageContaining("index 1")
			.withMessageContaining("must have 4 dimensions");
	}

	@Test
	void fewerImportResultsThanDocumentsFailTheUpsert() throws Exception {
		when(this.collection.retrieve()).thenReturn(schema(field("embedding", 4)));
		// The client leaves out a document it cannot serialize, so Typesense only
		// reports on the others
		when(this.documents.import_(any(java.util.Collection.class), any(ImportDocumentsParameters.class)))
			.thenReturn("{\"success\":true}");

		assertThatIllegalStateException().isThrownBy(() -> store().upsert(List.of(entry("a", 4), entry("b", 4))))
			.withMessageContaining("returned 1 import results for 2 documents");
	}

	@Test
	void anUpsertThatTypesenseAcceptsSucceeds() throws Exception {
		when(this.collection.retrieve()).thenReturn(schema(field("embedding", 4)));
		when(this.documents.import_(any(java.util.Collection.class), any(ImportDocumentsParameters.class)))
			.thenReturn("{\"success\":true}\n{\"success\":true}");

		store().upsert(List.of(entry("a", 4), entry("b", 4)));

		verify(this.documents).import_(any(java.util.Collection.class), any(ImportDocumentsParameters.class));
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void aSearchRejectedByTypesenseFailsTheQueryEmbeddingSearchOnly() throws Exception {
		when(this.collection.retrieve()).thenThrow(new IllegalStateException("connection lost"));
		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 1f, 0f, 0f });
		when(this.multiSearch.perform(any(MultiSearchSearchesParameter.class), anyMap()))
			.thenReturn(new MultiSearchResult().addResultsItem(
					new MultiSearchResultItem().code(400L).error("Query field `embedding` must have 4 dimensions.")));
		TypesenseVectorStore store = store();

		assertThatIllegalStateException()
			.isThrownBy(() -> store.similaritySearch(new float[] { 1f, 0f, 0f }, SearchRequest.builder().build()))
			.withMessageContaining("must have 4 dimensions");
		// Text search keeps returning an empty list when the search fails
		assertThat(store.similaritySearch(SearchRequest.builder().query("query").build())).isEmpty();
	}

	@Test
	void aSearchAsksForAsManyHitsAsTopK() throws Exception {
		when(this.embeddingModel.embed("query")).thenReturn(new float[] { 1f, 0f, 0f, 0f });
		MultiSearchResultItem noHits = new MultiSearchResultItem();
		noHits.hits(List.of());
		when(this.multiSearch.perform(any(MultiSearchSearchesParameter.class), anyMap()))
			.thenReturn(new MultiSearchResult().addResultsItem(noHits));

		store().similaritySearch(SearchRequest.builder().query("query").topK(25).build());

		ArgumentCaptor<MultiSearchSearchesParameter> searches = ArgumentCaptor
			.forClass(MultiSearchSearchesParameter.class);
		verify(this.multiSearch).perform(searches.capture(), anyMap());
		// Typesense returns 10 hits per page unless asked for more
		assertThat(searches.getValue().getSearches().get(0).getPerPage()).isEqualTo(25);
	}

	private TypesenseVectorStore store() {
		return TypesenseVectorStore.builder(this.client, this.embeddingModel).build();
	}

	private TypesenseVectorStore initializingStore() {
		return TypesenseVectorStore.builder(this.client, this.embeddingModel).initializeSchema(true).build();
	}

	private static EmbeddedDocument entry(String id, int dimensions) {
		float[] embedding = new float[dimensions];
		embedding[0] = 1f;
		return new EmbeddedDocument(new Document(id, "text", Map.of()), embedding);
	}

	private static Field field(String name, Integer numDim) {
		return new Field().name(name).numDim(numDim);
	}

	private static CollectionResponse schema(Field... fields) {
		CollectionResponse response = new CollectionResponse();
		response.setFields(List.of(fields));
		return response;
	}

}
