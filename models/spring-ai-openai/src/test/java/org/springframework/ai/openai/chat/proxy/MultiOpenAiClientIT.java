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

package org.springframework.ai.openai.chat.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MultiOpenAiClientIT.Config.class)
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
@ActiveProfiles("logging-test")
class MultiOpenAiClientIT {

	private static final Logger logger = LoggerFactory.getLogger(MultiOpenAiClientIT.class);

	@Autowired
	private OpenAiChatModel baseChatModel;

	@Autowired
	private OpenAiApi baseOpenAiApi;

	@Test
	void multiClientFlow() {
		// Derive a new OpenAiApi for Groq (Llama3)
		OpenAiApi groqApi = baseOpenAiApi.mutate()
			.baseUrl("https://api.groq.com/openai")
			.apiKey(System.getenv("GROQ_API_KEY"))
			.build();

		// Derive a new OpenAiApi for OpenAI GPT-4
		OpenAiApi gpt4Api = baseOpenAiApi.mutate()
			.baseUrl("https://api.openai.com")
			.apiKey(System.getenv("OPENAI_API_KEY"))
			.build();

		// Derive a new OpenAiChatModel for Groq
		OpenAiChatModel groqModel = baseChatModel.mutate()
			.openAiApi(groqApi)
			.defaultOptions(OpenAiChatOptions.builder().model("llama3-70b-8192").temperature(0.5).build())
			.build();

		// Derive a new OpenAiChatModel for GPT-4
		OpenAiChatModel gpt4Model = baseChatModel.mutate()
			.openAiApi(gpt4Api)
			.defaultOptions(OpenAiChatOptions.builder().model("gpt-4").temperature(0.7).build())
			.build();

		// Simple prompt for both models
		String prompt = "What is the capital of France?";

		String groqResponse = ChatClient.builder(groqModel).build().prompt(prompt).call().content();
		String gpt4Response = ChatClient.builder(gpt4Model).build().prompt(prompt).call().content();

		logger.info("Groq (Llama3) response: {}", groqResponse);
		logger.info("OpenAI GPT-4 response: {}", gpt4Response);

		assertThat(groqResponse).containsIgnoringCase("Paris");
		assertThat(gpt4Response).containsIgnoringCase("Paris");

		logger.info("OpenAI GPT-4 response: {}", gpt4Response);

		assertThat(groqResponse).containsIgnoringCase("Paris");
		assertThat(gpt4Response).containsIgnoringCase("Paris");
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi() {
			return OpenAiApi.builder().baseUrl("foo").apiKey("bar").build();
		}

		@Bean
		public OpenAiChatModel openAiClient(OpenAiApi openAiApi) {
			return OpenAiChatModel.builder().openAiApi(openAiApi).build();
		}

	}

}
