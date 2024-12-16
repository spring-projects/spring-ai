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

package org.springframework.ai.postgresml;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class PostgresMlEmbeddingOptionsTests {

	@Test
	public void defaultOptions() {
		PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder().build();

		assertThat(options.getTransformer()).isEqualTo(PostgresMlEmbeddingModel.DEFAULT_TRANSFORMER_MODEL);
		assertThat(options.getVectorType()).isEqualTo(PostgresMlEmbeddingModel.VectorType.PG_ARRAY);
		assertThat(options.getKwargs()).isEqualTo(Map.of());
		assertThat(options.getMetadataMode()).isEqualTo(org.springframework.ai.document.MetadataMode.EMBED);
	}

	@Test
	public void newOptions() {
		PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder()
			.transformer("intfloat/e5-small")
			.vectorType(PostgresMlEmbeddingModel.VectorType.PG_VECTOR)
			.metadataMode(org.springframework.ai.document.MetadataMode.ALL)
			.kwargs(Map.of("device", "cpu"))
			.build();

		assertThat(options.getTransformer()).isEqualTo("intfloat/e5-small");
		assertThat(options.getVectorType()).isEqualTo(PostgresMlEmbeddingModel.VectorType.PG_VECTOR);
		assertThat(options.getKwargs()).isEqualTo(Map.of("device", "cpu"));
		assertThat(options.getMetadataMode()).isEqualTo(org.springframework.ai.document.MetadataMode.ALL);
	}

	@Test
	public void mergeOptions() {

		var jdbcTemplate = Mockito.mock(JdbcTemplate.class);
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(jdbcTemplate);

		PostgresMlEmbeddingOptions options = embeddingModel.mergeOptions(EmbeddingOptions.EMPTY);

		// Default options
		assertThat(options.getTransformer()).isEqualTo(PostgresMlEmbeddingModel.DEFAULT_TRANSFORMER_MODEL);
		assertThat(options.getVectorType()).isEqualTo(PostgresMlEmbeddingModel.VectorType.PG_ARRAY);
		assertThat(options.getKwargs()).isEqualTo(Map.of());
		assertThat(options.getMetadataMode()).isEqualTo(org.springframework.ai.document.MetadataMode.EMBED);

		// Partial override
		options = embeddingModel.mergeOptions(PostgresMlEmbeddingOptions.builder()
			.transformer("intfloat/e5-small")
			.kwargs(Map.of("device", "cpu"))
			.build());

		assertThat(options.getTransformer()).isEqualTo("intfloat/e5-small");
		assertThat(options.getVectorType()).isEqualTo(PostgresMlEmbeddingModel.VectorType.PG_ARRAY); // Default
		assertThat(options.getKwargs()).isEqualTo(Map.of("device", "cpu"));
		assertThat(options.getMetadataMode()).isEqualTo(org.springframework.ai.document.MetadataMode.EMBED); // Default

		// Complete override
		options = embeddingModel.mergeOptions(PostgresMlEmbeddingOptions.builder()
			.transformer("intfloat/e5-small")
			.vectorType(PostgresMlEmbeddingModel.VectorType.PG_VECTOR)
			.metadataMode(org.springframework.ai.document.MetadataMode.ALL)
			.kwargs(Map.of("device", "cpu"))
			.build());

		assertThat(options.getTransformer()).isEqualTo("intfloat/e5-small");
		assertThat(options.getVectorType()).isEqualTo(PostgresMlEmbeddingModel.VectorType.PG_VECTOR);
		assertThat(options.getKwargs()).isEqualTo(Map.of("device", "cpu"));
		assertThat(options.getMetadataMode()).isEqualTo(org.springframework.ai.document.MetadataMode.ALL);
	}

}
