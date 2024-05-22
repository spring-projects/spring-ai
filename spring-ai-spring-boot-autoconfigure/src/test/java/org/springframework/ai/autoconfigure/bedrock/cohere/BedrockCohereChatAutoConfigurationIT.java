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
package org.springframework.ai.autoconfigure.bedrock.cohere;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.bedrock.cohere.BedrockCohereChatModel;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatModel;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.ReturnLikelihoods;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.Truncate;
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
public class BedrockCohereChatAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.bedrock.cohere.chat.enabled=true",
				"spring.ai.bedrock.aws.access-key=" + System.getenv("AWS_ACCESS_KEY_ID"),
				"spring.ai.bedrock.aws.secret-key=" + System.getenv("AWS_SECRET_ACCESS_KEY"),
				"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
				"spring.ai.bedrock.cohere.chat.model=" + CohereChatModel.COHERE_COMMAND_V14.id(),
				"spring.ai.bedrock.cohere.chat.options.temperature=0.5",
				"spring.ai.bedrock.cohere.chat.options.maxTokens=500")
		.withConfiguration(AutoConfigurations.of(BedrockCohereChatAutoConfiguration.class));

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
			BedrockCohereChatModel cohereChatModel = context.getBean(BedrockCohereChatModel.class);
			ChatResponse response = cohereChatModel.call(new Prompt(List.of(userMessage, systemMessage)));
			assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");
		});
	}

	@Test
	public void chatCompletionStreaming() {
		contextRunner.run(context -> {

			BedrockCohereChatModel cohereChatModel = context.getBean(BedrockCohereChatModel.class);

			Flux<ChatResponse> response = cohereChatModel.stream(new Prompt(List.of(userMessage, systemMessage)));

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
			.withPropertyValues("spring.ai.bedrock.cohere.chat.enabled=true",
					"spring.ai.bedrock.aws.access-key=ACCESS_KEY", "spring.ai.bedrock.aws.secret-key=SECRET_KEY",
					"spring.ai.bedrock.cohere.chat.model=MODEL_XYZ",
					"spring.ai.bedrock.aws.region=" + Region.EU_CENTRAL_1.id(),
					"spring.ai.bedrock.cohere.chat.options.temperature=0.55",
					"spring.ai.bedrock.cohere.chat.options.topP=0.55", "spring.ai.bedrock.cohere.chat.options.topK=10",
					"spring.ai.bedrock.cohere.chat.options.stopSequences=END1,END2",
					"spring.ai.bedrock.cohere.chat.options.returnLikelihoods=ALL",
					"spring.ai.bedrock.cohere.chat.options.numGenerations=3",
					"spring.ai.bedrock.cohere.chat.options.truncate=START",
					"spring.ai.bedrock.cohere.chat.options.maxTokens=123")
			.withConfiguration(AutoConfigurations.of(BedrockCohereChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(BedrockCohereChatProperties.class);
				var aswProperties = context.getBean(BedrockAwsConnectionProperties.class);

				assertThat(chatProperties.isEnabled()).isTrue();
				assertThat(aswProperties.getRegion()).isEqualTo(Region.EU_CENTRAL_1.id());
				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(chatProperties.getOptions().getTemperature()).isEqualTo(0.55f);
				assertThat(chatProperties.getOptions().getTopP()).isEqualTo(0.55f);
				assertThat(chatProperties.getOptions().getTopK()).isEqualTo(10);
				assertThat(chatProperties.getOptions().getStopSequences()).isEqualTo(List.of("END1", "END2"));
				assertThat(chatProperties.getOptions().getReturnLikelihoods()).isEqualTo(ReturnLikelihoods.ALL);
				assertThat(chatProperties.getOptions().getNumGenerations()).isEqualTo(3);
				assertThat(chatProperties.getOptions().getTruncate()).isEqualTo(Truncate.START);
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);

				assertThat(aswProperties.getAccessKey()).isEqualTo("ACCESS_KEY");
				assertThat(aswProperties.getSecretKey()).isEqualTo("SECRET_KEY");
			});
	}

	@Test
	public void chatCompletionDisabled() {

		// It is disabled by default
		new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(BedrockCohereChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockCohereChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockCohereChatModel.class)).isEmpty();
			});

		// Explicitly enable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.bedrock.cohere.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(BedrockCohereChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockCohereChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(BedrockCohereChatModel.class)).isNotEmpty();
			});

		// Explicitly disable the chat auto-configuration.
		new ApplicationContextRunner().withPropertyValues("spring.ai.bedrock.cohere.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(BedrockCohereChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockCohereChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockCohereChatModel.class)).isEmpty();
			});
	}

}
