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

package org.springframework.ai.bedrock.cohere.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.Truncate;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CohereEmbeddingBedrockApiTests {

	@Test
	void shouldUseInjectedRuntimeClients() {
		BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
		BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
		when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
			.body(SdkBytes.fromUtf8String(
					"{\"id\":\"embed-1\",\"embeddings\":[[1.0,2.0]],\"texts\":[\"hello\"],\"response_type\":\"embeddings\"}"))
			.build());

		CohereEmbeddingBedrockApi api = new CohereEmbeddingBedrockApi("cohere.embed-english-v3", client, asyncClient,
				"us-east-1");

		CohereEmbeddingResponse response = api
			.embedding(new CohereEmbeddingRequest(List.of("hello"), InputType.SEARCH_DOCUMENT, Truncate.END));

		assertThat(response.id()).isEqualTo("embed-1");
		assertThat(response.texts()).containsExactly("hello");
		assertThat(response.embeddings()).hasSize(1);
		verify(client).invokeModel(any(InvokeModelRequest.class));
	}

}
