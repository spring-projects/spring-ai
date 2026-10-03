/*
 * Copyright 2023-present the original author or authors.
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

import java.util.ArrayList;
import java.util.List;

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

	@Test
	void defaultSearchStrategyPreservesProcedurePath() {
		var store = Neo4jVectorStore.builder(this.driver, this.embeddingModel).build();
		assertThat(ReflectionTestUtils.getField(store, "searchStrategy"))
			.isEqualTo(Neo4jVectorStore.SearchStrategy.VECTOR_QUERY);
		assertThat(ReflectionTestUtils.getField(store, "filterableMetadataFields")).isEqualTo(List.of());
	}

	@Test
	void searchConfigurationCopiesMetadataFields() {
		var fields = new ArrayList<>(List.of("category", "category"));
		var builder = Neo4jVectorStore.builder(this.driver, this.embeddingModel)
			.searchStrategy(Neo4jVectorStore.SearchStrategy.SEARCH)
			.filterableMetadataFields(fields);
		fields.add("year");
		var store = builder.build();
		assertThat(ReflectionTestUtils.getField(store, "searchStrategy"))
			.isEqualTo(Neo4jVectorStore.SearchStrategy.SEARCH);
		assertThat(ReflectionTestUtils.getField(store, "filterableMetadataFields")).isEqualTo(List.of("category"));
	}

	@Test
	void rejectsMetadataFieldsWithLegacyStrategy() {
		assertThatThrownBy(() -> Neo4jVectorStore.builder(this.driver, this.embeddingModel)
			.filterableMetadataFields(List.of("category"))
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("requires the SEARCH strategy");
	}

	@Test
	void rejectsCustomConverterWithSearchStrategy() {
		assertThatThrownBy(() -> Neo4jVectorStore.builder(this.driver, this.embeddingModel)
			.searchStrategy(Neo4jVectorStore.SearchStrategy.SEARCH)
			.filterExpressionConverter(mock(FilterExpressionConverter.class))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("custom filterExpressionConverter");
	}

	@Test
	void rejectsInvalidSearchConfiguration() {
		var builder = Neo4jVectorStore.builder(this.driver, this.embeddingModel);
		assertThatThrownBy(() -> builder.searchStrategy(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.filterableMetadataFields(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> builder.filterableMetadataFields(List.of(" ")))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
