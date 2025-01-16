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

package org.springframework.ai.hunyuan.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletion;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionChunk;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionMessage;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionMessage.Role;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionRequest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
@EnabledIfEnvironmentVariable(named = "HUNYUAN_SECRET_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "HUNYUAN_SECRET_KEY", matches = ".+")
public class HunYuanApiIT {

	HunYuanApi hunyuanApi = new HunYuanApi(System.getenv("HUNYUAN_SECRET_ID"),System.getenv("HUNYUAN_SECRET_KEY"));

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("你好！", Role.user);
		ResponseEntity<HunYuanApi.ChatCompletionResponse> response = this.hunyuanApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(chatCompletionMessage), HunYuanApi.ChatModel.HUNYUAN_PRO.getValue(), 0.8,false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		System.out.println(response.getBody().response().choices().get(0).message().content());
//		System.out.println(response.getBody().response().errorMsg().message());
	}

	@Test
	void chatCompletionEntityWithSystemMessage() {
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did?", Role.user);
		ChatCompletionMessage systemMessage = new ChatCompletionMessage("""
				You are an AI assistant that helps people find information.
				Your name is Bob.
				You should reply to the user's request with your name and also in the style of a pirate.
					""", Role.system);

		ResponseEntity<HunYuanApi.ChatCompletionResponse> response = this.hunyuanApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(systemMessage, userMessage), HunYuanApi.ChatModel.HUNYUAN_PRO.getValue(), 0.8, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.user);
		Flux<ChatCompletionChunk> response = this.hunyuanApi.chatCompletionStream(new ChatCompletionRequest(
				List.of(chatCompletionMessage), HunYuanApi.ChatModel.HUNYUAN_PRO.getValue(), 0.8, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

}
