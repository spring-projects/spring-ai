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

package org.springframework.ai.autoconfigure.bedrock.titan;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.autoconfigure.bedrock.BedrockTestUtils;
import org.springframework.ai.autoconfigure.bedrock.RequiresAwsCredentials;
import org.springframework.ai.bedrock.titan.BedrockTitanChatModel;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatModel;
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
 * @author Mark Pollack
 * @since 1.0.0
 */
@RequiresAwsCredentials
public class BedrockTitanChatAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = BedrockTestUtils.getContextRunner()
		.withPropertyValues("spring.ai.bedrock.titan.chat.enabled=true",
				"spring.ai.bedrock.titan.chat.model=" + TitanChatModel.TITAN_TEXT_EXPRESS_V1.id(),
				"spring.ai.bedrock.titan.chat.options.temperature=0.5",
				"spring.ai.bedrock.titan.chat.options.maxTokenCount=500")
		.withConfiguration(AutoConfigurations.of(BedrockTitanChatAutoConfiguration.class));

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
			BedrockTitanChatModel chatModel = context.getBean(BedrockTitanChatModel.class);
			ChatResponse response = chatModel.call(new Prompt(List.of(this.userMessage, this.systemMessage)));
			assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");
		});
	}

	@Test
	public void chatCompletionStreaming() {
		this.contextRunner.run(context -> {

			BedrockTitanChatModel chatModel = context.getBean(BedrockTitanChatModel.class);

			Flux<ChatResponse> response = chatModel.stream(new Prompt(List.of(this.userMessage, this.systemMessage)));

			List<ChatResponse> responses = response.collectList().block();
			assertThat(responses.size()).isGreaterThan(1);

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
			.withPropertyValues("spring.ai.bedrock.titan.chat.enabled=true",
					"spring.ai.bedrock.aws.access-key=ACCESS_KEY", "spring.ai.bedrock.aws.secret-key=SECRET_KEY",
					"spring.ai.bedrock.titan.chat.model=MODEL_XYZ",
					"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
					"spring.ai.bedrock.titan.chat.options.temperature=0.55",
					"spring.ai.bedrock.titan.chat.options.topP=0.55",
					"spring.ai.bedrock.titan.chat.options.stopSequences=END1,END2",
					"spring.ai.bedrock.titan.chat.options.maxTokenCount=123")
			.withConfiguration(AutoConfigurations.of(BedrockTitanChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(BedrockTitanChatProperties.class);
				var aswProperties = context.getBean(BedrockAwsConnectionProperties.class);

				assertThat(chatProperties.isEnabled()).isTrue();
				assertThat(aswProperties.getRegion()).isEqualTo(Region.US_EAST_1.id());
				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(chatProperties.getOptions().getTemperature()).isCloseTo(0.55, within(0.0001));
				assertThat(chatProperties.getOptions().getTopP()).isCloseTo(0.55, within(0.0001));

				assertThat(chatProperties.getOptions().getStopSequences()).isEqualTo(List.of("END1", "END2"));
				assertThat(chatProperties.getOptions().getMaxTokenCount()).isEqualTo(123);

				assertThat(aswProperties.getAccessKey()).isEqualTo("ACCESS_KEY");
				assertThat(aswProperties.getSecretKey()).isEqualTo("SECRET_KEY");
			});
	}

	@Test
	public void chatCompletionDisabled() {

		// It is disabled by default
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withConfiguration(AutoConfigurations.of(BedrockTitanChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockTitanChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockTitanChatModel.class)).isEmpty();
			});

		// Explicitly enable the chat auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.titan.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(BedrockTitanChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockTitanChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(BedrockTitanChatModel.class)).isNotEmpty();
			});

		// Explicitly disable the chat auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.titan.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(BedrockTitanChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockTitanChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockTitanChatModel.class)).isEmpty();
			});
	}

}
