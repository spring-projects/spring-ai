/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.model.chat.memory.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatMemoryAutoConfiguration}.
 *
 * @author Thomas Vitale
 */
class ChatMemoryAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatMemoryAutoConfiguration.class));

	@Test
	void defaultConfiguration() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(ChatMemoryRepository.class);
			assertThat(context).hasSingleBean(ChatMemory.class);
		});
	}

	@Test
	void whenChatMemoryRepositoryExists() {
		this.contextRunner.withUserConfiguration(CustomChatMemoryRepositoryConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(ChatMemoryRepository.class);
			assertThat(context).hasBean("customChatMemoryRepository");
			assertThat(context).doesNotHaveBean("chatMemoryRepository");
		});
	}

	@Test
	void whenChatMemoryExists() {
		this.contextRunner.withUserConfiguration(CustomChatMemoryRepositoryConfiguration.class).run(context -> {
			assertThat(context).hasSingleBean(ChatMemoryRepository.class);
			assertThat(context).hasBean("customChatMemoryRepository");
			assertThat(context).doesNotHaveBean("chatMemoryRepository");
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class CustomChatMemoryRepositoryConfiguration {

		private final ChatMemoryRepository customChatMemoryRepository = new InMemoryChatMemoryRepository();

		@Bean
		ChatMemoryRepository customChatMemoryRepository() {
			return this.customChatMemoryRepository;
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class CustomChatMemoryConfiguration {

		private final ChatMemory customChatMemory = MessageWindowChatMemory.builder().build();

		@Bean
		ChatMemory customChatMemory() {
			return this.customChatMemory;
		}

	}

}
