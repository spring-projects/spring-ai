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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class PgVectorEmbeddingDimensionsTests {

	@Mock
	private EmbeddingModel embeddingModel;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Test
	public void explicitlySetDimensions() {

		final int explicitDimensions = 696;

		PgVectorStore pgVectorStore = PgVectorStore.builder()
			.jdbcTemplate(this.jdbcTemplate)
			.embeddingModel(this.embeddingModel)
			.dimensions(explicitDimensions)
			.build();
		var dim = pgVectorStore.embeddingDimensions();

		assertThat(dim).isEqualTo(explicitDimensions);
		verify(this.embeddingModel, never()).dimensions();
	}

	@Test
	public void embeddingModelDimensions() {
		given(this.embeddingModel.dimensions()).willReturn(969);

		PgVectorStore pgVectorStore = PgVectorStore.builder()
			.jdbcTemplate(this.jdbcTemplate)
			.embeddingModel(this.embeddingModel)
			.build();
		var dim = pgVectorStore.embeddingDimensions();

		assertThat(dim).isEqualTo(969);

		verify(this.embeddingModel, only()).dimensions();
	}

	@Test
	public void fallBackToDefaultDimensions() {

		given(this.embeddingModel.dimensions()).willThrow(new RuntimeException());

		PgVectorStore pgVectorStore = PgVectorStore.builder()
			.jdbcTemplate(this.jdbcTemplate)
			.embeddingModel(this.embeddingModel)
			.build();
		var dim = pgVectorStore.embeddingDimensions();

		assertThat(dim).isEqualTo(PgVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE);
		verify(this.embeddingModel, only()).dimensions();
	}

}
