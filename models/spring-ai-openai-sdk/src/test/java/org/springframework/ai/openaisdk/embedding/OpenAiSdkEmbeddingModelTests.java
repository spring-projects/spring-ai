/*
 * Copyright 2026-2026 the original author or authors.
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

package org.springframework.ai.openaisdk.embedding;

import java.util.List;
import java.util.Map;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.services.blocking.EmbeddingService;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openaisdk.OpenAiSdkEmbeddingModel;
import org.springframework.ai.openaisdk.OpenAiSdkEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAiSdkEmbeddingModelTests {

	@Mock
	private OpenAIClient openAiClient;

	@Mock
	private EmbeddingService embeddingService;

	@Test
	void shouldPassCustomHeadersToEmbeddingCreateParams() {
		when(this.openAiClient.embeddings()).thenReturn(this.embeddingService);
		when(this.embeddingService.create(any(EmbeddingCreateParams.class))).thenReturn(createEmbeddingResponse());

		OpenAiSdkEmbeddingModel embeddingModel = new OpenAiSdkEmbeddingModel(this.openAiClient, null,
				OpenAiSdkEmbeddingOptions.builder().model("text-embedding-3-small").build(), ObservationRegistry.NOOP);

		embeddingModel.call(new EmbeddingRequest(List.of("hello world"),
				OpenAiSdkEmbeddingOptions.builder().customHeaders(Map.of("X-Test-Header", "test-value")).build()));

		ArgumentCaptor<EmbeddingCreateParams> paramsCaptor = ArgumentCaptor.forClass(EmbeddingCreateParams.class);
		verify(this.embeddingService).create(paramsCaptor.capture());
		assertThat(paramsCaptor.getValue()._additionalHeaders().values("X-Test-Header")).containsExactly("test-value");
	}

	private static CreateEmbeddingResponse createEmbeddingResponse() {
		return CreateEmbeddingResponse.builder()
			.addData(Embedding.builder().embedding(List.of(0.1f, 0.2f)).index(0).build())
			.model("text-embedding-3-small")
			.usage(CreateEmbeddingResponse.Usage.builder().promptTokens(2).totalTokens(2).build())
			.build();
	}

}
