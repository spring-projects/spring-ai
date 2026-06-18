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

import org.jspecify.annotations.Nullable;
import redis.clients.jedis.RedisClient;

import org.springframework.ai.chat.memory.repository.redis.RedisChatMemoryRepository;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.ai.model.chat.memory.repository.redis.autoconfigure.RedisChatMemoryRepositoryAutoConfiguration;
import org.springframework.ai.model.chat.memory.repository.redis.autoconfigure.RedisChatMemoryRepositoryProperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

/**
 * Auto-configuration for Redis-based chat memory implementation.
 *
 * @author Brian Sam-Bodden
 * @author Yanming Zhou
 * @deprecated Use {@link RedisChatMemoryRepositoryAutoConfiguration} instead.
 */
@Deprecated(since = "2.0.1", forRemoval = true)
@SuppressWarnings("removal")
@AutoConfiguration(before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ RedisChatMemoryRepository.class, RedisClient.class })
@EnableConfigurationProperties(RedisChatMemoryProperties.class)
public class RedisChatMemoryAutoConfiguration {

	/**
	 * Provides a {@link BackwardCompatibleBindingPostProcessor} that binds legacy
	 * {@code spring.ai.chat.memory.redis} properties to
	 * {@link RedisChatMemoryRepositoryProperties} for smooth transition from the
	 * deprecated {@code spring-ai-autoconfigure-model-chat-memory-redis} module.
	 * @return the backward-compatible binding post-processor
	 * @deprecated for removal in favor of the current
	 * {@code spring.ai.chat.memory.repository.redis} prefix
	 * @since 2.0.1
	 */
	@Deprecated(since = "2.0.1", forRemoval = true)
	@Bean
	@ConditionalOnMissingBean
	BackwardCompatibleBindingPostProcessor backwardCompatibleBindingPostProcessor() {
		return new BackwardCompatibleBindingPostProcessor();
	}

	/**
	 * {@link BeanPostProcessor} that binds legacy {@code spring.ai.chat.memory.redis}
	 * properties to {@link RedisChatMemoryRepositoryProperties} for smooth transition
	 * from the deprecated {@code spring-ai-autoconfigure-model-chat-memory-redis} module.
	 * This ensures properties configured under the legacy prefix are still applied even
	 * when {@link EnableConfigurationProperties} has already bound the current
	 * {@code spring.ai.chat.memory.repository.redis} prefix.
	 *
	 * @deprecated for removal in favor of the current
	 * {@code spring.ai.chat.memory.repository.redis} prefix
	 * @since 2.0.1
	 */
	@Deprecated(since = "2.0.1", forRemoval = true)
	static class BackwardCompatibleBindingPostProcessor implements BeanPostProcessor, EnvironmentAware {

		private @Nullable Environment environment;

		@Override
		public void setEnvironment(Environment environment) {
			Assert.notNull(environment, "Environment must not be null");
			this.environment = environment;
		}

		@Override
		public @Nullable Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
			if (bean instanceof RedisChatMemoryRepositoryProperties properties) {
				Assert.notNull(this.environment, "Environment must not be null");
				// Fallback to legacy prefix
				Binder binder = Binder.get(this.environment);
				binder.bind(RedisChatMemoryProperties.CONFIG_PREFIX, Bindable.ofInstance(properties));
			}
			return bean;
		}

	}

}
