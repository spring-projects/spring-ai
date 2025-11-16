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

package org.springframework.ai.huggingface;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.retry.RetryUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for request building in {@link HuggingfaceChatModel}.
 *
 * @author Myeongdeok Kang
 */
class HuggingfaceChatRequestTests {

	private final HuggingfaceChatModel chatModel = HuggingfaceChatModel.builder()
		.huggingfaceApi(HuggingfaceApi.builder().apiKey("test-key").build())
		.defaultOptions(HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.temperature(0.7)
			.maxTokens(100)
			.topP(0.9)
			.build())
		.toolCallingManager(ToolCallingManager.builder().build())
		.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
		.build();

	@Test
	void createRequestWithDefaultOptions() {
		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message content"));

		assertThat(prompt.getInstructions()).hasSize(1);
		assertThat(prompt.getOptions()).isNotNull();

		HuggingfaceChatOptions options = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(options.getModel()).isEqualTo("meta-llama/Llama-3.2-3B-Instruct");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getTopP()).isEqualTo(0.9);
	}

	@Test
	void createRequestWithPromptHuggingfaceOptions() {
		// Runtime options should override the default options.
		HuggingfaceChatOptions promptOptions = HuggingfaceChatOptions.builder()
			.temperature(0.8)
			.topP(0.5)
			.maxTokens(200)
			.build();
		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message content", promptOptions));

		assertThat(prompt.getInstructions()).hasSize(1);

		HuggingfaceChatOptions options = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(options.getModel()).isEqualTo("meta-llama/Llama-3.2-3B-Instruct");
		assertThat(options.getTemperature()).isEqualTo(0.8);
		assertThat(options.getMaxTokens()).isEqualTo(200); // overridden
		assertThat(options.getTopP()).isEqualTo(0.5); // overridden
	}

	@Test
	void createRequestWithPromptPortableChatOptions() {
		// Portable runtime options.
		ChatOptions portablePromptOptions = ChatOptions.builder().temperature(0.9).topP(0.6).build();
		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message content", portablePromptOptions));

		assertThat(prompt.getInstructions()).hasSize(1);

		HuggingfaceChatOptions options = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(options.getModel()).isEqualTo("meta-llama/Llama-3.2-3B-Instruct");
		assertThat(options.getTemperature()).isEqualTo(0.9);
		assertThat(options.getTopP()).isEqualTo(0.6);
		assertThat(options.getMaxTokens()).isEqualTo(100); // default value maintained
	}

	@Test
	void createRequestWithPromptOptionsModelOverride() {
		// Runtime options override model
		HuggingfaceChatOptions promptOptions = HuggingfaceChatOptions.builder()
			.model("mistralai/Mistral-7B-Instruct-v0.3")
			.build();
		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message content", promptOptions));

		HuggingfaceChatOptions options = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(options.getModel()).isEqualTo("mistralai/Mistral-7B-Instruct-v0.3");
	}

	@Test
	void createRequestWithDefaultOptionsModelOverride() {
		HuggingfaceChatModel chatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(HuggingfaceApi.builder().apiKey("test-key").build())
			.defaultOptions(HuggingfaceChatOptions.builder().model("google/gemma-2-2b-it").temperature(0.5).build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();

		var prompt1 = chatModel.buildChatRequest(new Prompt("Test message content"));

		HuggingfaceChatOptions options1 = (HuggingfaceChatOptions) prompt1.getOptions();
		assertThat(options1.getModel()).isEqualTo("google/gemma-2-2b-it");

		// Prompt options should override the default options.
		HuggingfaceChatOptions promptOptions = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.build();
		var prompt2 = chatModel.buildChatRequest(new Prompt("Test message content", promptOptions));

		HuggingfaceChatOptions options2 = (HuggingfaceChatOptions) prompt2.getOptions();
		assertThat(options2.getModel()).isEqualTo("meta-llama/Llama-3.2-3B-Instruct");
	}

	@Test
	void createRequestWithAllMessageTypes() {
		var prompt = this.chatModel.buildChatRequest(new Prompt(createMessagesWithAllMessageTypes()));

		assertThat(prompt.getInstructions()).hasSize(3);

		var systemMessage = prompt.getInstructions().get(0);
		assertThat(systemMessage).isInstanceOf(SystemMessage.class);
		assertThat(systemMessage.getText()).isEqualTo("Test system message");

		var userMessage = prompt.getInstructions().get(1);
		assertThat(userMessage).isInstanceOf(UserMessage.class);
		assertThat(userMessage.getText()).isEqualTo("Test user message");

		var assistantMessage = prompt.getInstructions().get(2);
		assertThat(assistantMessage).isInstanceOf(AssistantMessage.class);
		assertThat(assistantMessage.getText()).isEqualTo("Test assistant message");
	}

	@Test
	void createRequestWithMultipleUserMessages() {
		List<Message> messages = List.of(new UserMessage("First question"), new UserMessage("Second question"),
				new UserMessage("Third question"));

		var prompt = this.chatModel.buildChatRequest(new Prompt(messages));

		assertThat(prompt.getInstructions()).hasSize(3);
		assertThat(prompt.getInstructions().get(0).getText()).isEqualTo("First question");
		assertThat(prompt.getInstructions().get(1).getText()).isEqualTo("Second question");
		assertThat(prompt.getInstructions().get(2).getText()).isEqualTo("Third question");
	}

	@Test
	void createRequestPreservesMessageOrder() {
		List<Message> messages = List.of(new SystemMessage("System"), new UserMessage("User 1"),
				new AssistantMessage("Assistant 1"), new UserMessage("User 2"));

		var prompt = this.chatModel.buildChatRequest(new Prompt(messages));

		assertThat(prompt.getInstructions()).hasSize(4);
		assertThat(prompt.getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(prompt.getInstructions().get(1)).isInstanceOf(UserMessage.class);
		assertThat(prompt.getInstructions().get(2)).isInstanceOf(AssistantMessage.class);
		assertThat(prompt.getInstructions().get(3)).isInstanceOf(UserMessage.class);
	}

	@Test
	void createRequestWithMinimalOptions() {
		HuggingfaceChatModel minimalChatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(HuggingfaceApi.builder().apiKey("test-key").build())
			.defaultOptions(HuggingfaceChatOptions.builder().model("meta-llama/Llama-3.2-3B-Instruct").build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.build();

		var prompt = minimalChatModel.buildChatRequest(new Prompt("Test message"));

		HuggingfaceChatOptions options = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(options.getModel()).isEqualTo("meta-llama/Llama-3.2-3B-Instruct");
		// Other options should be null or default values
	}

	@Test
	void createRequestWithMaximumOptions() {
		HuggingfaceChatOptions maxOptions = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.temperature(0.8)
			.maxTokens(500)
			.topP(0.95)
			.frequencyPenalty(0.5)
			.presencePenalty(0.3)
			.build();

		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message", maxOptions));

		HuggingfaceChatOptions options = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(options.getModel()).isEqualTo("meta-llama/Llama-3.2-3B-Instruct");
		assertThat(options.getTemperature()).isEqualTo(0.8);
		assertThat(options.getMaxTokens()).isEqualTo(500);
		assertThat(options.getTopP()).isEqualTo(0.95);
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getPresencePenalty()).isEqualTo(0.3);
	}

	@Test
	void createRequestWithStopSequences() {
		List<String> stopSequences = Arrays.asList("STOP", "END", "DONE");
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.stopSequences(stopSequences)
			.build();

		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message", options));

		HuggingfaceChatOptions resultOptions = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(resultOptions.getStopSequences()).isEqualTo(stopSequences);
	}

	@Test
	void createRequestWithSeed() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.seed(42)
			.build();

		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message", options));

		HuggingfaceChatOptions resultOptions = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(resultOptions.getSeed()).isEqualTo(42);
	}

	@Test
	void createRequestWithResponseFormat() {
		Map<String, Object> responseFormat = new HashMap<>();
		responseFormat.put("type", "json_object");
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.responseFormat(responseFormat)
			.build();

		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message", options));

		HuggingfaceChatOptions resultOptions = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(resultOptions.getResponseFormat()).isEqualTo(responseFormat);
	}

	@Test
	void createRequestWithToolPrompt() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.toolPrompt("You have access to these tools:")
			.build();

		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message", options));

		HuggingfaceChatOptions resultOptions = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(resultOptions.getToolPrompt()).isEqualTo("You have access to these tools:");
	}

	@Test
	void createRequestWithLogprobs() {
		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.logprobs(true)
			.topLogprobs(3)
			.build();

		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message", options));

		HuggingfaceChatOptions resultOptions = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(resultOptions.getLogprobs()).isTrue();
		assertThat(resultOptions.getTopLogprobs()).isEqualTo(3);
	}

	@Test
	void createRequestWithAllNewParameters() {
		List<String> stopSequences = Arrays.asList("STOP");
		Map<String, Object> responseFormat = new HashMap<>();
		responseFormat.put("type", "json_object");

		HuggingfaceChatOptions options = HuggingfaceChatOptions.builder()
			.model("meta-llama/Llama-3.2-3B-Instruct")
			.temperature(0.7)
			.maxTokens(200)
			.stopSequences(stopSequences)
			.seed(12345)
			.responseFormat(responseFormat)
			.toolPrompt("Tools available:")
			.logprobs(true)
			.topLogprobs(5)
			.build();

		var prompt = this.chatModel.buildChatRequest(new Prompt("Test message", options));

		HuggingfaceChatOptions resultOptions = (HuggingfaceChatOptions) prompt.getOptions();
		assertThat(resultOptions.getModel()).isEqualTo("meta-llama/Llama-3.2-3B-Instruct");
		assertThat(resultOptions.getTemperature()).isEqualTo(0.7);
		assertThat(resultOptions.getMaxTokens()).isEqualTo(200);
		assertThat(resultOptions.getStopSequences()).isEqualTo(stopSequences);
		assertThat(resultOptions.getSeed()).isEqualTo(12345);
		assertThat(resultOptions.getResponseFormat()).isEqualTo(responseFormat);
		assertThat(resultOptions.getToolPrompt()).isEqualTo("Tools available:");
		assertThat(resultOptions.getLogprobs()).isTrue();
		assertThat(resultOptions.getTopLogprobs()).isEqualTo(5);
	}

	private static List<Message> createMessagesWithAllMessageTypes() {
		var systemMessage = new SystemMessage("Test system message");
		var userMessage = new UserMessage("Test user message");
		var assistantMessage = new AssistantMessage("Test assistant message");

		return List.of(systemMessage, userMessage, assistantMessage);
	}

}
