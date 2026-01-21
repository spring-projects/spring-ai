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

package org.springframework.ai.vectorstore.gemfire.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.vectorstore.gemfire.GemFireVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geet Rawat
 * @author Soby Chacko
 */
class GemFireVectorStorePropertiesTests {

	@Test
	void defaultValues() {
		var props = new GemFireVectorStoreProperties();
		assertThat(props.getIndexName()).isEqualTo(GemFireVectorStore.DEFAULT_INDEX_NAME);
		assertThat(props.getHost()).isEqualTo(GemFireVectorStore.DEFAULT_HOST);
		assertThat(props.getPort()).isEqualTo(GemFireVectorStore.DEFAULT_PORT);
		assertThat(props.getBeamWidth()).isEqualTo(GemFireVectorStore.DEFAULT_BEAM_WIDTH);
		assertThat(props.getMaxConnections()).isEqualTo(GemFireVectorStore.DEFAULT_MAX_CONNECTIONS);
		assertThat(props.getFields()).isEqualTo(GemFireVectorStore.DEFAULT_FIELDS);
		assertThat(props.getBuckets()).isEqualTo(GemFireVectorStore.DEFAULT_BUCKETS);
		assertThat(props.getUsername()).isNull();
		assertThat(props.getPassword()).isNull();
		assertThat(props.getToken()).isNull();
	}

	@Test
	void customValues() {
		var props = new GemFireVectorStoreProperties();
		props.setIndexName("spring-ai-index");
		props.setHost("localhost");
		props.setPort(9090);
		props.setBeamWidth(10);
		props.setMaxConnections(10);
		props.setFields(new String[] { "test" });
		props.setBuckets(10);
		props.setUsername("username");
		props.setPassword("password");
		props.setToken("token");

		assertThat(props.getIndexName()).isEqualTo("spring-ai-index");
		assertThat(props.getHost()).isEqualTo("localhost");
		assertThat(props.getPort()).isEqualTo(9090);
		assertThat(props.getBeamWidth()).isEqualTo(10);
		assertThat(props.getMaxConnections()).isEqualTo(10);
		assertThat(props.getFields()).isEqualTo(new String[] { "test" });
		assertThat(props.getBuckets()).isEqualTo(10);
		assertThat(props.getUsername()).isEqualTo("username");
		assertThat(props.getPassword()).isEqualTo("password");
		assertThat(props.getToken()).isEqualTo("token");

	}

}
