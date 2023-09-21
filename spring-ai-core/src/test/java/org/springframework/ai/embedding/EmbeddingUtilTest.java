/*
 * Copyright 2023-2023 the original author or authors.
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
public class EmbeddingUtilTest {

	@Mock
	private EmbeddingClient embeddingClient;

	@Test
	public void testDefaultMethodImplementation() {

		EmbeddingClient dummy = new EmbeddingClient() {

			@Override
			public List<Double> embed(String text) {
				return List.of(0.1, 0.1, 0.1);
			}

			@Override
			public List<Double> embed(Document document) {
				throw new UnsupportedOperationException("Unimplemented method 'embed'");
			}

			@Override
			public List<List<Double>> embed(List<String> texts) {
				throw new UnsupportedOperationException("Unimplemented method 'embed'");
			}

			@Override
			public EmbeddingResponse embedForResponse(List<String> texts) {
				throw new UnsupportedOperationException("Unimplemented method 'embedForResponse'");
			}
		};

		assertThat(dummy.dimensions()).isEqualTo(3);
	}

	@ParameterizedTest
	@CsvFileSource(resources = "/embedding/embedding-model-dimensions.properties", numLinesToSkip = 1, delimiter = '=')
	public void testKnownEmbeddingModelDimensions(String model, String dimension) {
		assertThat(EmbeddingUtil.dimensions(embeddingClient, model)).isEqualTo(Integer.valueOf(dimension));
		verify(embeddingClient, never()).embed(any(String.class));
		verify(embeddingClient, never()).embed(any(Document.class));
	}

	@Test
	public void testUnknownModelDimension() {
		when(embeddingClient.embed(eq("Test String"))).thenReturn(List.of(0.1, 0.1, 0.1));
		assertThat(EmbeddingUtil.dimensions(embeddingClient, "unknown_model")).isEqualTo(3);
	}

}
