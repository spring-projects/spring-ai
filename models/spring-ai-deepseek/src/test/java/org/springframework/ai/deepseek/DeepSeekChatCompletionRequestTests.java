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

package org.springframework.ai.deepseek;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 */
public class DeepSeekChatCompletionRequestTests {

	@Test
	public void createRequestWithChatOptions() {

		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		var prompt = client.buildRequestPrompt(new Prompt("Test message content",
				DeepSeekChatOptions.builder().model("DEFAULT_MODEL").temperature(66.6).build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isFalse();

		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.temperature()).isEqualTo(66.6D);

		request = client.createRequest(new Prompt("Test message content",
				DeepSeekChatOptions.builder().model("PROMPT_MODEL").temperature(99.9D).build()), true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.stream()).isTrue();

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
		assertThat(request.temperature()).isEqualTo(99.9D);
	}

	@Test
	public void createRequestWithReasoningContent() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		DeepSeekAssistantMessage assistantMessage = DeepSeekAssistantMessage.builder()
			.content("The answer is 42")
			.reasoningContent("Let me think about this step by step...")
			.build();

		var prompt = client.buildRequestPrompt(new Prompt(List.of(assistantMessage),
				DeepSeekChatOptions.builder().model("deepseek-reasoner").build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		ChatCompletionMessage message = request.messages().get(0);
		assertThat(message.role()).isEqualTo(ChatCompletionMessage.Role.ASSISTANT);
		assertThat(message.content()).isEqualTo("The answer is 42");
		assertThat(message.reasoningContent()).isEqualTo("Let me think about this step by step...");
	}

	@Test
	public void createRequestWithNullReasoningContent() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		DeepSeekAssistantMessage assistantMessage = DeepSeekAssistantMessage.builder()
			.content("The answer is 42")
			.build();

		var prompt = client.buildRequestPrompt(new Prompt(List.of(assistantMessage),
				DeepSeekChatOptions.builder().model("deepseek-reasoner").build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		ChatCompletionMessage message = request.messages().get(0);
		assertThat(message.role()).isEqualTo(ChatCompletionMessage.Role.ASSISTANT);
		assertThat(message.content()).isEqualTo("The answer is 42");
		assertThat(message.reasoningContent()).isNull();
	}

	@Test
	public void createRequestWithRegularAssistantMessage() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		AssistantMessage assistantMessage = new AssistantMessage("The answer is 42");

		var prompt = client.buildRequestPrompt(new Prompt(List.of(assistantMessage),
				DeepSeekChatOptions.builder().model("deepseek-reasoner").build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		ChatCompletionMessage message = request.messages().get(0);
		assertThat(message.role()).isEqualTo(ChatCompletionMessage.Role.ASSISTANT);
		assertThat(message.content()).isEqualTo("The answer is 42");
		assertThat(message.reasoningContent()).isNull();
	}

	@Test
	public void createRequestWithReasoningContentAndPrefix() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		DeepSeekAssistantMessage assistantMessage = DeepSeekAssistantMessage.builder()
			.content("")
			.reasoningContent("Thinking in progress...")
			.prefix(true)
			.build();

		var prompt = client.buildRequestPrompt(new Prompt(List.of(assistantMessage),
				DeepSeekChatOptions.builder().model("deepseek-reasoner").build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		ChatCompletionMessage message = request.messages().get(0);
		assertThat(message.role()).isEqualTo(ChatCompletionMessage.Role.ASSISTANT);
		assertThat(message.prefix()).isTrue();
		assertThat(message.reasoningContent()).isEqualTo("Thinking in progress...");
	}

}
