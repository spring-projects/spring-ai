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

package org.springframework.ai.bedrock.titan.api;

import java.util.Map;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingRequest;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TitanEmbeddingBedrockApiTests {

	@Test
	void shouldUseInjectedRuntimeClients() {
		BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
		BedrockRuntimeAsyncClient asyncClient = mock(BedrockRuntimeAsyncClient.class);
		when(client.invokeModel(any(InvokeModelRequest.class))).thenReturn(InvokeModelResponse.builder()
			.body(SdkBytes.fromUtf8String(
					"{\"embedding\":[1.0,2.0],\"inputTextTokenCount\":2,\"successCount\":1,\"failureCount\":0,\"embeddingsByType\":{}}"))
			.build());

		TitanEmbeddingBedrockApi api = new TitanEmbeddingBedrockApi("amazon.titan-embed-text-v2:0", client, asyncClient,
				"us-east-1");

		TitanEmbeddingResponse response = api.embedding(TitanEmbeddingRequest.builder().inputText("hello").build());

		assertThat(response.inputTextTokenCount()).isEqualTo(2);
		assertThat(response.successCount()).isEqualTo(1);
		assertThat(response.failureCount()).isEqualTo(0);
		assertThat(response.embeddingsByType()).isEqualTo(Map.of());
		verify(client).invokeModel(any(InvokeModelRequest.class));
	}

}
