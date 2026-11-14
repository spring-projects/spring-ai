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

package org.springframework.ai.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.core.OverridingClassLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Md Nayan Mia
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
		assertThat(AbstractEmbeddingModel.dimensions(this.embeddingModel, model, "Hello world!"))
			.isEqualTo(Integer.valueOf(dimension));
		verify(this.embeddingModel, never()).embed(any(String.class));
		verify(this.embeddingModel, never()).embed(any(Document.class));
	}

	@Test
	public void testKnownModelDimensionsWithIsolatedContextClassLoader() throws Exception {
		ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
		ClassLoader modelClassLoader = new OverridingClassLoader(getClass().getClassLoader()) {
			@Override
			protected boolean isEligibleForOverriding(String className) {
				return className.equals(AbstractEmbeddingModel.class.getName());
			}
		};
		try {
			Thread.currentThread().setContextClassLoader(ClassLoader.getPlatformClassLoader());
			Class<?> modelClass = Class.forName(AbstractEmbeddingModel.class.getName(), true, modelClassLoader);
			assertThat(modelClass.getClassLoader()).isSameAs(modelClassLoader);
			Object dimensions = modelClass.getMethod("dimensions", EmbeddingModel.class, String.class, String.class)
				.invoke(null, this.embeddingModel, "text-embedding-ada-002", "Hello world!");
			assertThat(dimensions).isEqualTo(1536);
			verify(this.embeddingModel, never()).embed(any(String.class));
		}
		finally {
			Thread.currentThread().setContextClassLoader(originalClassLoader);
		}
	}

	@Test
	public void testUnknownModelDimension() {
		given(this.embeddingModel.embed(eq("Hello world!"))).willReturn(new float[] { 0.1f, 0.1f, 0.1f });
		assertThat(AbstractEmbeddingModel.dimensions(this.embeddingModel, "unknown_model", "Hello world!"))
			.isEqualTo(3);
	}

	@Test
	public void testGetEmbeddingContentDefaultReturnsText() {
		EmbeddingModel model = createDummyEmbeddingModel(null);
		Document document = new Document("raw text", Map.of("key", "value"));

		assertThat(model.getEmbeddingContent(document)).isEqualTo("raw text");
	}

	@Test
	public void testBatchedEmbedUsesGetEmbeddingContent() {
		// Create a model that overrides getEmbeddingContent to use MetadataMode,
		// simulating what OpenAI and other MetadataMode-aware models do.
		List<String> capturedTexts = new ArrayList<>();
		EmbeddingModel model = createDummyEmbeddingModel(MetadataMode.EMBED);

		Document doc = new Document("Some content", Map.of("title", "Getting Started"));

		model.embed(List.of(doc), EmbeddingOptions.builder().build(), new TokenCountBatchingStrategy());

		// Verify that the text sent for embedding includes metadata,
		// not just the raw text from Document.getText()
		String embeddingContent = model.getEmbeddingContent(doc);
		assertThat(embeddingContent).contains("title: Getting Started");
	}

	@Test
	public void testBatchedEmbedWithoutMetadataModeUsesRawText() {
		EmbeddingModel model = createDummyEmbeddingModel(null);

		Document doc = new Document("Some content", Map.of("title", "Getting Started"));

		model.embed(List.of(doc), EmbeddingOptions.builder().build(), new TokenCountBatchingStrategy());

		// Without MetadataMode override, getEmbeddingContent returns raw text
		String embeddingContent = model.getEmbeddingContent(doc);
		assertThat(embeddingContent).isEqualTo("Some content");
	}

	@Test
	public void testConcurrentBatchedEmbedWithMultipleBatches() {
		EmbeddingModel model = new EmbeddingModel() {
			@Override
			public float[] embed(Document document) {
				return new float[] { 1.0f };
			}

			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				List<Embedding> embeddings = new ArrayList<>();
				for (int i = 0; i < request.getInstructions().size(); i++) {
					String text = request.getInstructions().get(i);
					float val = Float.parseFloat(text);
					embeddings.add(new Embedding(new float[] { val }, i));
				}
				return new EmbeddingResponse(embeddings);
			}
		};

		BatchingStrategy batchingStrategy = docs -> List.of(docs.subList(0, 2), docs.subList(2, 4));

		List<Document> docs = List.of(new Document("10.0"), new Document("20.0"), new Document("30.0"),
				new Document("40.0"));

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<float[]> embeddings = model.embed(docs, EmbeddingOptions.builder().build(), batchingStrategy,
					executor);

			assertThat(embeddings).hasSize(4);
			assertThat(embeddings.get(0)).containsExactly(10.0f);
			assertThat(embeddings.get(1)).containsExactly(20.0f);
			assertThat(embeddings.get(2)).containsExactly(30.0f);
			assertThat(embeddings.get(3)).containsExactly(40.0f);
		}
		finally {
			executor.shutdown();
		}
	}

	@Test
	public void testConcurrentBatchedEmbedEmptyDocuments() {
		EmbeddingModel model = createDummyEmbeddingModel(null);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			List<float[]> embeddings = model.embed(List.of(), EmbeddingOptions.builder().build(),
					new TokenCountBatchingStrategy(), executor);
			assertThat(embeddings).isEmpty();
		}
		finally {
			executor.shutdown();
		}
	}

	@Test
	public void testConcurrentBatchedEmbedNullArguments() {
		EmbeddingModel model = createDummyEmbeddingModel(null);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			assertThatThrownBy(() -> model.embed(null, null, new TokenCountBatchingStrategy(), executor))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> model.embed(List.of(), null, null, executor))
				.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> model.embed(List.of(), null, new TokenCountBatchingStrategy(), null))
				.isInstanceOf(IllegalArgumentException.class);
		}
		finally {
			executor.shutdown();
		}
	}

	private EmbeddingModel createDummyEmbeddingModel(MetadataMode metadataMode) {
		return new EmbeddingModel() {

			@Override
			public String getEmbeddingContent(Document document) {
				if (metadataMode != null) {
					return document.getFormattedContent(metadataMode);
				}
				return document.getText();
			}

			@Override
			public float[] embed(Document document) {
				return this.embed(getEmbeddingContent(document));
			}

			@Override
			public float[] embed(String text) {
				return new float[] { 0.1f, 0.2f, 0.3f };
			}

			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				List<Embedding> embeddings = new ArrayList<>();
				for (int i = 0; i < request.getInstructions().size(); i++) {
					embeddings.add(new Embedding(new float[] { 0.1f, 0.2f, 0.3f }, i));
				}
				return new EmbeddingResponse(embeddings);
			}
		};
	}

}
