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

package org.springframework.ai.mistralai;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ricken Bazolo
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @author Nicolas Krier
 * @since 0.8.1
 */
class MistralAiChatCompletionRequestTests {

	private static final String BASE_URL = "https://faked.url";

	private static final String API_KEY = "FAKED_API_KEY";

	private static final String TEXT_CONTENT = "Hello world!";

	private static final String IMAGE_URL = "https://example.com/image.png";

	private static final Media IMAGE_MEDIA = new Media(Media.Format.IMAGE_PNG, URI.create(IMAGE_URL));

	private final MistralAiChatModel chatModel = MistralAiChatModel.builder()
		.mistralAiApi(MistralAiApi.builder().baseUrl(BASE_URL).apiKey(API_KEY).build())
		.build();

	@Test
	void chatCompletionDefaultRequestTest() {
		var prompt = this.chatModel.buildRequestPrompt(new Prompt("test content"));
		var request = this.chatModel.createRequest(prompt, false);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.topP()).isEqualTo(1);
		assertThat(request.temperature()).isEqualTo(0.7);
		assertThat(request.safePrompt()).isFalse();
		assertThat(request.maxTokens()).isNull();
		assertThat(request.stream()).isFalse();
	}

	@Test
	void chatCompletionRequestWithOptionsTest() {
		var options = MistralAiChatOptions.builder().temperature(0.5).topP(0.8).build();
		var prompt = this.chatModel.buildRequestPrompt(new Prompt("test content", options));
		var request = this.chatModel.createRequest(prompt, true);

		assertThat(request.messages()).hasSize(1);
		assertThat(request.topP()).isEqualTo(0.8);
		assertThat(request.temperature()).isEqualTo(0.5);
		assertThat(request.stream()).isTrue();
	}

	@Test
	void createChatCompletionMessagesWithUserMessage() {
		var userMessage = new UserMessage(TEXT_CONTENT);
		userMessage.getMedia().add(IMAGE_MEDIA);
		var prompt = createPrompt(userMessage);
		var chatCompletionRequest = this.chatModel.createRequest(prompt, false);
		verifyUserChatCompletionMessages(chatCompletionRequest.messages());
	}

	@Test
	void createChatCompletionMessagesWithSystemMessage() {
		var systemMessage = new SystemMessage(TEXT_CONTENT);
		var prompt = createPrompt(systemMessage);
		var chatCompletionRequest = this.chatModel.createRequest(prompt, false);
		verifySystemChatCompletionMessages(chatCompletionRequest.messages());
	}

	@Test
	void createChatCompletionMessagesWithAssistantMessage() {
		var toolCall1 = createToolCall(1);
		var toolCall2 = createToolCall(2);
		var toolCall3 = createToolCall(3);
		// @formatter:off
		var assistantMessage = AssistantMessage.builder()
				.content(TEXT_CONTENT)
				.toolCalls(List.of(toolCall1, toolCall2, toolCall3))
				.build();
		// @formatter:on
		var prompt = createPrompt(assistantMessage);
		var chatCompletionRequest = this.chatModel.createRequest(prompt, false);
		var chatCompletionMessages = chatCompletionRequest.messages();
		assertThat(chatCompletionMessages).hasSize(1);
		var chatCompletionMessage = chatCompletionMessages.get(0);
		assertThat(chatCompletionMessage.role()).isEqualTo(ChatCompletionMessage.Role.ASSISTANT);
		assertThat(chatCompletionMessage.content()).isEqualTo(TEXT_CONTENT);
		var toolCalls = chatCompletionMessage.toolCalls();
		assertThat(toolCalls).hasSize(3);
		verifyToolCall(toolCalls.get(0), toolCall1);
		verifyToolCall(toolCalls.get(1), toolCall2);
		verifyToolCall(toolCalls.get(2), toolCall3);
	}

	@Test
	void createChatCompletionMessagesWithToolResponseMessage() {
		var toolResponse1 = createToolResponse(1);
		var toolResponse2 = createToolResponse(2);
		var toolResponse3 = createToolResponse(3);
		var toolResponseMessage = ToolResponseMessage.builder()
			.responses(List.of(toolResponse1, toolResponse2, toolResponse3))
			.build();
		var prompt = createPrompt(toolResponseMessage);
		var chatCompletionRequest = this.chatModel.createRequest(prompt, false);
		var chatCompletionMessages = chatCompletionRequest.messages();
		assertThat(chatCompletionMessages).hasSize(3);
		verifyToolChatCompletionMessage(chatCompletionMessages.get(0), toolResponse1);
		verifyToolChatCompletionMessage(chatCompletionMessages.get(1), toolResponse2);
		verifyToolChatCompletionMessage(chatCompletionMessages.get(2), toolResponse3);
	}

	private Prompt createPrompt(Message message) {
		var chatOptions = MistralAiChatOptions.builder().temperature(0.7d).build();
		var prompt = new Prompt(message, chatOptions);

		return this.chatModel.buildRequestPrompt(prompt);
	}

	private static void verifyToolChatCompletionMessage(ChatCompletionMessage chatCompletionMessage,
			ToolResponseMessage.ToolResponse toolResponse) {
		assertThat(chatCompletionMessage.role()).isEqualTo(ChatCompletionMessage.Role.TOOL);
		assertThat(chatCompletionMessage.content()).isEqualTo(toolResponse.responseData());
		assertThat(chatCompletionMessage.name()).isEqualTo(toolResponse.name());
		assertThat(chatCompletionMessage.toolCalls()).isNull();
		assertThat(chatCompletionMessage.toolCallId()).isEqualTo(toolResponse.id());
	}

	private static ToolResponseMessage.ToolResponse createToolResponse(int number) {
		return new ToolResponseMessage.ToolResponse("id" + number, "name" + number, "responseData" + number);
	}

	private static void verifyToolCall(ChatCompletionMessage.ToolCall mistralToolCall,
			AssistantMessage.ToolCall toolCall) {
		assertThat(mistralToolCall.id()).isEqualTo(toolCall.id());
		assertThat(mistralToolCall.type()).isEqualTo(toolCall.type());
		var function = mistralToolCall.function();
		assertThat(function).isNotNull();
		assertThat(function.name()).isEqualTo(toolCall.name());
		assertThat(function.arguments()).isEqualTo(toolCall.arguments());
	}

	private static AssistantMessage.ToolCall createToolCall(int number) {
		return new AssistantMessage.ToolCall("id" + number, "type" + number, "name" + number, "arguments " + number);
	}

	private static void verifySystemChatCompletionMessages(List<ChatCompletionMessage> chatCompletionMessages) {
		assertThat(chatCompletionMessages).hasSize(1);
		var chatCompletionMessage = chatCompletionMessages.get(0);
		assertThat(chatCompletionMessage.role()).isEqualTo(ChatCompletionMessage.Role.SYSTEM);
		assertThat(chatCompletionMessage.content()).isEqualTo(TEXT_CONTENT);
	}

	private static void verifyUserChatCompletionMessages(List<ChatCompletionMessage> chatCompletionMessages) {
		assertThat(chatCompletionMessages).hasSize(1);
		var chatCompletionMessage = chatCompletionMessages.get(0);
		assertThat(chatCompletionMessage.role()).isEqualTo(ChatCompletionMessage.Role.USER);
		var contentChunks = (List<MistralAiApi.ChatCompletionMessage.ContentChunk>) chatCompletionMessage.content();
		assertThat(contentChunks).hasSize(2);
		var textChunk = (MistralAiApi.ChatCompletionMessage.TextChunk) contentChunks.get(0);
		assertThat(textChunk).isNotNull();
		assertThat(textChunk.text()).isEqualTo(TEXT_CONTENT);
		var imageUrlChunk = (MistralAiApi.ChatCompletionMessage.ImageUrlChunk) contentChunks.get(1);
		assertThat(imageUrlChunk).isNotNull();
		var imageUrl = imageUrlChunk.imageUrl();
		assertThat(imageUrl.url()).isEqualTo(IMAGE_URL);
		assertThat(imageUrl.detail()).isNull();
	}

}
