/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.bedrock.anthropic;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.bedrock.anthropic.BedrockAnthropicChatClient;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatModel;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class BedrockAnthropicChatAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.bedrock.anthropic.chat.enabled=true",
				"spring.ai.bedrock.aws.access-key=" + System.getenv("AWS_ACCESS_KEY_ID"),
				"spring.ai.bedrock.aws.secret-key=" + System.getenv("AWS_SECRET_ACCESS_KEY"),
				"spring.ai.bedrock.anthropic.chat.model=" + AnthropicChatModel.CLAUDE_V2.id(),
				"spring.ai.bedrock.aws.region=" + Region.EU_CENTRAL_1.id(),
				"spring.ai.bedrock.anthropic.chat.temperature=0.5", "spring.ai.bedrock.anthropic.chat.maxGenLen=500")
		.withConfiguration(AutoConfigurations.of(BedrockAnthropicChatAutoConfiguration.class));

	private final Message systemMessage = new SystemPromptTemplate("""
			You are a helpful AI assistant. Your name is {name}.
			You are an AI assistant that helps people find information.
			Your name is {name}
			You should reply to the user's request with your name and also in the style of a {voice}.
			""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

	private final UserMessage userMessage = new UserMessage(
			"Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.");

	@Test
	public void chatCompletion() {
		contextRunner.run(context -> {
			BedrockAnthropicChatClient anthropicChatClient = context.getBean(BedrockAnthropicChatClient.class);
			ChatResponse response = anthropicChatClient.call(new Prompt(List.of(userMessage, systemMessage)));
			assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");
		});
	}

	@Test
	public void chatCompletionStreaming() {
		contextRunner.run(context -> {

			BedrockAnthropicChatClient anthropicChatClient = context.getBean(BedrockAnthropicChatClient.class);

			Flux<ChatResponse> response = anthropicChatClient.stream(new Prompt(List.of(userMessage, systemMessage)));

			List<ChatResponse> responses = response.collectList().block();
			assertThat(responses.size()).isGreaterThan(2);

			String stitchedResponseContent = responses.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getContent)
				.collect(Collectors.joining());

			assertThat(stitchedResponseContent).contains("Blackbeard");
		});
	}

	@Test
	public void propertiesTest() {

		new ApplicationContextRunner()
			.withPropertyValues("spring.ai.bedrock.anthropic.chat.enabled=true",
					"spring.ai.bedrock.aws.access-key=ACCESS_KEY", "spring.ai.bedrock.aws.secret-key=SECRET_KEY",
					"spring.ai.bedrock.anthropic.chat.model=MODEL_XYZ",
					"spring.ai.bedrock.aws.region=" + Region.EU_CENTRAL_1.id(),
					"spring.ai.bedrock.anthropic.chat.temperature=0.55")
			.withConfiguration(AutoConfigurations.of(BedrockAnthropicChatAutoConfiguration.class))
			.run(context -> {
				var anthropicChatProperties = context.getBean(BedrockAnthropicChatProperties.class);
				var awsProperties = context.getBean(BedrockAwsConnectionProperties.class);

				assertThat(anthropicChatProperties.isEnabled()).isTrue();
				assertThat(awsProperties.getRegion()).isEqualTo(Region.EU_CENTRAL_1.id());

				assertThat(anthropicChatProperties.getTemperature()).isEqualTo(0.55f);
				assertThat(anthropicChatProperties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(awsProperties.getAccessKey()).isEqualTo("ACCESS_KEY");
				assertThat(awsProperties.getSecretKey()).isEqualTo("SECRET_KEY");
			});
	}

	@Test
	public void chatCompletionDisabled() {

		// It is disabled by default
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(BedrockAnthropicChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockAnthropicChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockAnthropicChatClient.class)).isEmpty();
			});

		// Explicitly enable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.bedrock.anthropic.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(BedrockAnthropicChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockAnthropicChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(BedrockAnthropicChatClient.class)).isNotEmpty();
			});

		// Explicitly disable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.bedrock.anthropic.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(BedrockAnthropicChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockAnthropicChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockAnthropicChatClient.class)).isEmpty();
			});
	}

}
