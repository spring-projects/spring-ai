/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.chat.memory.repository.bedrock.agentcore.autoconfigure;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.bedrock.agentcore.BedrockAgentCoreChatMemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link BedrockAgentCoreChatMemoryRepositoryAutoConfiguration}.
 *
 * @author Chaemin Lee
 */
class BedrockAgentCoreChatMemoryRepositoryAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(BedrockAgentCoreChatMemoryRepositoryAutoConfiguration.class))
		.withUserConfiguration(MockAwsConfig.class)
		.withPropertyValues("spring.ai.chat.memory.repository.bedrock.agent-core.memory.memory-id=test-store");

	@Test
	void autoConfiguresBedrockRepository() {
		this.contextRunner.run(ctx -> {
			assertThat(ctx).hasSingleBean(BedrockAgentCoreChatMemoryRepository.class);
			assertThat(ctx).hasSingleBean(ChatMemoryRepository.class);
		});
	}

	@Test
	void backsOffWhenChatMemoryRepositoryAlreadyDefined() {
		this.contextRunner.withUserConfiguration(UserDefinedRepositoryConfig.class).run(ctx -> {
			assertThat(ctx).hasSingleBean(ChatMemoryRepository.class);
			assertThat(ctx).doesNotHaveBean(BedrockAgentCoreChatMemoryRepository.class);
		});
	}

	@Test
	void defaultActorIdIsSpringAi() {
		this.contextRunner.run(ctx -> {
			BedrockAgentCoreChatMemoryRepositoryProperties props = ctx
				.getBean(BedrockAgentCoreChatMemoryRepositoryProperties.class);
			assertThat(props.getActorId()).isEqualTo("spring-ai");
		});
	}

	@Test
	void customActorIdIsApplied() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.memory.repository.bedrock.agent-core.memory.actor-id=my-agent")
			.run(ctx -> {
				BedrockAgentCoreChatMemoryRepositoryProperties props = ctx
					.getBean(BedrockAgentCoreChatMemoryRepositoryProperties.class);
				assertThat(props.getActorId()).isEqualTo("my-agent");
			});
	}

	@Configuration
	static class MockAwsConfig {

		@Bean
		AwsCredentialsProvider credentialsProvider() {
			return Mockito.mock(AwsCredentialsProvider.class);
		}

		@Bean
		AwsRegionProvider regionProvider() {
			AwsRegionProvider mock = Mockito.mock(AwsRegionProvider.class);
			Mockito.when(mock.getRegion()).thenReturn(Region.US_EAST_1);
			return mock;
		}

	}

	@Configuration
	static class UserDefinedRepositoryConfig {

		@Bean
		ChatMemoryRepository customRepository() {
			return Mockito.mock(ChatMemoryRepository.class);
		}

	}

}
