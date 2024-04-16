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
		assertThat(props.getCassandraContactPointHosts()).isNull();
		assertThat(props.getCassandraContactPointPort()).isEqualTo(9042);
		assertThat(props.getCassandraLocalDatacenter()).isNull();
		assertThat(props.getKeyspace()).isEqualTo(CassandraVectorStoreConfig.DEFAULT_KEYSPACE_NAME);
		assertThat(props.getTable()).isEqualTo(CassandraVectorStoreConfig.DEFAULT_TABLE_NAME);
		assertThat(props.getContentFieldName()).isEqualTo(CassandraVectorStoreConfig.DEFAULT_CONTENT_COLUMN_NAME);
		assertThat(props.getEmbeddingFieldName()).isEqualTo(CassandraVectorStoreConfig.DEFAULT_EMBEDDING_COLUMN_NAME);
		assertThat(props.getIndexName()).isEqualTo(CassandraVectorStoreConfig.DEFAULT_INDEX_NAME);
		assertThat(props.getDisallowSchemaCreation()).isFalse();
	}

	@Test
	void customValues() {
		var props = new CassandraVectorStoreProperties();
		props.setCassandraContactPointHosts("127.0.0.1,127.0.0.2");
		props.setCassandraContactPointPort(9043);
		props.setCassandraLocalDatacenter("dc1");
		props.setKeyspace("my_keyspace");
		props.setTable("my_table");
		props.setContentFieldName("my_content");
		props.setEmbeddingFieldName("my_vector");
		props.setIndexName("my_sai");
		props.setDisallowSchemaCreation(true);

		assertThat(props.getCassandraContactPointHosts()).isEqualTo("127.0.0.1,127.0.0.2");
		assertThat(props.getCassandraContactPointPort()).isEqualTo(9043);
		assertThat(props.getCassandraLocalDatacenter()).isEqualTo("dc1");
		assertThat(props.getKeyspace()).isEqualTo("my_keyspace");
		assertThat(props.getTable()).isEqualTo("my_table");
		assertThat(props.getContentFieldName()).isEqualTo("my_content");
		assertThat(props.getEmbeddingFieldName()).isEqualTo("my_vector");
		assertThat(props.getIndexName()).isEqualTo("my_sai");
		assertThat(props.getDisallowSchemaCreation()).isTrue();
	}

}
