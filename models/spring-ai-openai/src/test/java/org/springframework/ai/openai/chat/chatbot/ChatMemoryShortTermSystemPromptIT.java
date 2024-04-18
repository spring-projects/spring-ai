/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.openai.chat.chatbot;

import java.util.List;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.chatbot.ChatBot;
import org.springframework.ai.chat.chatbot.DefaultChatBot;
import org.springframework.ai.chat.chatbot.DefaultStreamingChatBot;
import org.springframework.ai.chat.chatbot.StreamingChatBot;
import org.springframework.ai.chat.history.ChatMemory;
import org.springframework.ai.chat.history.ChatMemoryAgentListener;
import org.springframework.ai.chat.history.ChatMemoryRetriever;
import org.springframework.ai.chat.history.InMemoryChatMemory;
import org.springframework.ai.chat.history.LastMaxTokenSizeContentTransformer;
import org.springframework.ai.chat.history.SystemPromptChatMemoryAugmentor;
import org.springframework.ai.evaluation.BaseMemoryTest;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

@SpringBootTest(classes = ChatMemoryShortTermSystemPromptIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class ChatMemoryShortTermSystemPromptIT extends BaseMemoryTest {

	@Autowired
	public ChatMemoryShortTermSystemPromptIT(RelevancyEvaluator relevancyEvaluator, ChatBot chatBot,
			StreamingChatBot streamingChatBot) {
		super(relevancyEvaluator, chatBot, streamingChatBot);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public OpenAiChatClient openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatClient(openAiApi);
		}

		@Bean
		public ChatMemory chatHistory() {
			return new InMemoryChatMemory();
		}

		@Bean
		public TokenCountEstimator tokenCountEstimator() {
			return new JTokkitTokenCountEstimator();
		}

		@Bean
		public ChatBot memoryChatAgent(OpenAiChatClient chatClient, ChatMemory chatHistory,
				TokenCountEstimator tokenCountEstimator) {

			return DefaultChatBot.builder(chatClient)
				.withRetrievers(List.of(new ChatMemoryRetriever(chatHistory)))
				.withContentPostProcessors(List.of(new LastMaxTokenSizeContentTransformer(tokenCountEstimator, 1000)))
				.withAugmentors(List.of(new SystemPromptChatMemoryAugmentor()))
				.withChatAgentListeners(List.of(new ChatMemoryAgentListener(chatHistory)))
				.build();
		}

		@Bean
		public StreamingChatBot memoryStreamingChatAgent(OpenAiChatClient streamingChatClient, ChatMemory chatHistory,
				TokenCountEstimator tokenCountEstimator) {

			return DefaultStreamingChatBot.builder(streamingChatClient)
				.withRetrievers(List.of(new ChatMemoryRetriever(chatHistory)))
				.withDocumentPostProcessors(List.of(new LastMaxTokenSizeContentTransformer(tokenCountEstimator, 1000)))
				.withAugmentors(List.of(new SystemPromptChatMemoryAugmentor()))
				.withChatAgentListeners(List.of(new ChatMemoryAgentListener(chatHistory)))
				.build();
		}

		@Bean
		public RelevancyEvaluator relevancyEvaluator(OpenAiChatClient chatClient) {
			return new RelevancyEvaluator(chatClient);
		}

	}

}
