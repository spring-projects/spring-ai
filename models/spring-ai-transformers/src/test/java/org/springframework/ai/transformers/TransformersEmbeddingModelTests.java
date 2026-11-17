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

package org.springframework.ai.transformers;

import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author Christian Tzolov
 */
public class TransformersEmbeddingModelTests {

	private static DecimalFormat DF = new DecimalFormat("#.#####");

	@Test
	void embed() throws Exception {

		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		float[] embed = embeddingModel.embed("Hello world");
		assertThat(embed).hasSize(384);
		assertThat(DF.format(embed[0])).isEqualTo(DF.format(-0.19744634628295898));
		assertThat(DF.format(embed[383])).isEqualTo(DF.format(0.17298996448516846));
	}

	@Test
	void embedDocument() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		float[] embed = embeddingModel.embed(new Document("Hello world"));
		assertThat(embed).hasSize(384);
		assertThat(DF.format(embed[0])).isEqualTo(DF.format(-0.19744634628295898));
		assertThat(DF.format(embed[383])).isEqualTo(DF.format(0.17298996448516846));
	}

	@Test
	void embedList() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		List<float[]> embed = embeddingModel.embed(List.of("Hello world", "World is big"));
		assertThat(embed).hasSize(2);
		assertThat(embed.get(0)).hasSize(384);
		assertThat(DF.format(embed.get(0)[0])).isEqualTo(DF.format(-0.19744634628295898));
		assertThat(DF.format(embed.get(0)[383])).isEqualTo(DF.format(0.17298996448516846));

		assertThat(embed.get(1)).hasSize(384);
		assertThat(DF.format(embed.get(1)[0])).isEqualTo(DF.format(0.4293745160102844));
		assertThat(DF.format(embed.get(1)[383])).isEqualTo(DF.format(0.05501303821802139));

		assertThat(embed.get(0)).isNotEqualTo(embed.get(1));
	}

	@Test
	void embedForResponse() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		EmbeddingResponse embed = embeddingModel.embedForResponse(List.of("Hello world", "World is big"));
		assertThat(embed.getResults()).hasSize(2);
		assertTrue(embed.getMetadata().isEmpty(), "Expected embed metadata to be empty, but it was not.");

		assertThat(embed.getResults().get(0).getOutput()).hasSize(384);
		assertThat(DF.format(embed.getResults().get(0).getOutput()[0])).isEqualTo(DF.format(-0.19744634628295898));
		assertThat(DF.format(embed.getResults().get(0).getOutput()[383])).isEqualTo(DF.format(0.17298996448516846));

		assertThat(embed.getResults().get(1).getOutput()).hasSize(384);
		assertThat(DF.format(embed.getResults().get(1).getOutput()[0])).isEqualTo(DF.format(0.4293745160102844));
		assertThat(DF.format(embed.getResults().get(1).getOutput()[383])).isEqualTo(DF.format(0.05501303821802139));
	}

	@Test
	void dimensions() throws Exception {

		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		assertThat(embeddingModel.dimensions()).isEqualTo(384);
		// cached
		assertThat(embeddingModel.dimensions()).isEqualTo(384);
	}

	@Test
	void closeReleasesNativeResources() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		HuggingFaceTokenizer tokenizer = mock(HuggingFaceTokenizer.class);
		OrtSession session = mock(OrtSession.class);
		ReflectionTestUtils.setField(embeddingModel, "tokenizer", tokenizer);
		ReflectionTestUtils.setField(embeddingModel, "session", session);

		embeddingModel.close();

		verify(tokenizer).close();
		verify(session).close();
	}

	@Test
	void sessionOptionsCustomizerIsApplied() throws Exception {
		AtomicReference<OrtSession.SessionOptions> customized = new AtomicReference<>();
		try (TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel()) {
			embeddingModel.setSessionOptionsCustomizer(options -> {
				options.setIntraOpNumThreads(1);
				customized.set(options);
			});
			embeddingModel.afterPropertiesSet();

			assertThat(customized.get()).isNotNull();
			float[] embed = embeddingModel.embed("Hello world");
			assertThat(embed).hasSize(384);
			assertThat(DF.format(embed[0])).isEqualTo(DF.format(-0.19744634628295898));
		}
	}

	@Test
	void sessionOptionsCustomizerFailurePropagates() throws Exception {
		try (TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel()) {
			embeddingModel.setSessionOptionsCustomizer(options -> {
				throw new OrtException("customizer failed");
			});

			assertThatThrownBy(embeddingModel::afterPropertiesSet).isInstanceOf(OrtException.class)
				.hasMessageContaining("customizer failed");
			assertThat(ReflectionTestUtils.getField(embeddingModel, "sessionOptions")).isNull();
		}
	}

	@Test
	void closeReleasesSessionOptions() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		OrtSession session = mock(OrtSession.class);
		OrtSession.SessionOptions sessionOptions = mock(OrtSession.SessionOptions.class);
		ReflectionTestUtils.setField(embeddingModel, "session", session);
		ReflectionTestUtils.setField(embeddingModel, "sessionOptions", sessionOptions);

		embeddingModel.close();

		InOrder inOrder = inOrder(session, sessionOptions);
		inOrder.verify(session).close();
		inOrder.verify(sessionOptions).close();
	}

	@Test
	void closeReleasesSessionOptionsWhenSessionCloseFails() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		OrtSession session = mock(OrtSession.class);
		OrtSession.SessionOptions sessionOptions = mock(OrtSession.SessionOptions.class);
		doThrow(new OrtException("boom")).when(session).close();
		ReflectionTestUtils.setField(embeddingModel, "session", session);
		ReflectionTestUtils.setField(embeddingModel, "sessionOptions", sessionOptions);

		assertThatThrownBy(embeddingModel::close).isInstanceOf(OrtException.class);
		verify(sessionOptions).close();
	}

	@Test
	void closeBeforeInitializationIsSafe() {
		assertThatNoException().isThrownBy(() -> new TransformersEmbeddingModel().close());
	}

}
