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

package org.springframework.ai.vectorstore.mariadb;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.EmbeddedDocument;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Diego Dupin
 */
public class MariaDBStoreTests {

	@ParameterizedTest(name = "{0} - enquote identifier validation")
	@CsvSource({
			// Standard valid cases
			"customvectorstore, true, `customvectorstore`", "user_data, true, `user_data`", "test123, true, `test123`",
			"valid_table_name, true, `valid_table_name`", "customvectorstore, false, customvectorstore",
			"user_data, false, user_data", "test123, false, test123", "valid_table_name, false, valid_table_name",
			"1234567890123456789012345678901234567890123456789012345678901234, false, `1234567890123456789012345678901234567890123456789012345678901234`" })
	void enquoteIdentifier(String tableName, boolean alwaysQuote, String expected) {
		assertThat(MariaDBSchemaValidator.validateAndEnquoteIdentifier(tableName, alwaysQuote)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{0} - error identifier validation")
	@CsvSource({ "12345678901234567890123456789012345678901234567890123456789012345, false",
			"12345678901234567890123456789012345678901234567890123456789012345, true",
			"customvectorstore;drop table users;, false", "some\u0000notpossibleValue, true" })
	void enquoteIdentifierThrow(String tableName, boolean alwaysQuote) {
		Assert.assertThrows(IllegalArgumentException.class,
				() -> MariaDBSchemaValidator.validateAndEnquoteIdentifier(tableName, alwaysQuote));
	}

	@Test
	void shouldAddDocumentsInBatchesAndEmbedOnce() {
		// Given
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		var mariadbVectorStore = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel)
			.maxDocumentBatchSize(1000)
			.build();

		// Testing with 9989 documents
		var documents = Collections.nCopies(9989, new Document("foo"));

		// When
		mariadbVectorStore.doAdd(documents);

		// Then
		verify(embeddingModel, only()).embed(eq(documents), any(), any());

		var batchUpdateCaptor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
		verify(jdbcTemplate, times(10)).batchUpdate(anyString(), batchUpdateCaptor.capture());

		assertThat(batchUpdateCaptor.getAllValues()).hasSize(10)
			.allSatisfy(BatchPreparedStatementSetter::getBatchSize)
			.satisfies(batches -> {
				for (int i = 0; i < 9; i++) {
					assertThat(batches.get(i).getBatchSize()).as("Batch at index %d should have size 10", i)
						.isEqualTo(1000);
				}
				assertThat(batches.get(9).getBatchSize()).as("Last batch should have size 989").isEqualTo(989);
			});
	}

	@Test
	void failedTableReadLeavesTheSizeUnknownAndIsRetried() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(), any(), any()))
			.thenThrow(new DataAccessResourceFailureException("connection lost"))
			.thenReturn(List.of("vector(4)"));
		when(jdbcTemplate.query(anyString(), ArgumentMatchers.<RowMapper<Document>>any(), any(), any(), any()))
			.thenReturn(List.of());
		var store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).build();
		var request = SearchRequest.builder().query("hello").topK(5).similarityThresholdAll().build();

		// The lookup fails, so the size is unknown and is not checked against a guess
		// from the model
		assertThatNoException().isThrownBy(() -> store.doSimilaritySearch(new float[] { 0.1f, 0.2f, 0.3f }, request));

		// Once the table can be read again the real size applies
		assertThatIllegalArgumentException()
			.isThrownBy(() -> store.doSimilaritySearch(new float[] { 0.1f, 0.2f, 0.3f }, request))
			.withMessageContaining("dimension 3")
			.withMessageContaining("dimension 4");
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void readSizeIsCached() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(), any(), any()))
			.thenReturn(List.of("vector(4)"));
		var store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).build();

		store.doUpsert(List.of(new EmbeddedDocument(new Document("foo"), new float[] { 0.1f, 0.2f, 0.3f, 0.4f })));
		store.doUpsert(List.of(new EmbeddedDocument(new Document("bar"), new float[] { 0.1f, 0.2f, 0.3f, 0.4f })));

		verify(jdbcTemplate, times(2)).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
		verify(jdbcTemplate, times(1)).queryForList(anyString(), eq(String.class), any(), any(), any());
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void missingTableLeavesTheSizeUnknownInsteadOfAskingTheModel() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(), any(), any())).thenReturn(List.of());
		var store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).build();

		store.doUpsert(List.of(new EmbeddedDocument(new Document("foo"), new float[] { 0.1f, 0.2f, 0.3f })));

		verify(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void initializingOverAnExistingTableDoesNotAskTheModel() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);
		var store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).initializeSchema(true).build();

		store.afterPropertiesSet();

		verify(jdbcTemplate, never()).execute(contains("CREATE TABLE"));
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void initializingCreatesTheTableWithTheModelSize() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(3);
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);
		var store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).initializeSchema(true).build();

		store.afterPropertiesSet();

		verify(jdbcTemplate).execute(contains("VECTOR(3)"));
	}

	@Test
	void sizeOfACreatedTableIsReadBackInsteadOfAssumed() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.dimensions()).thenReturn(7);
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);
		// Another store created the table first with a different size, so this
		// store's CREATE TABLE IF NOT EXISTS did nothing
		when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(), any(), any()))
			.thenReturn(List.of("vector(4)"));
		var store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).initializeSchema(true).build();

		store.afterPropertiesSet();
		store.doUpsert(List.of(new EmbeddedDocument(new Document("foo"), new float[] { 0.1f, 0.2f, 0.3f, 0.4f })));

		verify(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> store.doUpsert(
					List.of(new EmbeddedDocument(new Document("bar"), new float[] { 0.1f, 0.2f, 0.3f, 0.4f, 0.5f }))))
			.withMessageContaining("dimension 4");
	}

	@Test
	void failedValidationDoesNotAskTheModel() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(String.class))).thenReturn("testdb");
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);
		// The metadata column is missing
		when(jdbcTemplate.queryForList(anyString(), eq("testdb"), eq("vector_store"))).thenReturn(List
			.of(Map.of("COLUMN_NAME", "id"), Map.of("COLUMN_NAME", "content"), Map.of("COLUMN_NAME", "embedding")));
		var store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).schemaValidation(true).build();

		assertThatIllegalStateException().isThrownBy(store::afterPropertiesSet);
		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void validatingAnExistingTableDoesNotAskTheModel() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(jdbcTemplate.queryForObject(anyString(), eq(String.class))).thenReturn("testdb");
		when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(1);
		when(jdbcTemplate.queryForList(anyString(), eq("testdb"), eq("vector_store")))
			.thenReturn(List.of(Map.of("COLUMN_NAME", "id"), Map.of("COLUMN_NAME", "content"),
					Map.of("COLUMN_NAME", "metadata"), Map.of("COLUMN_NAME", "embedding")));
		var store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).schemaValidation(true).build();

		store.afterPropertiesSet();

		verify(embeddingModel, never()).dimensions();
	}

	@Test
	void observationLeavesOutTheSizeWhenItIsUnknown() {
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		when(jdbcTemplate.queryForList(anyString(), eq(String.class), any(), any(), any()))
			.thenThrow(new DataAccessResourceFailureException("connection lost"))
			.thenReturn(List.of("vector(4)"));
		var store = MariaDBVectorStore.builder(jdbcTemplate, embeddingModel).build();

		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isNull();
		assertThat(store.createObservationContextBuilder("query").build().getDimensions()).isEqualTo(4);
		verify(embeddingModel, never()).dimensions();
	}

}
