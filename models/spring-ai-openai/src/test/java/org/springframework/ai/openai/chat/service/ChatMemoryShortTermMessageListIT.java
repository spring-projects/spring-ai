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
package org.springframework.ai.openai.chat.service;

import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.service.ChatService;
import org.springframework.ai.chat.service.PromptTransformingChatService;
import org.springframework.ai.chat.service.StreamingPromptTransformingChatService;
import org.springframework.ai.chat.service.StreamingChatService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryChatServiceListener;
import org.springframework.ai.chat.memory.ChatMemoryRetriever;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.memory.LastMaxTokenSizeContentTransformer;
import org.springframework.ai.chat.memory.MessageChatMemoryAugmentor;
import org.springframework.ai.evaluation.BaseMemoryTest;
import org.springframework.ai.evaluation.RelevancyEvaluator;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

@Disabled("ChatService Memory implementation are deprecated. No need to test them.")
@SpringBootTest(classes = ChatMemoryShortTermMessageListIT.Config.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class ChatMemoryShortTermMessageListIT extends BaseMemoryTest {

	@Autowired
	public ChatMemoryShortTermMessageListIT(RelevancyEvaluator relevancyEvaluator, ChatService chatService,
			StreamingChatService streamingChatService) {
		super(relevancyEvaluator, chatService, streamingChatService);
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return new OpenAiApi(System.getenv("OPENAI_API_KEY"));
		}

		@Bean
		public OpenAiChatModel openAiClient(OpenAiApi openAiApi) {
			return new OpenAiChatModel(openAiApi);
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
		public ChatService memoryChatService(OpenAiChatModel chatModel, ChatMemory chatHistory,
				TokenCountEstimator tokenCountEstimator) {

			return PromptTransformingChatService.builder(chatModel)
				.withRetrievers(List.of(new ChatMemoryRetriever(chatHistory)))
				.withContentPostProcessors(List.of(new LastMaxTokenSizeContentTransformer(tokenCountEstimator, 1000)))
				.withAugmentors(List.of(new MessageChatMemoryAugmentor()))
				.withChatServiceListeners(List.of(new ChatMemoryChatServiceListener(chatHistory)))
				.build();
		}

		@Bean
		public StreamingChatService memoryStreamingChatService(OpenAiChatModel streamingChatModel,
				ChatMemory chatHistory, TokenCountEstimator tokenCountEstimator) {

			return StreamingPromptTransformingChatService.builder(streamingChatModel)
				.withRetrievers(List.of(new ChatMemoryRetriever(chatHistory)))
				.withDocumentPostProcessors(List.of(new LastMaxTokenSizeContentTransformer(tokenCountEstimator, 1000)))
				.withAugmentors(List.of(new MessageChatMemoryAugmentor()))
				.withChatServiceListeners(List.of(new ChatMemoryChatServiceListener(chatHistory)))
				.build();
		}

		@Bean
		public RelevancyEvaluator relevancyEvaluator(OpenAiChatModel chatModel) {
			return new RelevancyEvaluator(chatModel);
		}

	}

}
