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

package org.springframework.ai.minimax.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.minimax.MiniMaxChatOptions;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.minimax.api.MockWeatherService;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Geng Rong
 * @author Ilayaperumal Gopinathan
 */
@EnabledIfEnvironmentVariable(named = "MINIMAX_API_KEY", matches = ".+")
public class MiniMaxChatOptionsTests {

	private static final Logger logger = LoggerFactory.getLogger(MiniMaxChatOptionsTests.class);

	private final MiniMaxChatModel chatModel = new MiniMaxChatModel(new MiniMaxApi(System.getenv("MINIMAX_API_KEY")));

	private final ToolCallingManager toolCallingManager = DefaultToolCallingManager.builder().build();

	@Test
	void testMarkSensitiveInfo() {

		UserMessage userMessage = new UserMessage(
				"Please extract the phone number, the content: My name is Bob, and my phone number is 133-12345678");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		// markSensitiveInfo is enabled by default
		ChatResponse response = this.chatModel
			.call(new Prompt(messages, MiniMaxChatOptions.builder().maskSensitiveInfo(true).build()));
		String responseContent = response.getResult().getOutput().getText();

		assertThat(responseContent).contains("133-**");
		assertThat(responseContent).doesNotContain("133-12345678");
	}

	@Test
	void testToolCalling() {
		UserMessage userMessage = new UserMessage("What is the weather in San Francisco?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));

		MiniMaxChatOptions options = MiniMaxChatOptions.builder()
			.model(org.springframework.ai.minimax.api.MiniMaxApi.ChatModel.ABAB_6_5_S_Chat.value)
			.toolCallbacks(List.of(FunctionToolCallback.builder("CurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();
		var prompt = new Prompt(messages, options);

		ChatResponse response = this.chatModel.call(prompt);

		while (response.hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
			prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
			response = this.chatModel.call(prompt);
		}

		String responseContent = response.getResult().getOutput().getText();

		assertThat(responseContent).contains("30");
	}

	@Test
	void testToolCallingStream() {
		UserMessage userMessage = new UserMessage("What is the weather in Paris?");

		List<Message> messages = new ArrayList<>(List.of(userMessage));
		MiniMaxChatOptions options = MiniMaxChatOptions.builder()
			.model(org.springframework.ai.minimax.api.MiniMaxApi.ChatModel.ABAB_6_5_S_Chat.value)
			.toolCallbacks(List.of(FunctionToolCallback.builder("CurrentWeather", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build()))
			.build();

		var prompt = new Prompt(messages, options);

		AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
		new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();

		while (aggregatedRef.get().hasToolCalls()) {
			ToolExecutionResult toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt,
					aggregatedRef.get());
			prompt = new Prompt(toolExecutionResult.conversationHistory(), options);
			aggregatedRef.set(null);
			new MessageAggregator().aggregate(this.chatModel.stream(prompt), aggregatedRef::set).collectList().block();
		}

		String content = Objects.requireNonNull(aggregatedRef.get().getResult()).getOutput().getText();

		logger.info("Response: {}", content);

		assertThat(content).contains("15");
	}

}
