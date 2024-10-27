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

package org.springframework.ai.qianfan.api;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.stringtemplate.v4.ST;
import reactor.core.publisher.Flux;

import org.springframework.ai.ResourceUtils;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletion;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionChunk;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionMessage;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionMessage.Role;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionRequest;
import org.springframework.ai.qianfan.api.QianFanApi.EmbeddingList;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
@EnabledIfEnvironmentVariables(value = { @EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "QIANFAN_SECRET_KEY", matches = ".+") })
public class QianFanApiIT {

	QianFanApi qianFanApi = new QianFanApi(System.getenv("QIANFAN_API_KEY"), System.getenv("QIANFAN_SECRET_KEY"));

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ResponseEntity<ChatCompletion> response = this.qianFanApi.chatCompletionEntity(new ChatCompletionRequest(
				List.of(chatCompletionMessage), buildSystemMessage(), "ernie_speed", 0.7, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		Flux<ChatCompletionChunk> response = this.qianFanApi.chatCompletionStream(new ChatCompletionRequest(
				List.of(chatCompletionMessage), buildSystemMessage(), "ernie_speed", 0.7, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

	@Test
	void embeddings() {
		ResponseEntity<EmbeddingList> response = this.qianFanApi
			.embeddings(new QianFanApi.EmbeddingRequest("Hello world"));

		assertThat(response).isNotNull();
		assertThat(Objects.requireNonNull(response.getBody()).data()).hasSize(1);
		assertThat(response.getBody().data().get(0).embedding()).hasSize(1024);
	}

	private String buildSystemMessage() {
		String systemMessageTemplate = ResourceUtils.getText("classpath:/prompts/system-message.st");
		ST st = new ST(systemMessageTemplate, '{', '}');
		return st.add("name", "QianFan").add("voice", "pirate").render();
	}

}
