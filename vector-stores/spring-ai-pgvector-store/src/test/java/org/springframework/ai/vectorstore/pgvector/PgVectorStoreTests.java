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

package org.springframework.ai.vectorstore.pgvector;

import java.util.Collections;

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
 * @author Muthukumaran Navaneethakrishnan
 * @author Soby Chacko
 */
public class PgVectorStoreTests {

	@ParameterizedTest(name = "{0} - Verifies valid Table name")
	@CsvSource({
			// Standard valid cases
			"customvectorstore, true", "user_data, true", "test123, true", "valid_table_name, true",

			// Edge cases
			"'', false", // Empty string
			"   , false", // Spaces only
			"custom vector store, false", // Spaces in name
			"customvectorstore;, false", // Semicolon appended
			"customvectorstore--, false", // SQL comment appended
			"drop table users;, false", // SQL command as a name
			"customvectorstore;drop table users;, false", // Valid name followed by
			// command
			"customvectorstore#, false", // Hash character included
			"customvectorstore$, false", // Dollar sign included
			"1, false", // Numeric only
			"customvectorstore or 1=1, false", // SQL Injection attempt
			"customvectorstore;--, false", // Ending with comment
			"custom_vector_store; DROP TABLE users;, false", // Injection with valid part
			"'customvectorstore\u0000', false", // Null byte included
			"'customvectorstore\n', false", // Newline character
			"12345678901234567890123456789012345678901234567890123456789012345, false" // More
	// than
	// 64
	// characters
	})
	void isValidTable(String tableName, Boolean expected) {
		assertThat(PgVectorSchemaValidator.isValidNameForDatabaseObject(tableName)).isEqualTo(expected);
	}

	@Test
	void shouldAddDocumentsInBatchesAndEmbedOnce() {
		// Given
		var jdbcTemplate = mock(JdbcTemplate.class);
		var embeddingModel = mock(EmbeddingModel.class);
		var pgVectorStore = PgVectorStore.builder(jdbcTemplate, embeddingModel).maxDocumentBatchSize(1000).build();

		// Testing with 9989 documents
		var documents = Collections.nCopies(9989, new Document("foo"));

		// When
		pgVectorStore.doAdd(documents);

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
