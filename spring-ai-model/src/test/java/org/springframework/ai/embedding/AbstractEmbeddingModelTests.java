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

package org.springframework.ai.embedding;

import java.util.List;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

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

	@Test
	public void testMetadataEmbedding() {

		String[] stringToEmbed = new String[] { null };

		// spy writing the content of the embedding request into stringToEmbedd
		class LoggingEmbeddingModel extends AbstractEmbeddingModel {

			public LoggingEmbeddingModel() {
			}

			public LoggingEmbeddingModel(MetadataMode metadataMode) {
				super(metadataMode);
			}

			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {

				stringToEmbed[0] = request.getInstructions().get(0);

				return new EmbeddingResponse(List.of(new Embedding(new float[] { 1.0f }, 0)));
			}

		}

		Document document = Document.builder()
			.text("Hello world!")
			.metadata("toEmbed", "should be included by default")
			.metadata("notToEmbedd", "should not be included by default")
			.build();

		document.setContentFormatter(
				DefaultContentFormatter.builder().withExcludedEmbedMetadataKeys("notToEmbedd").build());

		SoftAssertions.assertSoftly(softly -> {
			new LoggingEmbeddingModel().embed(document);

			softly.assertThat(stringToEmbed[0])
				.contains("Hello world!")
				.contains("should be included by default")
				.doesNotContain("should not be included by default");

			new LoggingEmbeddingModel(MetadataMode.ALL).embed(document);

			softly.assertThat(stringToEmbed[0])
				.contains("Hello world!")
				.contains("should be included by default")
				.contains("should not be included by default");
		});
	}

	@ParameterizedTest
	@CsvFileSource(resources = "/embedding/embedding-model-dimensions.properties", numLinesToSkip = 1, delimiter = '=')
	public void testKnownEmbeddingModelDimensions(String model, String dimension) {
		assertThat(AbstractEmbeddingModel.dimensions(this.embeddingModel, model, "Hello world!"))
			.isEqualTo(Integer.valueOf(dimension));
		verify(this.embeddingModel, never()).embed(any(String.class));
		verify(this.embeddingModel, never()).embed(any(Document.class));
	}

	@Test
	public void testUnknownModelDimension() {
		given(this.embeddingModel.embed(eq("Hello world!"))).willReturn(new float[] { 0.1f, 0.1f, 0.1f });
		assertThat(AbstractEmbeddingModel.dimensions(this.embeddingModel, "unknown_model", "Hello world!"))
			.isEqualTo(3);
	}

}
