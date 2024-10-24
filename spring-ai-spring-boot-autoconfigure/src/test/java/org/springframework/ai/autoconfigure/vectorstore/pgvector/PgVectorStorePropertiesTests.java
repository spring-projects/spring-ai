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

package org.springframework.ai.autoconfigure.vectorstore.pgvector;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.PgVectorStore.PgDistanceType;
import org.springframework.ai.vectorstore.PgVectorStore.PgIndexType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class PgVectorStorePropertiesTests {

	@Test
	public void defaultValues() {
		var props = new PgVectorStoreProperties();
		assertThat(props.getDimensions()).isEqualTo(PgVectorStore.INVALID_EMBEDDING_DIMENSION);
		assertThat(props.getDistanceType()).isEqualTo(PgDistanceType.COSINE_DISTANCE);
		assertThat(props.getIndexType()).isEqualTo(PgIndexType.HNSW);
		assertThat(props.isRemoveExistingVectorStoreTable()).isFalse();

		assertThat(props.isSchemaValidation()).isFalse();
		assertThat(props.getSchemaName()).isEqualTo(PgVectorStore.DEFAULT_SCHEMA_NAME);
		assertThat(props.getTableName()).isEqualTo(PgVectorStore.DEFAULT_TABLE_NAME);

	}

	@Test
	public void customValues() {
		var props = new PgVectorStoreProperties();

		props.setDimensions(1536);
		props.setDistanceType(PgDistanceType.EUCLIDEAN_DISTANCE);
		props.setIndexType(PgIndexType.IVFFLAT);
		props.setRemoveExistingVectorStoreTable(true);

		props.setSchemaValidation(true);
		props.setSchemaName("my_vector_schema");
		props.setTableName("my_vector_table");

		assertThat(props.getDimensions()).isEqualTo(1536);
		assertThat(props.getDistanceType()).isEqualTo(PgDistanceType.EUCLIDEAN_DISTANCE);
		assertThat(props.getIndexType()).isEqualTo(PgIndexType.IVFFLAT);
		assertThat(props.isRemoveExistingVectorStoreTable()).isTrue();

		assertThat(props.isSchemaValidation()).isTrue();
		assertThat(props.getSchemaName()).isEqualTo("my_vector_schema");
		assertThat(props.getTableName()).isEqualTo("my_vector_table");
	}

}
