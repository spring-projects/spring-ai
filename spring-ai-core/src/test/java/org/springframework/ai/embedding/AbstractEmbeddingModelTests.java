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
package org.springframework.ai.embedding;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class AbstractEmbeddingModelTests {

	@Mock
	private EmbeddingModel embeddingModel;

	@Test
	public void testDefaultMethodImplementation() {

		EmbeddingModel dummy = new EmbeddingModel() {

			@Override
			public float[] embed(String text) {
				return new float[] { 0.1f, 0.1f, 0.1f };
			}

			@Override
			public float[] embed(Document document) {
				throw new UnsupportedOperationException("Unimplemented method 'embed'");
			}

			@Override
			public List<float[]> embed(List<String> texts) {
				throw new UnsupportedOperationException("Unimplemented method 'embed'");
			}

			@Override
			public EmbeddingResponse embedForResponse(List<String> texts) {
				throw new UnsupportedOperationException("Unimplemented method 'embedForResponse'");
			}

			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				throw new UnsupportedOperationException("Unimplemented method 'call'");
			}
		};

		assertThat(dummy.dimensions()).isEqualTo(3);
	}

	@ParameterizedTest
	@CsvFileSource(resources = "/embedding/embedding-model-dimensions.properties", numLinesToSkip = 1, delimiter = '=')
	public void testKnownEmbeddingModelDimensions(String model, String dimension) {
		assertThat(AbstractEmbeddingModel.dimensions(embeddingModel, model, "Hello world!"))
			.isEqualTo(Integer.valueOf(dimension));
		verify(embeddingModel, never()).embed(any(String.class));
		verify(embeddingModel, never()).embed(any(Document.class));
	}

	@Test
	public void testUnknownModelDimension() {
		when(embeddingModel.embed(eq("Hello world!"))).thenReturn(new float[]{0.1f, 0.1f, 0.1f});
		assertThat(AbstractEmbeddingModel.dimensions(embeddingModel, "unknown_model", "Hello world!")).isEqualTo(3);
	}

}
