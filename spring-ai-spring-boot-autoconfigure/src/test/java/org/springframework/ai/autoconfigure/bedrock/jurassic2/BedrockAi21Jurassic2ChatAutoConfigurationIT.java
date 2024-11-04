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

package org.springframework.ai.autoconfigure.bedrock.jurassic2;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.autoconfigure.bedrock.BedrockTestUtils;
import org.springframework.ai.autoconfigure.bedrock.RequiresAwsCredentials;
import org.springframework.ai.autoconfigure.bedrock.jurrasic2.BedrockAi21Jurassic2ChatAutoConfiguration;
import org.springframework.ai.autoconfigure.bedrock.jurrasic2.BedrockAi21Jurassic2ChatProperties;
import org.springframework.ai.bedrock.jurassic2.BedrockAi21Jurassic2ChatModel;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;

/**
 * @author Ahmed Yousri
 * @since 1.0.0
 */
@RequiresAwsCredentials
public class BedrockAi21Jurassic2ChatAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = BedrockTestUtils.getContextRunner()
		.withPropertyValues("spring.ai.bedrock.jurassic2.chat.enabled=true",
				"spring.ai.bedrock.jurassic2.chat.model="
						+ Ai21Jurassic2ChatBedrockApi.Ai21Jurassic2ChatModel.AI21_J2_ULTRA_V1.id(),
				"spring.ai.bedrock.jurassic2.chat.options.temperature=0.5",
				"spring.ai.bedrock.jurassic2.chat.options.maxGenLen=500")
		.withConfiguration(AutoConfigurations.of(BedrockAi21Jurassic2ChatAutoConfiguration.class));

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
			BedrockAi21Jurassic2ChatModel ai21Jurassic2ChatModel = context.getBean(BedrockAi21Jurassic2ChatModel.class);
			ChatResponse response = ai21Jurassic2ChatModel
				.call(new Prompt(List.of(this.userMessage, this.systemMessage)));
			assertThat(response.getResult().getOutput().getContent()).contains("Blackbeard");
		});
	}

	@Test
	public void propertiesTest() {

		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.jurassic2.chat.enabled=true",
					"spring.ai.bedrock.aws.access-key=ACCESS_KEY", "spring.ai.bedrock.aws.secret-key=SECRET_KEY",
					"spring.ai.bedrock.jurassic2.chat.model=MODEL_XYZ",
					"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
					"spring.ai.bedrock.jurassic2.chat.options.temperature=0.55",
					"spring.ai.bedrock.jurassic2.chat.options.maxTokens=123")
			.withConfiguration(AutoConfigurations.of(BedrockAi21Jurassic2ChatAutoConfiguration.class))
			.run(context -> {
				var chatProperties = context.getBean(BedrockAi21Jurassic2ChatProperties.class);
				var awsProperties = context.getBean(BedrockAwsConnectionProperties.class);

				assertThat(chatProperties.isEnabled()).isTrue();
				assertThat(awsProperties.getRegion()).isEqualTo(Region.US_EAST_1.id());

				assertThat(chatProperties.getOptions().getTemperature()).isCloseTo(0.55, within(0.0001));
				assertThat(chatProperties.getOptions().getMaxTokens()).isEqualTo(123);
				assertThat(chatProperties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(awsProperties.getAccessKey()).isEqualTo("ACCESS_KEY");
				assertThat(awsProperties.getSecretKey()).isEqualTo("SECRET_KEY");
			});
	}

	@Test
	public void chatCompletionDisabled() {

		// It is disabled by default
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withConfiguration(AutoConfigurations.of(BedrockAi21Jurassic2ChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockAi21Jurassic2ChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockAi21Jurassic2ChatModel.class)).isEmpty();
			});

		// Explicitly enable the chat auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.jurassic2.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(BedrockAi21Jurassic2ChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockAi21Jurassic2ChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(BedrockAi21Jurassic2ChatModel.class)).isNotEmpty();
			});

		// Explicitly disable the chat auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.jurassic2.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(BedrockAi21Jurassic2ChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockAi21Jurassic2ChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockAi21Jurassic2ChatModel.class)).isEmpty();
			});
	}

}
