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

package org.springframework.ai.model.tool.autoconfigure;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.observation.ToolCallingContentObservationFilter;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.tool.support.ToolDefinitions;
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
	void resolveMultipleFunctionAndToolCallbacks() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallbackResolver = context.getBean(ToolCallbackResolver.class);
				assertThat(toolCallbackResolver).isInstanceOf(DelegatingToolCallbackResolver.class);

				assertThat(toolCallbackResolver.resolve("getForecast")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getForecast").getToolDefinition().name())
					.isEqualTo("getForecast");

				assertThat(toolCallbackResolver.resolve("getAlert")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getAlert").getToolDefinition().name()).isEqualTo("getAlert");

				assertThat(toolCallbackResolver.resolve("weatherFunction1")).isNotNull();
				assertThat(toolCallbackResolver.resolve("weatherFunction1").getToolDefinition().name())
					.isEqualTo("weatherFunction1");

				assertThat(toolCallbackResolver.resolve("getCurrentWeather3")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getCurrentWeather3").getToolDefinition().name())
					.isEqualTo("getCurrentWeather3");

				assertThat(toolCallbackResolver.resolve("getCurrentWeather4")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getCurrentWeather4").getToolDefinition().name())
					.isEqualTo("getCurrentWeather4");

				assertThat(toolCallbackResolver.resolve("getCurrentWeather5")).isNotNull();
				assertThat(toolCallbackResolver.resolve("getCurrentWeather5").getToolDefinition().name())
					.isEqualTo("getCurrentWeather5");
			});
	}

	@Test
	void resolveMissingToolCallbacks() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				var toolCallbackResolver = context.getBean(ToolCallbackResolver.class);
				assertThat(toolCallbackResolver).isInstanceOf(DelegatingToolCallbackResolver.class);

				assertThat(toolCallbackResolver.resolve("NonExisting")).isNull();
			});
	}

	@Test
	void observationFilterDefault() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withUserConfiguration(Config.class)
			.run(context -> {
				assertThat(context).doesNotHaveBean(ToolCallingContentObservationFilter.class);
			});
	}

	@Test
	void observationFilterEnabled() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.withPropertyValues("spring.ai.tools.observations.include-content=true")
			.withUserConfiguration(Config.class)
			.run(context -> {
				assertThat(context).hasSingleBean(ToolCallingContentObservationFilter.class);
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

		@Bean
		@Description("Get the weather in location. Return temperature in 36°F or 36°C format.")
		public Function<Request, Response> weatherFunction1() {
			return request -> new Response("30");
		}

		@Bean
		public ToolCallback functionCallbacks3() {
			return FunctionToolCallback.builder("getCurrentWeather3", (Request request) -> "15.0°C")
				.description("Gets the weather in location")
				.inputType(Request.class)
				.build();
		}

		@Bean
		public ToolCallback functionCallbacks4() {
			return FunctionToolCallback.builder("getCurrentWeather4", (Request request) -> "15.0°C")
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
				.toolDefinition(ToolDefinitions.builder(toolMethod).build())
				.toolMethod(toolMethod)
				.toolObject(new WeatherService())
				.build();
		}

		public record Request(String location) {
		}

		public record Response(String temperature) {
		}

	}

}
