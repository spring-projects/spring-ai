/*
 * Copyright 2023-2025 the original author or authors.
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
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Ricken Bazolo
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @author Nicolas Krier
 * @since 0.8.1
 */
class MistralAiChatCompletionRequestTest {

	private static final String BASE_URL = "https://faked.url";

	private static final String API_KEY = "FAKED_API_KEY";

	private static final String TEXT_CONTENT = "Hello world!";

	private static final String IMAGE_URL = "https://example.com/image.png";

	private static final Media IMAGE_MEDIA = new Media(Media.Format.IMAGE_PNG, URI.create(IMAGE_URL));

	private final MistralAiChatModel chatModel = MistralAiChatModel.builder()
		.mistralAiApi(new MistralAiApi(BASE_URL, API_KEY))
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
	void whenToolRuntimeOptionsThenMergeWithDefaults() {
		MistralAiChatOptions defaultOptions = MistralAiChatOptions.builder()
			.model("DEFAULT_MODEL")
			.internalToolExecutionEnabled(true)
			.toolCallbacks(new TestToolCallback("tool1"), new TestToolCallback("tool2"))
			.toolNames("tool1", "tool2")
			.toolContext(Map.of("key1", "value1", "key2", "valueA"))
			.build();

		MistralAiChatModel anotherChatModel = MistralAiChatModel.builder()
			.mistralAiApi(new MistralAiApi(BASE_URL, API_KEY))
			.defaultOptions(defaultOptions)
			.build();

		MistralAiChatOptions runtimeOptions = MistralAiChatOptions.builder()
			.internalToolExecutionEnabled(false)
			.toolCallbacks(new TestToolCallback("tool3"), new TestToolCallback("tool4"))
			.toolNames("tool3")
			.toolContext(Map.of("key2", "valueB"))
			.build();
		Prompt prompt = anotherChatModel.buildRequestPrompt(new Prompt("Test message content", runtimeOptions));

		assertThat(((ToolCallingChatOptions) prompt.getOptions())).isNotNull();
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getInternalToolExecutionEnabled()).isFalse();
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks()).hasSize(2);
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolCallbacks()
			.stream()
			.map(toolCallback -> toolCallback.getToolDefinition().name())).containsExactlyInAnyOrder("tool3", "tool4");
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolNames()).containsExactlyInAnyOrder("tool3");
		assertThat(((ToolCallingChatOptions) prompt.getOptions()).getToolContext()).containsEntry("key1", "value1")
			.containsEntry("key2", "valueB");
	}

	@Test
	void createMessagesWithUserMessage() {
		var userMessage = new UserMessage(TEXT_CONTENT);
		userMessage.getMedia().add(IMAGE_MEDIA);
		var chatCompletionMessages = this.chatModel.createMessages(userMessage).toList();
		verifyUserChatCompletionMessages(chatCompletionMessages);
	}

	@Test
	void createMessagesWithAnotherUserMessage() {
		var anotherUserMessage = new AnotherUserMessage(TEXT_CONTENT, List.of(IMAGE_MEDIA));
		var chatCompletionMessages = this.chatModel.createMessages(anotherUserMessage).toList();
		verifyUserChatCompletionMessages(chatCompletionMessages);
	}

	@Test
	void createMessagesWithSimpleUserMessage() {
		var simpleUserMessage = new SimpleMessage(MessageType.USER, TEXT_CONTENT);
		var chatCompletionMessages = this.chatModel.createMessages(simpleUserMessage).toList();
		assertThat(chatCompletionMessages).hasSize(1);
		var chatCompletionMessage = chatCompletionMessages.get(0);
		assertThat(chatCompletionMessage.role()).isEqualTo(ChatCompletionMessage.Role.USER);
		assertThat(chatCompletionMessage.content()).isEqualTo(TEXT_CONTENT);
	}

	@Test
	void createMessagesWithSystemMessage() {
		var systemMessage = new SystemMessage(TEXT_CONTENT);
		var chatCompletionMessages = this.chatModel.createMessages(systemMessage).toList();
		verifySystemChatCompletionMessages(chatCompletionMessages);
	}

	@Test
	void createMessagesWithSimpleSystemMessage() {
		var simpleSystemMessage = new SimpleMessage(MessageType.SYSTEM, TEXT_CONTENT);
		var chatCompletionMessages = this.chatModel.createMessages(simpleSystemMessage).toList();
		verifySystemChatCompletionMessages(chatCompletionMessages);
	}

	@Test
	void createMessagesWithAssistantMessage() {
		var toolCall1 = createToolCall(1);
		var toolCall2 = createToolCall(2);
		var toolCall3 = createToolCall(3);
		var assistantMessage = new AssistantMessage(TEXT_CONTENT, Map.of(), List.of(toolCall1, toolCall2, toolCall3));
		var chatCompletionMessages = this.chatModel.createMessages(assistantMessage).toList();
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
	void createMessagesWithSimpleAssistantMessage() {
		var simpleAssistantMessage = new SimpleMessage(MessageType.ASSISTANT, TEXT_CONTENT);
		assertThatThrownBy(() -> this.chatModel.createMessages(simpleAssistantMessage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unexpected assistant message class: " + SimpleMessage.class.getName());
	}

	@Test
	void createMessagesWithToolResponseMessage() {
		var toolResponse1 = createToolResponse(1);
		var toolResponse2 = createToolResponse(2);
		var toolResponse3 = createToolResponse(3);
		var toolResponseMessage = new ToolResponseMessage(List.of(toolResponse1, toolResponse2, toolResponse3));
		var chatCompletionMessages = this.chatModel.createMessages(toolResponseMessage).toList();
		assertThat(chatCompletionMessages).hasSize(3);
		verifyToolChatCompletionMessage(chatCompletionMessages.get(0), toolResponse1);
		verifyToolChatCompletionMessage(chatCompletionMessages.get(1), toolResponse2);
		verifyToolChatCompletionMessage(chatCompletionMessages.get(2), toolResponse3);
	}

	@Test
	void createMessagesWithInvalidToolResponseMessage() {
		var toolResponse = new ToolResponseMessage.ToolResponse(null, null, null);
		var toolResponseMessage = new ToolResponseMessage(List.of(toolResponse));
		assertThatThrownBy(() -> this.chatModel.createMessages(toolResponseMessage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("ToolResponseMessage must have an id");
	}

	@Test
	void createMessagesWithSimpleToolMessage() {
		var simpleToolMessage = new SimpleMessage(MessageType.TOOL, TEXT_CONTENT);
		assertThatThrownBy(() -> this.chatModel.createMessages(simpleToolMessage))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Unexpected tool message class: " + SimpleMessage.class.getName());
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
		var rawContent = chatCompletionMessage.rawContent();
		assertThat(rawContent).isNotNull();
		var mediaContents = (List<ChatCompletionMessage.MediaContent>) rawContent;
		assertThat(mediaContents).hasSize(2);
		var textMediaContent = mediaContents.get(0);
		assertThat(textMediaContent).isNotNull();
		assertThat(textMediaContent.type()).isEqualTo("text");
		assertThat(textMediaContent.text()).isEqualTo(TEXT_CONTENT);
		assertThat(textMediaContent.imageUrl()).isNull();
		var imageUrlMediaContent = mediaContents.get(1);
		assertThat(imageUrlMediaContent).isNotNull();
		assertThat(imageUrlMediaContent.type()).isEqualTo("image_url");
		assertThat(imageUrlMediaContent.text()).isNull();
		var imageUrl = imageUrlMediaContent.imageUrl();
		assertThat(imageUrl).isNotNull();
		assertThat(imageUrl.url()).isEqualTo(IMAGE_URL);
		assertThat(imageUrl.detail()).isNull();
	}

	static class SimpleMessage extends AbstractMessage {

		SimpleMessage(MessageType messageType, String textContent) {
			super(messageType, textContent, Map.of());
		}

	}

	static class AnotherUserMessage extends AbstractMessage implements MediaContent {

		private final List<Media> media;

		AnotherUserMessage(String textContent, List<Media> media) {
			super(MessageType.USER, textContent, Map.of());
			this.media = List.copyOf(media);
		}

		@Override
		public List<Media> getMedia() {
			return this.media;
		}

	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

}
