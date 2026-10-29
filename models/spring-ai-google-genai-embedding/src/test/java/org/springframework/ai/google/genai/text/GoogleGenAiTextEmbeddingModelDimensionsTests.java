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

package org.springframework.ai.google.genai.text;

import java.lang.reflect.Field;
import java.util.List;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.EmbedContentConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link GoogleGenAiTextEmbeddingModel#dimensions()}.
 *
 * @author Akshit Khandelwal
 */
class GoogleGenAiTextEmbeddingModelDimensionsTests {

	private final Models mockModels = mock(Models.class);

	private final GoogleGenAiEmbeddingConnectionDetails mockConnectionDetails = mock(
			GoogleGenAiEmbeddingConnectionDetails.class);

	@BeforeEach
	void setUp() throws Exception {
		Client mockGenAiClient = mock(Client.class);
		Field modelsField = Client.class.getDeclaredField("models");
		modelsField.setAccessible(true);
		modelsField.set(mockGenAiClient, this.mockModels);
		given(this.mockConnectionDetails.getGenAiClient()).willReturn(mockGenAiClient);
	}

	@Test
	void dimensionsOfKnownModel() {
		GoogleGenAiTextEmbeddingModel embeddingModel = createModel(
				GoogleGenAiTextEmbeddingOptions.builder().model("gemini-embedding-001").build());

		assertThat(embeddingModel.dimensions()).isEqualTo(3072);
		verify(this.mockModels, never()).embedContent(anyString(), any(List.class), any(EmbedContentConfig.class));
	}

	@Test
	void dimensionsUseConfiguredOutputDimensionality() {
		GoogleGenAiTextEmbeddingModel embeddingModel = createModel(
				GoogleGenAiTextEmbeddingOptions.builder().model("gemini-embedding-001").dimensions(768).build());

		assertThat(embeddingModel.dimensions()).isEqualTo(768);
		verify(this.mockModels, never()).embedContent(anyString(), any(List.class), any(EmbedContentConfig.class));
	}

	private GoogleGenAiTextEmbeddingModel createModel(GoogleGenAiTextEmbeddingOptions options) {
		return new GoogleGenAiTextEmbeddingModel(this.mockConnectionDetails, options, RetryUtils.SHORT_RETRY_TEMPLATE);
	}

}
