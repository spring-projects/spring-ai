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

package org.springframework.ai.anthropicsdk;

import java.util.List;
import java.util.Optional;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ToolUseBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AnthropicSdkChatModel}. Tests request building and response
 * parsing with mocked SDK client.
 *
 * @author Soby Chacko
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AnthropicSdkChatModelTests {

	@Mock
	private AnthropicClient anthropicClient;

	@Mock
	private AnthropicClientAsync anthropicClientAsync;

	@Mock
	private MessageService messageService;

	private AnthropicSdkChatModel chatModel;

	@BeforeEach
	void setUp() {
		given(this.anthropicClient.messages()).willReturn(this.messageService);

		this.chatModel = AnthropicSdkChatModel.builder()
			.anthropicClient(this.anthropicClient)
			.anthropicClientAsync(this.anthropicClientAsync)
			.options(AnthropicSdkChatOptions.builder()
				.model(Model.CLAUDE_SONNET_4_20250514)
				.maxTokens(1024)
				.temperature(0.7)
				.build())
			.build();
	}

	@Test
	void callWithSimpleUserMessage() {
		Message mockResponse = createMockMessage("Hello! How can I help you today?", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt("Hello"));

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello! How can I help you today?");

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.model().asString()).isEqualTo("claude-sonnet-4-20250514");
		assertThat(request.maxTokens()).isEqualTo(1024);
	}

	@Test
	void callWithSystemAndUserMessages() {
		Message mockResponse = createMockMessage("I am a helpful assistant.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		SystemMessage systemMessage = new SystemMessage("You are a helpful assistant.");
		UserMessage userMessage = new UserMessage("Who are you?");

		ChatResponse response = this.chatModel.call(new Prompt(List.of(systemMessage, userMessage)));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("I am a helpful assistant.");

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.system()).isPresent();
	}

	@Test
	void callWithRuntimeOptionsOverride() {
		Message mockResponse = createMockMessage("Response with override", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		AnthropicSdkChatOptions runtimeOptions = AnthropicSdkChatOptions.builder()
			.model("claude-3-opus-20240229")
			.maxTokens(2048)
			.temperature(0.3)
			.build();

		ChatResponse response = this.chatModel.call(new Prompt("Test", runtimeOptions));

		assertThat(response).isNotNull();

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.model().asString()).isEqualTo("claude-3-opus-20240229");
		assertThat(request.maxTokens()).isEqualTo(2048);
	}

	@Test
	void responseContainsUsageMetadata() {
		Message mockResponse = createMockMessage("Test response", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt("Test"));

		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata().getUsage()).isNotNull();
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(10);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(20);
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(30);
	}

	@Test
	void responseContainsFinishReason() {
		Message mockResponse = createMockMessage("Stopped at max tokens", StopReason.MAX_TOKENS);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		ChatResponse response = this.chatModel.call(new Prompt("Test"));

		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("max_tokens");
	}

	@Test
	void responseWithToolUseBlock() {
		Message mockResponse = createMockMessageWithToolUse("toolu_123", "getCurrentWeather",
				JsonValue.from(java.util.Map.of("location", "San Francisco")), StopReason.TOOL_USE);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		// Disable internal tool execution to verify tool call parsing only
		AnthropicSdkChatOptions options = AnthropicSdkChatOptions.builder().internalToolExecutionEnabled(false).build();

		ChatResponse response = this.chatModel.call(new Prompt("What's the weather?", options));

		assertThat(response.getResult()).isNotNull();
		AssistantMessage output = response.getResult().getOutput();
		assertThat(output.getToolCalls()).isNotEmpty();
		assertThat(output.getToolCalls()).hasSize(1);

		var toolCall = output.getToolCalls().get(0);
		assertThat(toolCall.id()).isEqualTo("toolu_123");
		assertThat(toolCall.name()).isEqualTo("getCurrentWeather");
		assertThat(toolCall.arguments()).contains("San Francisco");
	}

	@Test
	void getDefaultOptionsReturnsCopy() {
		var defaultOptions1 = this.chatModel.getDefaultOptions();
		var defaultOptions2 = this.chatModel.getDefaultOptions();

		assertThat(defaultOptions1).isNotSameAs(defaultOptions2);
		assertThat(defaultOptions1.getModel()).isEqualTo(defaultOptions2.getModel());
	}

	@Test
	void buildRequestPromptMergesOptions() {
		AnthropicSdkChatModel model = AnthropicSdkChatModel.builder()
			.anthropicClient(this.anthropicClient)
			.anthropicClientAsync(this.anthropicClientAsync)
			.options(AnthropicSdkChatOptions.builder().model("default-model").maxTokens(1000).temperature(0.5).build())
			.build();

		AnthropicSdkChatOptions runtimeOptions = AnthropicSdkChatOptions.builder().temperature(0.9).build();

		Prompt originalPrompt = new Prompt("Test", runtimeOptions);
		Prompt requestPrompt = model.buildRequestPrompt(originalPrompt);

		AnthropicSdkChatOptions mergedOptions = (AnthropicSdkChatOptions) requestPrompt.getOptions();
		assertThat(mergedOptions.getModel()).isEqualTo("default-model");
		assertThat(mergedOptions.getMaxTokens()).isEqualTo(1000);
		assertThat(mergedOptions.getTemperature()).isEqualTo(0.9);
	}

	@Test
	void multiTurnConversation() {
		Message mockResponse = createMockMessage("Paris is the capital of France.", StopReason.END_TURN);
		given(this.messageService.create(any(MessageCreateParams.class))).willReturn(mockResponse);

		UserMessage user1 = new UserMessage("What is the capital of France?");
		AssistantMessage assistant1 = new AssistantMessage("The capital of France is Paris.");
		UserMessage user2 = new UserMessage("What is its population?");

		ChatResponse response = this.chatModel.call(new Prompt(List.of(user1, assistant1, user2)));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("Paris is the capital of France.");

		ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
		verify(this.messageService).create(captor.capture());

		MessageCreateParams request = captor.getValue();
		assertThat(request.messages()).hasSize(3);
	}

	private Message createMockMessage(String text, StopReason stopReason) {
		TextBlock textBlock = mock(TextBlock.class);
		given(textBlock.text()).willReturn(text);

		ContentBlock contentBlock = mock(ContentBlock.class);
		given(contentBlock.isText()).willReturn(true);
		given(contentBlock.isToolUse()).willReturn(false);
		given(contentBlock.asText()).willReturn(textBlock);

		Usage usage = mock(Usage.class);
		given(usage.inputTokens()).willReturn(10L);
		given(usage.outputTokens()).willReturn(20L);

		Message message = mock(Message.class);
		given(message.id()).willReturn("msg_123");
		given(message.model()).willReturn(Model.CLAUDE_SONNET_4_20250514);
		given(message.content()).willReturn(List.of(contentBlock));
		given(message.stopReason()).willReturn(Optional.of(stopReason));
		given(message.usage()).willReturn(usage);

		return message;
	}

	private Message createMockMessageWithToolUse(String toolId, String toolName, JsonValue input,
			StopReason stopReason) {
		ToolUseBlock toolUseBlock = mock(ToolUseBlock.class);
		given(toolUseBlock.id()).willReturn(toolId);
		given(toolUseBlock.name()).willReturn(toolName);
		given(toolUseBlock._input()).willReturn(input);

		ContentBlock contentBlock = mock(ContentBlock.class);
		given(contentBlock.isText()).willReturn(false);
		given(contentBlock.isToolUse()).willReturn(true);
		given(contentBlock.asToolUse()).willReturn(toolUseBlock);

		Usage usage = mock(Usage.class);
		given(usage.inputTokens()).willReturn(15L);
		given(usage.outputTokens()).willReturn(25L);

		Message message = mock(Message.class);
		given(message.id()).willReturn("msg_456");
		given(message.model()).willReturn(Model.CLAUDE_SONNET_4_20250514);
		given(message.content()).willReturn(List.of(contentBlock));
		given(message.stopReason()).willReturn(Optional.of(stopReason));
		given(message.usage()).willReturn(usage);

		return message;
	}

}
