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

package org.springframework.ai.autoconfigure.vectorstore.mariadb;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore;
import org.springframework.ai.vectorstore.mariadb.MariaDBVectorStore.MariaDBDistanceType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Diego Dupin
 */
public class MariaDbStorePropertiesTests {

	@Test
	public void defaultValues() {
		var props = new MariaDbStoreProperties();
		assertThat(props.getDimensions()).isEqualTo(MariaDBVectorStore.INVALID_EMBEDDING_DIMENSION);
		assertThat(props.getDistanceType()).isEqualTo(MariaDBDistanceType.COSINE);
		assertThat(props.isRemoveExistingVectorStoreTable()).isFalse();

		assertThat(props.isSchemaValidation()).isFalse();
		assertThat(props.getSchemaName()).isNull();
		assertThat(props.getTableName()).isEqualTo(MariaDBVectorStore.DEFAULT_TABLE_NAME);

	}

	@Test
	public void customValues() {
		var props = new MariaDbStoreProperties();

		props.setDimensions(1536);
		props.setDistanceType(MariaDBDistanceType.EUCLIDEAN);
		props.setRemoveExistingVectorStoreTable(true);

		props.setSchemaValidation(true);
		props.setSchemaName("my_vector_schema");
		props.setTableName("my_vector_table");
		props.setIdFieldName("my_vector_id");
		props.setMetadataFieldName("my_vector_meta");
		props.setContentFieldName("my_vector_content");
		props.setEmbeddingFieldName("my_vector_embedding");
		props.setInitializeSchema(true);

		assertThat(props.getDimensions()).isEqualTo(1536);
		assertThat(props.getDistanceType()).isEqualTo(MariaDBDistanceType.EUCLIDEAN);
		assertThat(props.isRemoveExistingVectorStoreTable()).isTrue();

		assertThat(props.isSchemaValidation()).isTrue();
		assertThat(props.getSchemaName()).isEqualTo("my_vector_schema");
		assertThat(props.getTableName()).isEqualTo("my_vector_table");
		assertThat(props.getIdFieldName()).isEqualTo("my_vector_id");
		assertThat(props.getMetadataFieldName()).isEqualTo("my_vector_meta");
		assertThat(props.getContentFieldName()).isEqualTo("my_vector_content");
		assertThat(props.getEmbeddingFieldName()).isEqualTo("my_vector_embedding");
	}

}
