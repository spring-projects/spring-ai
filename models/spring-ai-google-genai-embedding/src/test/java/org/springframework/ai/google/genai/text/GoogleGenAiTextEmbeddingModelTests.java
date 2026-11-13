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
import java.util.Optional;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.ContentEmbedding;
import com.google.genai.types.EmbedContentConfig;
import com.google.genai.types.EmbedContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions.TaskType;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link GoogleGenAiTextEmbeddingModel} request building. Verifies that
 * the configured {@code taskType} is forwarded to {@link EmbedContentConfig} (see
 * GH-5966).
 *
 * @author Jewoo Shin
 */
@ExtendWith(MockitoExtension.class)
public class GoogleGenAiTextEmbeddingModelTests {

	private Client mockGenAiClient;

	@Mock
	private Models mockModels;

	@Mock
	private GoogleGenAiEmbeddingConnectionDetails mockConnectionDetails;

	private GoogleGenAiTextEmbeddingModel embeddingModel;

	@BeforeEach
	public void setUp() throws Exception {
		this.mockGenAiClient = mock(Client.class);
		Field modelsField = Client.class.getDeclaredField("models");
		modelsField.setAccessible(true);
		modelsField.set(this.mockGenAiClient, this.mockModels);

		given(this.mockConnectionDetails.getGenAiClient()).willReturn(this.mockGenAiClient);
		given(this.mockConnectionDetails.getModelEndpointName(anyString()))
			.willAnswer(invocation -> invocation.getArgument(0));

		this.embeddingModel = new GoogleGenAiTextEmbeddingModel(this.mockConnectionDetails,
				GoogleGenAiTextEmbeddingOptions.builder().build(), RetryUtils.SHORT_RETRY_TEMPLATE);
	}

	@Test
	public void taskTypeIsPassedToEmbedContentConfig() {
		EmbedContentConfig captured = invokeAndCapture(
				GoogleGenAiTextEmbeddingOptions.builder().model("model").taskType(TaskType.RETRIEVAL_QUERY).build());

		assertThat(captured.taskType()).contains("RETRIEVAL_QUERY");
	}

	@Test
	public void taskTypeDefaultsToRetrievalDocumentWhenNotSet() {
		EmbedContentConfig captured = invokeAndCapture(
				GoogleGenAiTextEmbeddingOptions.builder().model("model").build());

		assertThat(captured.taskType()).contains("RETRIEVAL_DOCUMENT");
	}

	private EmbedContentConfig invokeAndCapture(GoogleGenAiTextEmbeddingOptions requestOptions) {
		ContentEmbedding mockEmbedding = mock(ContentEmbedding.class);
		given(mockEmbedding.values()).willReturn(Optional.of(List.of(1.0f, 2.0f)));
		given(mockEmbedding.statistics()).willReturn(Optional.empty());

		EmbedContentResponse mockResponse = mock(EmbedContentResponse.class);
		given(mockResponse.embeddings()).willReturn(Optional.of(List.of(mockEmbedding)));

		given(this.mockModels.embedContent(anyString(), any(List.class), any(EmbedContentConfig.class)))
			.willReturn(mockResponse);

		this.embeddingModel.call(new EmbeddingRequest(List.of("text"), requestOptions));

		ArgumentCaptor<EmbedContentConfig> captor = ArgumentCaptor.forClass(EmbedContentConfig.class);
		verify(this.mockModels).embedContent(anyString(), any(List.class), captor.capture());
		return captor.getValue();
	}

}
