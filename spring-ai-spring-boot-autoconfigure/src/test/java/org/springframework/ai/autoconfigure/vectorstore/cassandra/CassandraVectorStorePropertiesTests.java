/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.vectorstore.cassandra;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.CassandraVectorStoreConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mick Semb Wever
 * @since 1.0.0
 */
class CassandraVectorStorePropertiesTests {

	@Test
	void defaultValues() {
		var props = new CassandraVectorStoreProperties();
		assertThat(props.getKeyspace()).isEqualTo(CassandraVectorStoreConfig.DEFAULT_KEYSPACE_NAME);
		assertThat(props.getTable()).isEqualTo(CassandraVectorStoreConfig.DEFAULT_TABLE_NAME);
		assertThat(props.getContentColumnName()).isEqualTo(CassandraVectorStoreConfig.DEFAULT_CONTENT_COLUMN_NAME);
		assertThat(props.getEmbeddingColumnName()).isEqualTo(CassandraVectorStoreConfig.DEFAULT_EMBEDDING_COLUMN_NAME);
		assertThat(props.getIndexName()).isNull();
		assertThat(props.getDisallowSchemaCreation()).isFalse();
		assertThat(props.getFixedThreadPoolExecutorSize())
			.isEqualTo(CassandraVectorStoreConfig.DEFAULT_ADD_CONCURRENCY);
	}

	@Test
	void customValues() {
		var props = new CassandraVectorStoreProperties();
		props.setKeyspace("my_keyspace");
		props.setTable("my_table");
		props.setContentColumnName("my_content");
		props.setEmbeddingColumnName("my_vector");
		props.setIndexName("my_sai");
		props.setDisallowSchemaCreation(true);
		props.setFixedThreadPoolExecutorSize(10);

		assertThat(props.getKeyspace()).isEqualTo("my_keyspace");
		assertThat(props.getTable()).isEqualTo("my_table");
		assertThat(props.getContentColumnName()).isEqualTo("my_content");
		assertThat(props.getEmbeddingColumnName()).isEqualTo("my_vector");
		assertThat(props.getIndexName()).isEqualTo("my_sai");
		assertThat(props.getDisallowSchemaCreation()).isTrue();
		assertThat(props.getFixedThreadPoolExecutorSize()).isEqualTo(10);
	}

}
