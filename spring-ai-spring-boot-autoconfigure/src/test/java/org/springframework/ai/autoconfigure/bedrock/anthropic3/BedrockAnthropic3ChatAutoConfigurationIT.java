/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.bedrock.anthropic3;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.autoconfigure.bedrock.BedrockTestUtils;
import org.springframework.ai.autoconfigure.bedrock.RequiresAwsCredentials;
import org.springframework.ai.bedrock.anthropic3.BedrockAnthropic3ChatModel;
import org.springframework.ai.bedrock.anthropic3.api.Anthropic3ChatBedrockApi.AnthropicChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
@RequiresAwsCredentials
public class BedrockAnthropic3ChatAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = BedrockTestUtils.getContextRunner()
		.withPropertyValues("spring.ai.bedrock.anthropic3.chat.enabled=true",
				"spring.ai.bedrock.anthropic3.chat.model=" + AnthropicChatModel.CLAUDE_V3_SONNET.id(),
				"spring.ai.bedrock.anthropic3.chat.options.temperature=0.5")
		.withConfiguration(AutoConfigurations.of(BedrockAnthropic3ChatAutoConfiguration.class));

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
		this.contextRunner.run(context -> {
			BedrockAnthropic3ChatModel anthropicChatModel = context.getBean(BedrockAnthropic3ChatModel.class);
			ChatResponse response = anthropicChatModel.call(new Prompt(List.of(this.userMessage, this.systemMessage)));
			assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");
		});
	}

	@Test
	public void chatCompletionStreaming() {
		this.contextRunner.run(context -> {

			BedrockAnthropic3ChatModel anthropicChatModel = context.getBean(BedrockAnthropic3ChatModel.class);

			Flux<ChatResponse> response = anthropicChatModel
				.stream(new Prompt(List.of(this.userMessage, this.systemMessage)));

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

		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.anthropic3.chat.enabled=true",
					"spring.ai.bedrock.aws.access-key=ACCESS_KEY", "spring.ai.bedrock.aws.secret-key=SECRET_KEY",
					"spring.ai.bedrock.anthropic3.chat.model=MODEL_XYZ",
					"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
					"spring.ai.bedrock.anthropic3.chat.options.temperature=0.55")
			.withConfiguration(AutoConfigurations.of(BedrockAnthropic3ChatAutoConfiguration.class))
			.run(context -> {
				var anthropicChatProperties = context.getBean(BedrockAnthropic3ChatProperties.class);
				var awsProperties = context.getBean(BedrockAwsConnectionProperties.class);

				assertThat(anthropicChatProperties.isEnabled()).isTrue();
				assertThat(awsProperties.getRegion()).isEqualTo(Region.US_EAST_1.id());

				assertThat(anthropicChatProperties.getOptions().getTemperature()).isCloseTo(0.55, within(0.0001));
				assertThat(anthropicChatProperties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(awsProperties.getAccessKey()).isEqualTo("ACCESS_KEY");
				assertThat(awsProperties.getSecretKey()).isEqualTo("SECRET_KEY");
			});
	}

	@Test
	public void chatCompletionDisabled() {

		// It is disabled by default
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withConfiguration(AutoConfigurations.of(BedrockAnthropic3ChatAutoConfiguration.class))

			.run(context -> {
				assertThat(context.getBeansOfType(BedrockAnthropic3ChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockAnthropic3ChatModel.class)).isEmpty();
			});

		// Explicitly enable the chat auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.anthropic3.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(BedrockAnthropic3ChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockAnthropic3ChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(BedrockAnthropic3ChatModel.class)).isNotEmpty();
			});

		// Explicitly disable the chat auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.anthropic3.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(BedrockAnthropic3ChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockAnthropic3ChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockAnthropic3ChatModel.class)).isEmpty();
			});
	}

}
