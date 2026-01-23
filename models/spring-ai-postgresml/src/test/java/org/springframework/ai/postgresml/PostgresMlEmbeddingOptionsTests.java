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

		PostgresMlEmbeddingOptions options = embeddingModel.mergeOptions(EmbeddingOptions.builder().build());

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

	@Test
	public void builderWithEmptyKwargs() {
		PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder().kwargs(Map.of()).build();

		assertThat(options.getKwargs()).isEmpty();
		assertThat(options.getKwargs()).isNotNull();
	}

	@Test
	public void builderWithMultipleKwargs() {
		Map<String, Object> kwargs = Map.of("device", "gpu", "batch_size", 32, "max_length", 512, "normalize", true);

		PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder().kwargs(kwargs).build();

		assertThat(options.getKwargs()).hasSize(4);
		assertThat(options.getKwargs().get("device")).isEqualTo("gpu");
		assertThat(options.getKwargs().get("batch_size")).isEqualTo(32);
		assertThat(options.getKwargs().get("max_length")).isEqualTo(512);
		assertThat(options.getKwargs().get("normalize")).isEqualTo(true);
	}

	@Test
	public void allVectorTypes() {
		for (PostgresMlEmbeddingModel.VectorType vectorType : PostgresMlEmbeddingModel.VectorType.values()) {
			PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder().vectorType(vectorType).build();

			assertThat(options.getVectorType()).isEqualTo(vectorType);
		}
	}

	@Test
	public void allMetadataModes() {
		for (org.springframework.ai.document.MetadataMode mode : org.springframework.ai.document.MetadataMode
			.values()) {
			PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder().metadataMode(mode).build();

			assertThat(options.getMetadataMode()).isEqualTo(mode);
		}
	}

	@Test
	public void mergeOptionsWithNullInput() {
		var jdbcTemplate = Mockito.mock(JdbcTemplate.class);
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(jdbcTemplate);

		PostgresMlEmbeddingOptions options = embeddingModel.mergeOptions(null);

		// Should return default options when input is null
		assertThat(options.getTransformer()).isEqualTo(PostgresMlEmbeddingModel.DEFAULT_TRANSFORMER_MODEL);
		assertThat(options.getVectorType()).isEqualTo(PostgresMlEmbeddingModel.VectorType.PG_ARRAY);
		assertThat(options.getKwargs()).isEqualTo(Map.of());
		assertThat(options.getMetadataMode()).isEqualTo(org.springframework.ai.document.MetadataMode.EMBED);
	}

	@Test
	public void mergeOptionsPreservesOriginal() {
		var jdbcTemplate = Mockito.mock(JdbcTemplate.class);
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(jdbcTemplate);

		PostgresMlEmbeddingOptions original = PostgresMlEmbeddingOptions.builder()
			.transformer("original-model")
			.kwargs(Map.of("original", "value"))
			.build();

		PostgresMlEmbeddingOptions merged = embeddingModel.mergeOptions(original);

		// Verify original options are not modified
		assertThat(original.getTransformer()).isEqualTo("original-model");
		assertThat(original.getKwargs()).containsEntry("original", "value");

		// Verify merged options have expected values
		assertThat(merged.getTransformer()).isEqualTo("original-model");
	}

	@Test
	public void mergeOptionsWithComplexKwargs() {
		var jdbcTemplate = Mockito.mock(JdbcTemplate.class);
		PostgresMlEmbeddingModel embeddingModel = new PostgresMlEmbeddingModel(jdbcTemplate);

		Map<String, Object> complexKwargs = Map.of("device", "cuda:0", "model_kwargs",
				Map.of("trust_remote_code", true), "encode_kwargs",
				Map.of("normalize_embeddings", true, "batch_size", 64));

		PostgresMlEmbeddingOptions options = embeddingModel
			.mergeOptions(PostgresMlEmbeddingOptions.builder().kwargs(complexKwargs).build());

		assertThat(options.getKwargs()).hasSize(3);
		assertThat(options.getKwargs().get("device")).isEqualTo("cuda:0");
		assertThat(options.getKwargs().get("model_kwargs")).isInstanceOf(Map.class);
		assertThat(options.getKwargs().get("encode_kwargs")).isInstanceOf(Map.class);
	}

	@Test
	public void builderChaining() {
		PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder()
			.transformer("model-1")
			.transformer("model-2") // Should override previous value
			.vectorType(PostgresMlEmbeddingModel.VectorType.PG_VECTOR)
			.metadataMode(org.springframework.ai.document.MetadataMode.ALL)
			.kwargs(Map.of("key1", "value1"))
			.kwargs(Map.of("key2", "value2")) // Should override previous kwargs
			.build();

		assertThat(options.getTransformer()).isEqualTo("model-2");
		assertThat(options.getKwargs()).containsEntry("key2", "value2");
		assertThat(options.getKwargs()).doesNotContainKey("key1");
	}

	@Test
	public void settersModifyOptions() {
		PostgresMlEmbeddingOptions options = new PostgresMlEmbeddingOptions();

		options.setVectorType(PostgresMlEmbeddingModel.VectorType.PG_VECTOR);
		options.setKwargs(Map.of("key", "value"));
		options.setMetadataMode(org.springframework.ai.document.MetadataMode.NONE);

		assertThat(options.getVectorType()).isEqualTo(PostgresMlEmbeddingModel.VectorType.PG_VECTOR);
		assertThat(options.getKwargs()).containsEntry("key", "value");
		assertThat(options.getMetadataMode()).isEqualTo(org.springframework.ai.document.MetadataMode.NONE);
	}

	@Test
	public void getModelReturnsNull() {
		PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder().build();

		assertThat(options.getModel()).isNull();
	}

	@Test
	public void getDimensionsReturnsNull() {
		PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder().build();

		assertThat(options.getDimensions()).isNull();
	}

	@Test
	public void builderReturnsSameInstance() {
		PostgresMlEmbeddingOptions.Builder builder = PostgresMlEmbeddingOptions.builder().transformer("model-1");

		PostgresMlEmbeddingOptions options1 = builder.build();
		PostgresMlEmbeddingOptions options2 = builder.build();

		// Builder returns the same instance on multiple build() calls
		assertThat(options1).isSameAs(options2);
		assertThat(options1.getTransformer()).isEqualTo(options2.getTransformer());
	}

	@Test
	public void modifyingBuilderAfterBuildAffectsPreviousInstance() {
		PostgresMlEmbeddingOptions.Builder builder = PostgresMlEmbeddingOptions.builder().transformer("model-1");

		PostgresMlEmbeddingOptions options1 = builder.build();

		// Modifying builder after build
		builder.transformer("model-2");
		PostgresMlEmbeddingOptions options2 = builder.build();

		// Both instances are the same and have the updated value
		assertThat(options1).isSameAs(options2);
		assertThat(options1.getTransformer()).isEqualTo("model-2");
		assertThat(options2.getTransformer()).isEqualTo("model-2");
	}

	@Test
	public void setAdditionalParametersAcceptsNull() {
		PostgresMlEmbeddingOptions options = new PostgresMlEmbeddingOptions();
		options.setKwargs(null);

		assertThat(options.getKwargs()).isNull();
	}

}
