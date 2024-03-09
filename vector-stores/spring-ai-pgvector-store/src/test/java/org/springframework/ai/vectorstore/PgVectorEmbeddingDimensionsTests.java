/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.vectorstore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class PgVectorEmbeddingDimensionsTests {

	@Mock
	private EmbeddingClient embeddingClient;

	@Mock
	private JdbcTemplate jdbcTemplate;

	@Test
	public void explicitlySetDimensions() {

		final int explicitDimensions = 696;

		var dim = new PgVectorStore(jdbcTemplate, embeddingClient, explicitDimensions).embeddingDimensions();

		assertThat(dim).isEqualTo(explicitDimensions);
		verify(embeddingClient, never()).dimensions();
	}

	@Test
	public void embeddingClientDimensions() {
		when(embeddingClient.dimensions()).thenReturn(969);

		var dim = new PgVectorStore(jdbcTemplate, embeddingClient).embeddingDimensions();

		assertThat(dim).isEqualTo(969);

		verify(embeddingClient, only()).dimensions();
	}

	@Test
	public void fallBackToDefaultDimensions() {

		when(embeddingClient.dimensions()).thenThrow(new RuntimeException());

		var dim = new PgVectorStore(jdbcTemplate, embeddingClient).embeddingDimensions();

		assertThat(dim).isEqualTo(PgVectorStore.OPENAI_EMBEDDING_DIMENSION_SIZE);
		verify(embeddingClient, only()).dimensions();
	}

}
