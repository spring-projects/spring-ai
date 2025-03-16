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

package org.springframework.ai.zhipuai.api;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletion;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionChunk;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionMessage;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionRequest;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.Embedding;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.EmbeddingList;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
@EnabledIfEnvironmentVariable(named = "ZHIPU_AI_API_KEY", matches = ".+")
public class ZhiPuAiApiIT {

	ZhiPuAiApi zhiPuAiApi = new ZhiPuAiApi(System.getenv("ZHIPU_AI_API_KEY"));

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ResponseEntity<ChatCompletion> response = this.zhiPuAiApi
			.chatCompletionEntity(new ChatCompletionRequest(List.of(chatCompletionMessage), "glm-3-turbo", 0.7, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionEntityWithMoreParams() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ResponseEntity<ChatCompletion> response = this.zhiPuAiApi
			.chatCompletionEntity(new ChatCompletionRequest(List.of(chatCompletionMessage), "glm-3-turbo", 1024, null,
					false, 0.95, 0.7, null, null, null, "test_request_id", false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		Flux<ChatCompletionChunk> response = this.zhiPuAiApi
			.chatCompletionStream(new ChatCompletionRequest(List.of(chatCompletionMessage), "glm-3-turbo", 0.7, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

	@Test
	void embeddings() {
		ResponseEntity<EmbeddingList<Embedding>> response = this.zhiPuAiApi
			.embeddings(new ZhiPuAiApi.EmbeddingRequest<>("Hello world"));

		assertThat(response).isNotNull();
		assertThat(Objects.requireNonNull(response.getBody()).data()).hasSize(1);
		assertThat(response.getBody().data().get(0).embedding()).hasSize(1024);
	}

	@Test
	void embeddingsWithDimensions() {
		ResponseEntity<EmbeddingList<Embedding>> response = this.zhiPuAiApi
			.embeddings(new ZhiPuAiApi.EmbeddingRequest<>("Hello world",
					ZhiPuAiApi.EmbeddingModel.Embedding_3.getValue(), 1536));

		assertThat(response).isNotNull();
		assertThat(Objects.requireNonNull(response.getBody()).data()).hasSize(1);
		assertThat(response.getBody().data().get(0).embedding()).hasSize(1536);
	}

}
