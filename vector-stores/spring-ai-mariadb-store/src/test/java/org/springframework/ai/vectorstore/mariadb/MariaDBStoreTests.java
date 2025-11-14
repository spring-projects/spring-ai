/*
 * Copyright 2023-2024 the original author or authors.
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

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

}
