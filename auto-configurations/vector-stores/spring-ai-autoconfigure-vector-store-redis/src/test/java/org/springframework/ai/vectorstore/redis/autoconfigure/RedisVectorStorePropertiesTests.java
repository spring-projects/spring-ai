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

package org.springframework.ai.vectorstore.redis.autoconfigure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Julien Ruaux
 * @author Eddú Meléndez
 * @author Brian Sam-Bodden
 */
class RedisVectorStorePropertiesTests {

	@Test
	void defaultValues() {
		var props = new RedisVectorStoreProperties();
		assertThat(props.getIndexName()).isEqualTo("default-index");
		assertThat(props.getPrefix()).isEqualTo("default:");

		// Verify default HNSW parameters
		assertThat(props.getHnsw().getM()).isEqualTo(16);
		assertThat(props.getHnsw().getEfConstruction()).isEqualTo(200);
		assertThat(props.getHnsw().getEfRuntime()).isEqualTo(10);
	}

	@Test
	void customValues() {
		var props = new RedisVectorStoreProperties();
		props.setIndexName("myIdx");
		props.setPrefix("doc:");

		assertThat(props.getIndexName()).isEqualTo("myIdx");
		assertThat(props.getPrefix()).isEqualTo("doc:");
	}

	@Test
	void customHnswValues() {
		var props = new RedisVectorStoreProperties();
		RedisVectorStoreProperties.HnswProperties hnsw = props.getHnsw();

		hnsw.setM(32);
		hnsw.setEfConstruction(100);
		hnsw.setEfRuntime(50);

		assertThat(props.getHnsw().getM()).isEqualTo(32);
		assertThat(props.getHnsw().getEfConstruction()).isEqualTo(100);
		assertThat(props.getHnsw().getEfRuntime()).isEqualTo(50);
	}

}
