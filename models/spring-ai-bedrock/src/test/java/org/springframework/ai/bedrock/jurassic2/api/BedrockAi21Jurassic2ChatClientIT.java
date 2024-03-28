/*
 * Copyright 2023 - 2024 the original author or authors.
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

package org.springframework.ai.bedrock.jurassic2.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.bedrock.jurassic2.BedrockAi21Jurassic2ChatClient;
import org.springframework.ai.bedrock.jurassic2.BedrockAi21Jurassic2ChatOptions;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.parser.MapOutputParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
class BedrockAi21Jurassic2ChatClientIT {

	@Autowired
	private BedrockAi21Jurassic2ChatClient client;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void roleTest() {
		UserMessage userMessage = new UserMessage(
				"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));

		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

		ChatResponse response = client.call(prompt);

		assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");
	}

	@Test
	void testEmojiPenaltyFalse() {
		BedrockAi21Jurassic2ChatOptions.Penalty penalty = new BedrockAi21Jurassic2ChatOptions.Penalty.Builder()
			.applyToEmojis(false)
			.build();
		BedrockAi21Jurassic2ChatOptions options = new BedrockAi21Jurassic2ChatOptions.Builder()
			.withPresencePenalty(penalty)
			.build();

		UserMessage userMessage = new UserMessage("Can you express happiness using an emoji like 😄 ?");
		Prompt prompt = new Prompt(List.of(userMessage), options);

		ChatResponse response = client.call(prompt);

		assertThat(response.getResult().getOutput().getContent()).matches(content -> content.contains("😄"));
	}

	@Test
	@Disabled("This test is failing when run in combination with the other tests")
	void emojiPenaltyWhenTrueByDefaultApplyPenaltyTest() {
		// applyToEmojis is by default true
		BedrockAi21Jurassic2ChatOptions.Penalty penalty = new BedrockAi21Jurassic2ChatOptions.Penalty.Builder().build();
		BedrockAi21Jurassic2ChatOptions options = new BedrockAi21Jurassic2ChatOptions.Builder()
			.withPresencePenalty(penalty)
			.build();

		UserMessage userMessage = new UserMessage("Can you express happiness using an emoji like 😄?");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));

		Prompt prompt = new Prompt(List.of(userMessage, systemMessage), options);

		ChatResponse response = client.call(prompt);

		assertThat(response.getResult().getOutput().getContent()).doesNotContain("😄");
	}

	@Test
	void mapOutputParser() {
		MapOutputParser outputParser = new MapOutputParser();

		String format = outputParser.getFormat();
		String template = """
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = client.call(prompt).getResult();

		Map<String, Object> result = outputParser.parse(generation.getOutput().getContent());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	@Test
	void simpleChatResponse() {
		UserMessage userMessage = new UserMessage("Tell me a joke about AI.");
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", "Bob", "voice", "pirate"));

		Prompt prompt = new Prompt(List.of(userMessage, systemMessage));

		ChatResponse response = client.call(prompt);

		assertThat(response.getResult().getOutput().getContent()).contains("AI");
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public Ai21Jurassic2ChatBedrockApi jurassic2ChatBedrockApi() {
			return new Ai21Jurassic2ChatBedrockApi(
					Ai21Jurassic2ChatBedrockApi.Ai21Jurassic2ChatModel.AI21_J2_MID_V1.id(),
					EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
					Duration.ofMinutes(2));
		}

		@Bean
		public BedrockAi21Jurassic2ChatClient bedrockAi21Jurassic2ChatClient(
				Ai21Jurassic2ChatBedrockApi jurassic2ChatBedrockApi) {
			return new BedrockAi21Jurassic2ChatClient(jurassic2ChatBedrockApi,
					BedrockAi21Jurassic2ChatOptions.builder()
						.withTemperature(0.5f)
						.withMaxTokens(100)
						.withTopP(0.9f)
						.build());
		}

	}

}
