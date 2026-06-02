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

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.api.DeepSeekApi;

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

	// gh-5038: DeepSeek thinking mode requires the previous assistant message's
	// reasoning_content to be passed back, otherwise the next request fails with
	// "The reasoning_content in the thinking mode must be passed back to the API." (400).
	// inbound (buildGeneration) already captures reasoningContent into
	// DeepSeekAssistantMessage; this test pins outbound roundtrip in createRequest.
	@Test
	public void createRequestPreservesReasoningContentFromDeepSeekAssistantMessage() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		var assistantMessage = DeepSeekAssistantMessage.builder()
			.content("final answer")
			.reasoningContent("step-by-step thinking trace")
			.build();

		var prompt = client.buildRequestPrompt(
				new Prompt(List.of(new UserMessage("question"), assistantMessage, new UserMessage("follow-up")),
						DeepSeekChatOptions.builder().model("deepseek-reasoner").build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(3);
		var serializedAssistant = request.messages().get(1);
		assertThat(serializedAssistant.role()).isEqualTo(DeepSeekApi.ChatCompletionMessage.Role.ASSISTANT);
		assertThat(serializedAssistant.content()).isEqualTo("final answer");
		assertThat(serializedAssistant.reasoningContent()).isEqualTo("step-by-step thinking trace");
	}

	// Plain AssistantMessage (not a DeepSeekAssistantMessage) has no reasoningContent
	// to forward; verify the outbound field stays null and we don't NPE.
	@Test
	public void createRequestKeepsReasoningContentNullForPlainAssistantMessage() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		var assistantMessage = new org.springframework.ai.chat.messages.AssistantMessage("final answer");

		var prompt = client.buildRequestPrompt(
				new Prompt(List.of(new UserMessage("question"), assistantMessage, new UserMessage("follow-up")),
						DeepSeekChatOptions.builder().model("deepseek-chat").build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(3);
		var serializedAssistant = request.messages().get(1);
		assertThat(serializedAssistant.role()).isEqualTo(DeepSeekApi.ChatCompletionMessage.Role.ASSISTANT);
		assertThat(serializedAssistant.content()).isEqualTo("final answer");
		assertThat(serializedAssistant.reasoningContent()).isNull();
	}

}
