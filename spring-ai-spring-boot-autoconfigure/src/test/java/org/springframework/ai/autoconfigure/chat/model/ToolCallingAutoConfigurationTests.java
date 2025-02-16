/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.autoconfigure.chat.model;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolCallingAutoConfiguration}.
 *
 * @author Thomas Vitale
 * @author Christian Tzolov
 */
class ToolCallingAutoConfigurationTests {

	@Test
	void beansAreCreated() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.run(context -> {
				var toolCallbackResolver = context.getBean(ToolCallbackResolver.class);
				assertThat(toolCallbackResolver).isInstanceOf(DelegatingToolCallbackResolver.class);

				var toolExecutionExceptionProcessor = context.getBean(ToolExecutionExceptionProcessor.class);
				assertThat(toolExecutionExceptionProcessor).isInstanceOf(DefaultToolExecutionExceptionProcessor.class);

				var toolCallingManager = context.getBean(ToolCallingManager.class);
				assertThat(toolCallingManager).isInstanceOf(DefaultToolCallingManager.class);
			});
	}

	@Test
	void resolveMultipleFuncitonAndToolCallbacks() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallbackResolver = context.getBean(ToolCallbackResolver.class);
				assertThat(toolCallbackResolver).isInstanceOf(DelegatingToolCallbackResolver.class);

				assertThat(toolCallbackResolver.resolve("getForecast")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getForecast").getName()).isEqualTo("getForecast");

				assertThat(toolCallbackResolver.resolve("getAlert")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getAlert").getName()).isEqualTo("getAlert");

				assertThat(toolCallbackResolver.resolve("weatherFunction1")).isNotNull();
				assertThat(toolCallbackResolver.resolve("weatherFunction1").getName()).isEqualTo("weatherFunction1");

				assertThat(toolCallbackResolver.resolve("getCurrentWeather3")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getCurrentWeather3").getName())
					.isEqualTo("getCurrentWeather3");

				assertThat(toolCallbackResolver.resolve("getCurrentWeather4")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getCurrentWeather4").getName())
					.isEqualTo("getCurrentWeather4");

				assertThat(toolCallbackResolver.resolve("getCurrentWeather5")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getCurrentWeather5").getName())
					.isEqualTo("getCurrentWeather5");
			});
	}

	static class WeatherService {

		@Tool(description = "Get the weather in location. Return temperature in 36°F or 36°C format.")
		public String getForecast(String location) {
			return "30";
		}

		@Tool(description = "Get the weather in location. Return temperature in 36°F or 36°C format.")
		public String getForecast2(String location) {
			return "30";
		}

		public String getAlert(String usState) {
			return "Alert";
		}

	}

	@Configuration
	static class Config {

		// Note: Currently we do not have ToolCallbackResolver implementation that can
		// resolve the ToolCallback from the Tool annotation.
		// Therefore we need to provide the ToolCallback instances explicitly using the
		// ToolCallbacks.from(...) utility method.
		@Bean
		public ToolCallbackProvider toolCallbacks() {
			return MethodToolCallbackProvider.builder().toolObjects(new WeatherService()).build();
		}

		public record Request(String location) {
		}

		public record Response(String temperature) {
		}

		@Bean
		@Description("Get the weather in location. Return temperature in 36°F or 36°C format.")
		public Function<Request, Response> weatherFunction1() {
			return request -> new Response("30");
		}

		@Bean
		public FunctionCallback functionCallbacks3() {
			return FunctionCallback.builder()
				.function("getCurrentWeather3", (Request request) -> "15.0°C")
				.description("Gets the weather in location")
				.inputType(Request.class)
				.build();
		}

		@Bean
		public FunctionCallback functionCallbacks4() {
			return FunctionCallback.builder()
				.function("getCurrentWeather4", (Request request) -> "15.0°C")
				.description("Gets the weather in location")
				.inputType(Request.class)
				.build();

		}

		@Bean
		public ToolCallback toolCallbacks5() {
			return FunctionToolCallback.builder("getCurrentWeather5", (Request request) -> "15.0°C")
				.description("Gets the weather in location")
				.inputType(Request.class)
				.build();

		}

		@Bean
		public ToolCallbackProvider blabla() {
			return new StaticToolCallbackProvider(
					FunctionToolCallback.builder("getCurrentWeather5", (Request request) -> "15.0°C")
						.description("Gets the weather in location")
						.inputType(Request.class)
						.build());

		}

		@Bean
		public ToolCallback toolCallbacks6() {
			var toolMethod = ReflectionUtils.findMethod(WeatherService.class, "getAlert", String.class);
			return MethodToolCallback.builder()
				.toolDefinition(ToolDefinition.builder(toolMethod).build())
				.toolMethod(toolMethod)
				.toolObject(new WeatherService())
				.build();
		}

	}

}
