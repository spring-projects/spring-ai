/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.client.webflux.autoconfigure;

import org.springframework.ai.mcp.client.common.autoconfigure.WebClientFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Default configuration for {@link WebClientFactory}.
 *
 * <p>
 * Provides a default implementation of {@link WebClientFactory} that returns a standard
 * {@link WebClient.Builder} for all connections. This bean is only created if no custom
 * {@link WebClientFactory} bean is provided.
 *
 * @author limch02
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(WebClient.class)
public class DefaultWebClientFactory {

	/**
	 * Creates a default {@link WebClientFactory} implementation.
	 * <p>
	 * This factory returns a standard {@link WebClient.Builder} for all connection names.
	 * Custom implementations can be provided by defining a {@link WebClientFactory} bean.
	 * @return the default WebClientFactory instance
	 */
	@Bean
	@ConditionalOnMissingBean(WebClientFactory.class)
	public WebClientFactory webClientFactory() {
		return new DefaultWebClientFactoryImpl();
	}

	private static class DefaultWebClientFactoryImpl implements WebClientFactory {

		@Override
		public WebClient.Builder create(String connectionName) {
			return WebClient.builder();
		}

	}

}
