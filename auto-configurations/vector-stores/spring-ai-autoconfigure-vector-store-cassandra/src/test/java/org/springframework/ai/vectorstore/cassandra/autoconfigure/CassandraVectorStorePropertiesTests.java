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

package org.springframework.ai.vectorstore.cassandra.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.cassandra.CassandraVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Mick Semb Wever
 * @since 1.0.0
 */
class CassandraVectorStorePropertiesTests {

	@Test
	void defaultValues() {
		var props = new CassandraVectorStoreProperties();
		assertThat(props.getKeyspace()).isEqualTo(CassandraVectorStore.DEFAULT_KEYSPACE_NAME);
		assertThat(props.getTable()).isEqualTo(CassandraVectorStore.DEFAULT_TABLE_NAME);
		assertThat(props.getContentColumnName()).isEqualTo(CassandraVectorStore.DEFAULT_CONTENT_COLUMN_NAME);
		assertThat(props.getEmbeddingColumnName()).isEqualTo(CassandraVectorStore.DEFAULT_EMBEDDING_COLUMN_NAME);
		assertThat(props.getIndexName()).isNull();
		assertThat(props.getFixedThreadPoolExecutorSize()).isEqualTo(CassandraVectorStore.DEFAULT_ADD_CONCURRENCY);
	}

	@Test
	void customValues() {
		var props = new CassandraVectorStoreProperties();
		props.setKeyspace("my_keyspace");
		props.setTable("my_table");
		props.setContentColumnName("my_content");
		props.setEmbeddingColumnName("my_vector");
		props.setIndexName("my_sai");
		props.setFixedThreadPoolExecutorSize(10);

		assertThat(props.getKeyspace()).isEqualTo("my_keyspace");
		assertThat(props.getTable()).isEqualTo("my_table");
		assertThat(props.getContentColumnName()).isEqualTo("my_content");
		assertThat(props.getEmbeddingColumnName()).isEqualTo("my_vector");
		assertThat(props.getIndexName()).isEqualTo("my_sai");
		assertThat(props.getFixedThreadPoolExecutorSize()).isEqualTo(10);
	}

}
