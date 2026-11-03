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

package org.springframework.ai.vectorstore.neo4j;

import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.exceptions.ServiceUnavailableException;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for how {@link Neo4jVectorStore} resolves the vector size from the vector
 * index, creates the index at startup and checks caller-supplied vectors.
 *
 * @author Soby Chacko
 */
class Neo4jVectorStoreTests {

	private final Driver driver = mock(Driver.class);

	private final Session session = mock(Session.class);

	private final Result result = mock(Result.class);

	private final TransactionContext tx = mock(TransactionContext.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	Neo4jVectorStoreTests() {
		when(this.driver.session(any(SessionConfig.class))).thenReturn(this.session);
		when(this.session.run(anyString())).thenReturn(this.result);
		when(this.tx.run(anyString())).thenReturn(this.result);
	}

	@Test
	void buildingTheStoreDoesNotAskTheModel() {
		store();

		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void sizeIsReadFromTheIndex() {
		indexReadsReturn(List.of(Values.value(4)));

		assertThat(store().createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void aConfiguredSizeIsUsedWithoutReadingTheIndex() {
		Neo4jVectorStore store = Neo4jVectorStore.builder(this.driver, this.embeddingModel)
			.embeddingDimension(3)
			.build();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(3);
		verify(this.session, never()).executeRead(any());
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void aMissingIndexLeavesTheSizeUnknownAndUpsertIsRefused() {
		indexReadsReturn(List.of());
		Neo4jVectorStore store = store();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isNull();
		assertThatIllegalStateException().isThrownBy(() -> store.upsert(List.of(entry(4))))
			.withMessageContaining(Neo4jVectorStore.DEFAULT_INDEX_NAME);
		verify(this.session, never()).executeWrite(any());
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void aFailedReadIsNotCachedAndTheIndexIsReadAgain() {
		when(this.session.executeRead(any())).thenThrow(new ServiceUnavailableException("connection lost"))
			.thenReturn(List.of(Values.value(4)));
		Neo4jVectorStore store = store();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isNull();
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verify(this.session, times(2)).executeRead(any());
		verifyNoInteractions(this.embeddingModel);
	}

	@Test
	void anIndexWithoutDimensionsSkipsTheSizeCheck() {
		indexReadsReturn(List.of(Values.NULL));
		Neo4jVectorStore store = store();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isNull();
		store.upsert(List.of(entry(3), entry(5)));
		verify(this.session).executeWrite(any());
		verify(this.session, times(1)).executeRead(any());
	}

	@Test
	void aWrongSizeIsRejectedBeforeAnythingIsWritten() {
		indexReadsReturn(List.of(Values.value(4)));
		Neo4jVectorStore store = store();

		assertThatIllegalArgumentException().isThrownBy(() -> store.upsert(List.of(entry(4), entry(3))))
			.withMessageContaining("index 1");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> store.similaritySearch(new float[3], SearchRequest.builder().build()))
			.withMessageContaining("Query embedding");
		verify(this.session, never()).executeWrite(any());
	}

	@Test
	void startingOverAnExistingIndexDoesNotAskTheModelOrCreateIt() {
		indexReadsReturn(List.of(Values.value(4)));
		Neo4jVectorStore store = initializingStore();

		store.afterPropertiesSet();

		verifyNoInteractions(this.embeddingModel);
		assertThat(schemaStatements()).singleElement().asString().startsWith("CREATE CONSTRAINT");
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verify(this.session, times(1)).executeRead(any());
	}

	@Test
	void startingWithoutAnIndexCreatesItWithTheModelSizeAndReadsTheSizeBack() {
		when(this.embeddingModel.dimensions()).thenReturn(7);
		// Missing at first, then another process' index of a different size is found
		when(this.session.executeRead(any())).thenReturn(List.of()).thenReturn(List.of(Values.value(5)));
		Neo4jVectorStore store = initializingStore();

		store.afterPropertiesSet();

		List<String> statements = schemaStatements();
		assertThat(statements).hasSize(2);
		assertThat(statements.get(1)).contains("CREATE VECTOR INDEX").contains("`vector.dimensions`: 7");
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(5);
		verify(this.embeddingModel, times(1)).dimensions();
		verify(this.session, times(2)).executeRead(any());
	}

	@Test
	void startupFailsWhenTheIndexIsStillMissingAfterCreatingIt() {
		when(this.embeddingModel.dimensions()).thenReturn(7);
		indexReadsReturn(List.of());
		Neo4jVectorStore store = initializingStore();

		assertThatIllegalStateException().isThrownBy(store::afterPropertiesSet)
			.withMessageContaining("does not exist after creating it");
		verify(this.session, times(2)).executeRead(any());
	}

	@Test
	void aFailedLookupAtStartupFailsInitialization() {
		when(this.session.executeRead(any())).thenThrow(new ServiceUnavailableException("connection lost"));
		Neo4jVectorStore store = initializingStore();

		assertThatIllegalStateException().isThrownBy(store::afterPropertiesSet)
			.withMessageContaining(Neo4jVectorStore.DEFAULT_INDEX_NAME)
			.withCauseInstanceOf(ServiceUnavailableException.class);
		assertThat(schemaStatements()).singleElement().asString().startsWith("CREATE CONSTRAINT");
		verifyNoInteractions(this.embeddingModel);
	}

	private void indexReadsReturn(List<Value> rows) {
		when(this.session.executeRead(any())).thenReturn(rows);
	}

	/**
	 * Runs every schema transaction the store issued against a mock transaction and
	 * returns the statements, in order.
	 */
	@SuppressWarnings("unchecked")
	private List<String> schemaStatements() {
		ArgumentCaptor<Consumer<TransactionContext>> work = ArgumentCaptor.forClass(Consumer.class);
		verify(this.session, atLeast(0)).executeWriteWithoutResult(work.capture());
		work.getAllValues().forEach(consumer -> consumer.accept(this.tx));
		ArgumentCaptor<String> statements = ArgumentCaptor.forClass(String.class);
		verify(this.tx, atLeast(0)).run(statements.capture());
		return statements.getAllValues();
	}

	private Neo4jVectorStore store() {
		return Neo4jVectorStore.builder(this.driver, this.embeddingModel).build();
	}

	private Neo4jVectorStore initializingStore() {
		return Neo4jVectorStore.builder(this.driver, this.embeddingModel).initializeSchema(true).build();
	}

	private static EmbeddedDocument entry(int dimensions) {
		return new EmbeddedDocument(new Document("content"), new float[dimensions]);
	}

}
