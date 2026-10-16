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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.typesense.api.Client;
import org.typesense.api.Documents;
import org.typesense.model.ImportDocumentsParameters;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TypesenseVectorStoreAddTests {

	private final Client client = mock(Client.class);

	private final org.typesense.api.Collection collection = mock(org.typesense.api.Collection.class);

	private final Documents typesenseDocuments = mock(Documents.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	private final TypesenseVectorStore vectorStore = TypesenseVectorStore.builder(this.client, this.embeddingModel)
		.build();

	@BeforeEach
	void setUp() throws Exception {
		when(this.client.collections("vector_store")).thenReturn(this.collection);
		when(this.collection.documents()).thenReturn(this.typesenseDocuments);
		when(this.embeddingModel.embed(anyList(), any(EmbeddingOptions.class), any(BatchingStrategy.class)))
			.thenReturn(List.of(new float[] { 0.1f }, new float[] { 0.2f }, new float[] { 0.3f }));
	}

	@Test
	void mixedImportResultsShouldReportFailedDocuments() throws Exception {
		when(this.typesenseDocuments.import_(anyList(), any(ImportDocumentsParameters.class)))
			.thenReturn("{\"success\":true}\n{\"success\":false,\"error\":\"Invalid vector dimension\"}\n"
					+ "{\"success\":true}");

		assertThatThrownBy(() -> this.vectorStore.add(documents())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("1 of 3")
			.hasMessageContaining("Invalid vector dimension");
	}

	@Test
	void allSuccessfulImportResultsShouldCompleteNormally() throws Exception {
		when(this.typesenseDocuments.import_(anyList(), any(ImportDocumentsParameters.class)))
			.thenReturn("{\"success\":true}\n{\"success\":true}\n{\"success\":true}");

		assertThatCode(() -> this.vectorStore.add(documents())).doesNotThrowAnyException();
	}

	@Test
	void allFailedImportResultsShouldReportEveryFailure() throws Exception {
		when(this.typesenseDocuments.import_(anyList(), any(ImportDocumentsParameters.class)))
			.thenReturn("{\"success\":false,\"error\":\"First document failed\"}\n"
					+ "{\"success\":false,\"error\":\"Second document failed\"}");

		assertThatThrownBy(() -> this.vectorStore.add(twoDocuments())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("2 of 2")
			.hasMessageContaining("First document failed")
			.hasMessageContaining("Second document failed");
	}

	@Test
	void emptyImportResponseShouldNotReportSuccessForNonEmptyBatch() throws Exception {
		when(this.typesenseDocuments.import_(anyList(), any(ImportDocumentsParameters.class))).thenReturn("");

		assertThatThrownBy(() -> this.vectorStore.add(documents())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Received 0 import results for 3 documents");
	}

	@Test
	void incompleteImportResponseShouldReportResultCountMismatch() throws Exception {
		when(this.typesenseDocuments.import_(anyList(), any(ImportDocumentsParameters.class)))
			.thenReturn("{\"success\":true}\n{\"success\":true}");

		assertThatThrownBy(() -> this.vectorStore.add(documents())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Received 2 import results for 3 documents");
	}

	@Test
	void malformedImportResponseShouldReportParsingFailure() throws Exception {
		when(this.typesenseDocuments.import_(anyList(), any(ImportDocumentsParameters.class))).thenReturn("{invalid}");

		assertThatThrownBy(() -> this.vectorStore.add(documents())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to parse Typesense import response");
	}

	@Test
	void importRequestExceptionShouldReachCaller() throws Exception {
		when(this.typesenseDocuments.import_(anyList(), any(ImportDocumentsParameters.class)))
			.thenThrow(new RuntimeException("connection refused"));

		assertThatThrownBy(() -> this.vectorStore.add(documents())).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Failed to add documents")
			.hasCauseInstanceOf(RuntimeException.class)
			.hasRootCauseMessage("connection refused");
	}

	@Test
	void emptyBatchShouldCompleteWithoutImportRequest() {
		assertThatCode(() -> this.vectorStore.add(List.of())).doesNotThrowAnyException();
		verifyNoInteractions(this.client, this.collection, this.typesenseDocuments);
	}

	private static List<Document> documents() {
		return List.of(new Document("first"), new Document("second"), new Document("third"));
	}

	private static List<Document> twoDocuments() {
		return List.of(new Document("first"), new Document("second"));
	}

}
