/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.vectorstore.neo4j;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.filter.FilterExpressionConverter;
import org.springframework.ai.vectorstore.neo4j.filter.Neo4jVectorFilterExpressionConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * @author Soby Chacko
 */
class Neo4jVectorStoreBuilderTests {

	private final Driver driver = mock(Driver.class);

	private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

	@Test
	void defaultFilterExpressionConverter() {
		Neo4jVectorStore store = Neo4jVectorStore.builder(this.driver, this.embeddingModel).build();
		Object converter = ReflectionTestUtils.getField(store, "filterExpressionConverter");
		assertThat(converter).isInstanceOf(Neo4jVectorFilterExpressionConverter.class);
	}

	@Test
	void customFilterExpressionConverter() {
		FilterExpressionConverter custom = mock(FilterExpressionConverter.class);
		Neo4jVectorStore store = Neo4jVectorStore.builder(this.driver, this.embeddingModel)
			.filterExpressionConverter(custom)
			.build();
		Object converter = ReflectionTestUtils.getField(store, "filterExpressionConverter");
		assertThat(converter).isSameAs(custom);
	}

	@Test
	void nullFilterExpressionConverterThrows() {
		assertThatThrownBy(
				() -> Neo4jVectorStore.builder(this.driver, this.embeddingModel).filterExpressionConverter(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("FilterExpressionConverter must not be null");
	}

}
