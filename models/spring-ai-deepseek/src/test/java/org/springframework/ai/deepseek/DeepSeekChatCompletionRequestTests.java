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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
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

	// gh-5898: when an assistant turn carrying tool_calls is replayed back to the API
	// with thinking enabled, reasoning_content must be present on the assistant
	// message or the API returns an "invalid_request_error".
	@Test
	public void createRequestPreservesReasoningContentOnAssistantToolCallMessage() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		var assistantToolCall = new AssistantMessage.ToolCall("call_1", "function", "getWeather",
				"{\"city\":\"Beijing\"}");
		var assistant = new DeepSeekAssistantMessage.Builder().content("")
			.reasoningContent("I should check the weather first.")
			.toolCalls(List.of(assistantToolCall))
			.build();

		var prompt = client.buildRequestPrompt(new Prompt(List.of(new UserMessage("hi"), assistant),
				DeepSeekChatOptions.builder().model("deepseek-reasoner").build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(2);
		var assistantParam = request.messages().get(1);
		assertThat(assistantParam.role()).isEqualTo(DeepSeekApi.ChatCompletionMessage.Role.ASSISTANT);
		assertThat(assistantParam.reasoningContent()).isEqualTo("I should check the weather first.");
		assertThat(assistantParam.toolCalls()).hasSize(1);
	}

	@Test
	public void createRequestLeavesReasoningContentNullForPlainAssistantMessage() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		var prompt = client.buildRequestPrompt(new Prompt(List.of(new UserMessage("hi"), new AssistantMessage("ok")),
				DeepSeekChatOptions.builder().model("deepseek-chat").build()));

		var request = client.createRequest(prompt, false);

		assertThat(request.messages().get(1).reasoningContent()).isNull();
	}

	// gh-5898: in the streaming path, pre-tool-call reasoning_content and content
	// chunks are emitted individually before tool_call chunks. The chunk that
	// triggers tool execution carries only tool_calls, so the assistant message
	// reaching the conversation history would be missing the accumulated context.
	// enrichResponseWithAccumulatedFields stitches them back together.
	@Test
	public void enrichResponseWithAccumulatedFieldsMergesContentAndReasoningOntoToolCallMessage() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		var toolCall = new AssistantMessage.ToolCall("call_1", "function", "getWeather", "{\"city\":\"Hangzhou\"}");
		// Simulate the chunk that triggers tool execution: tool_calls present,
		// content/reasoningContent empty/null because earlier chunks were already
		// emitted as separate one-element windows.
		var toolCallOnlyMessage = new DeepSeekAssistantMessage.Builder().content("")
			.toolCalls(List.of(toolCall))
			.build();
		var response = ChatResponse.builder()
			.generations(List.of(new Generation(toolCallOnlyMessage, ChatGenerationMetadata.builder().build())))
			.build();

		var enriched = client.enrichResponseWithAccumulatedFields(response,
				"Sure, let me check the weather in Hangzhou.",
				"The user wants the weather and whether to bring an umbrella; I should call getWeather.");

		var enrichedMessage = (DeepSeekAssistantMessage) enriched.getResult().getOutput();
		assertThat(enrichedMessage.getText()).isEqualTo("Sure, let me check the weather in Hangzhou.");
		assertThat(enrichedMessage.getReasoningContent())
			.isEqualTo("The user wants the weather and whether to bring an umbrella; I should call getWeather.");
		assertThat(enrichedMessage.getToolCalls()).hasSize(1);
		assertThat(enrichedMessage.getToolCalls().get(0).id()).isEqualTo("call_1");
		// reasoning_content is mirrored into metadata so it survives Prompt#mutate()
		assertThat(enrichedMessage.getMetadata()).containsEntry(DeepSeekAssistantMessage.REASONING_CONTENT_METADATA_KEY,
				"The user wants the weather and whether to bring an umbrella; I should call getWeather.");
	}

	@Test
	public void enrichResponseWithAccumulatedFieldsIsNoOpWhenAccumulatorsEmpty() {
		var client = DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("TEST").build()).build();

		var toolCall = new AssistantMessage.ToolCall("call_1", "function", "noop", "{}");
		var existing = new DeepSeekAssistantMessage.Builder().content("").toolCalls(List.of(toolCall)).build();
		var response = ChatResponse.builder()
			.generations(List.of(new Generation(existing, ChatGenerationMetadata.builder().build())))
			.build();

		var result = client.enrichResponseWithAccumulatedFields(response, "", "");

		assertThat(result).isSameAs(response);
	}

}
