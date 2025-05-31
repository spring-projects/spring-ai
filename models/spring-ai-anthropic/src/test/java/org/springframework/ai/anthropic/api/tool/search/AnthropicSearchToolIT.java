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

package org.springframework.ai.anthropic.api.tool.search;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
public class AnthropicSearchToolIT {

	private static final Logger logger = LoggerFactory.getLogger(AnthropicSearchToolIT.class);

	AnthropicApi anthropicApi = AnthropicApi.builder().apiKey(System.getenv("ANTHROPIC_API_KEY")).build();

	@Test
	void chatCompletionWithWebSearchTool() {
		List<AnthropicApi.AnthropicMessage> messageConversation = new ArrayList<>();

		AnthropicApi.AnthropicMessage chatCompletionMessage = new AnthropicApi.AnthropicMessage(
				List.of(new AnthropicApi.ContentBlock(
						"What's the weather like in San Francisco? Show the temperature in Celsius.")),
				AnthropicApi.Role.USER);

		messageConversation.add(chatCompletionMessage);

		AnthropicApi.ChatCompletionRequest chatCompletionRequest = AnthropicApi.ChatCompletionRequest.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_3_5_HAIKU)
			.messages(messageConversation)
			.maxTokens(1024)
			.tools(List.of(WebSearchTool.builder().maxUses(1).build()))
			.build();

		ResponseEntity<ChatCompletionResponse> response = this.anthropicApi.chatCompletionEntity(chatCompletionRequest);

		var responseText = response.getBody()
			.content()
			.stream()
			.filter(contentBlock -> contentBlock.type() == ContentBlock.Type.TEXT)
			.map(ContentBlock::text)
			.map(str -> str.replace("\n", " ").trim())
			.collect(Collectors.joining());
		logger.info("RESPONSE: " + responseText);

		assertThat(response.getBody().usage().serverToolUse()).isNotNull();
		assertThat(response.getBody().usage().serverToolUse().webSearchRequests()).isGreaterThan(0);
	}

}
