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

package org.springframework.ai.model.chat.memory.redis.autoconfigure;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.chat.memory.repository.redis.autoconfigure.RedisChatMemoryRepositoryAutoConfiguration;
import org.springframework.ai.model.chat.memory.repository.redis.autoconfigure.RedisChatMemoryRepositoryProperties;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
class RedisChatMemoryAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(RedisChatMemoryAutoConfiguration.class,
				RedisChatMemoryRepositoryAutoConfiguration.class));

	@Test
	void propertiesWithOldPrefix() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
				"spring.ai.chat.memory.redis.host=10.0.0.1",
						"spring.ai.chat.memory.redis.port=6380",
						"spring.ai.chat.memory.redis.initialize-schema=false")
				// @formatter:on
			.run(context -> {
				assertThat(context).hasSingleBean(RedisChatMemoryRepositoryProperties.class);
				var chatProperties = context.getBean(RedisChatMemoryRepositoryProperties.class);

				assertThat(chatProperties.getHost()).isEqualTo("10.0.0.1");
				assertThat(chatProperties.getPort()).isEqualTo(6380);
				assertThat(chatProperties.getInitializeSchema()).isEqualTo(false);
			});
	}

	@Test
	void propertiesWithNewPrefix() {
		this.contextRunner.withPropertyValues(
		// @formatter:off
						"spring.ai.chat.memory.repository.redis.host=10.0.0.1",
						"spring.ai.chat.memory.repository.redis.port=6380",
						"spring.ai.chat.memory.repository.redis.initialize-schema=false")
				// @formatter:on
			.run(context -> {
				assertThat(context).hasSingleBean(RedisChatMemoryRepositoryProperties.class);
				var chatProperties = context.getBean(RedisChatMemoryRepositoryProperties.class);

				assertThat(chatProperties.getHost()).isEqualTo("10.0.0.1");
				assertThat(chatProperties.getPort()).isEqualTo(6380);
				assertThat(chatProperties.getInitializeSchema()).isEqualTo(false);
			});
	}

	@Test
	void passwordWithOldPrefix() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.memory.redis.host=10.0.0.1", "spring.ai.chat.memory.redis.port=6380",
					"spring.ai.chat.memory.redis.password=secret",
					"spring.ai.chat.memory.redis.initialize-schema=false")
			.run(context -> {
				var chatProperties = context.getBean(RedisChatMemoryRepositoryProperties.class);
				assertThat(chatProperties.getPassword()).isEqualTo("secret");
			});
	}

	@Test
	void passwordWithNewPrefix() {
		this.contextRunner
			.withPropertyValues("spring.ai.chat.memory.repository.redis.host=10.0.0.1",
					"spring.ai.chat.memory.repository.redis.port=6380",
					"spring.ai.chat.memory.repository.redis.password=secret",
					"spring.ai.chat.memory.repository.redis.initialize-schema=false")
			.run(context -> {
				var chatProperties = context.getBean(RedisChatMemoryRepositoryProperties.class);
				assertThat(chatProperties.getPassword()).isEqualTo("secret");
			});
	}

}
