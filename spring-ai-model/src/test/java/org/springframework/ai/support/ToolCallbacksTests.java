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

package org.springframework.ai.support;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolCallbacks}.
 *
 * @author zxuhan7
 */
class ToolCallbacksTests {

	@Test
	void fromConfigurationClassInstance() {
		try (var context = new AnnotationConfigApplicationContext(WeatherConfiguration.class)) {
			ToolCallback[] toolCallbacks = context.getBean(ToolCallbackProvider.class).getToolCallbacks();

			assertThat(toolCallbacks).hasSize(1);
			assertThat(toolCallbacks[0].getToolDefinition().name()).isEqualTo("weather");
			assertThat(toolCallbacks[0].call("{\"city\":\"Paris\"}")).isEqualTo("\"Paris is cloudy\"");
		}
	}

	@Test
	void fromClassBasedProxy() {
		ProxyFactory proxyFactory = new ProxyFactory(new WeatherTools());
		proxyFactory.setProxyTargetClass(true);

		ToolCallback[] toolCallbacks = ToolCallbacks.from(proxyFactory.getProxy());

		assertThat(toolCallbacks).hasSize(1);
		assertThat(toolCallbacks[0].call("{\"city\":\"Paris\"}")).isEqualTo("\"Paris is cloudy\"");
	}

	// Package-private and in a different package than MethodToolCallback, so that
	// calling the tools requires making them accessible

	@Configuration
	static class WeatherConfiguration {

		@Bean
		ToolCallbackProvider toolCallbackProvider() {
			return ToolCallbackProvider.from(ToolCallbacks.from(this));
		}

		@Tool(description = "Get the weather in a city")
		public String weather(String city) {
			return city + " is cloudy";
		}

	}

	static class WeatherTools {

		@Tool(description = "Get the weather in a city")
		public String weather(String city) {
			return city + " is cloudy";
		}

	}

}
