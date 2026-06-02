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

package org.springframework.ai.model.anthropic.autoconfigure.tool;

import java.util.function.Function;
import java.util.stream.Collectors;

import com.anthropic.models.messages.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.anthropic.autoconfigure.tool.MockWeatherService.Request;
import org.springframework.ai.model.anthropic.autoconfigure.tool.MockWeatherService.Response;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for tool calling via Spring bean-registered function callbacks using
 * user-controlled tool execution with {@link ToolCallAdvisor}.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class FunctionCallWithFunctionBeanIT {

	private static final String WEATHER_TOOL_DESCRIPTION = "Get the weather in location. Return temperature in 36°F or 36°C format.";

	private final Logger logger = LoggerFactory.getLogger(FunctionCallWithFunctionBeanIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.anthropic.api-key=" + System.getenv("ANTHROPIC_API_KEY"))
		.withConfiguration(
				AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {
		this.contextRunner.withPropertyValues("spring.ai.anthropic.chat.model=" + Model.CLAUDE_HAIKU_4_5.asString())
			.run(context -> {

				AnthropicChatModel chatModel = context.getBean(AnthropicChatModel.class);

				ToolCallback weatherToolCallback = buildToolCallback("weatherFunction",
						context.getBean("weatherFunction", Function.class));

				String content = ChatClient.create(chatModel)
					.prompt()
					.advisors(ToolCallAdvisor.builder().build())
					.user("What's the weather like in San Francisco, in Paris, France and in Tokyo, Japan?"
							+ " Return the temperature in Celsius.")
					.tools(t -> t.callbacks(weatherToolCallback))
					.call()
					.content();

				logger.info("Response: {}", content);
				assertThat(content).contains("30", "10", "15");

				ToolCallback weatherToolCallback3 = buildToolCallback("weatherFunction3",
						context.getBean("weatherFunction3", Function.class));

				content = ChatClient.create(chatModel)
					.prompt()
					.advisors(ToolCallAdvisor.builder().build())
					.user("What's the weather like in San Francisco, in Paris, France and in Tokyo, Japan?"
							+ " Return the temperature in Celsius.")
					.tools(t -> t.callbacks(weatherToolCallback3))
					.call()
					.content();

				logger.info("Response: {}", content);
				assertThat(content).contains("30", "10", "15");
			});
	}

	@Test
	void streamFunctionCallTest() {
		this.contextRunner.withPropertyValues("spring.ai.anthropic.chat.model=" + Model.CLAUDE_HAIKU_4_5.asString())
			.run(context -> {

				AnthropicChatModel chatModel = context.getBean(AnthropicChatModel.class);

				ToolCallback weatherToolCallback = buildToolCallback("weatherFunction",
						context.getBean("weatherFunction", Function.class));

				Flux<String> response = ChatClient.create(chatModel)
					.prompt()
					.advisors(ToolCallAdvisor.builder().build())
					.user("What's the weather like in San Francisco, in Paris, France and in Tokyo, Japan?"
							+ " Return the temperature in Celsius.")
					.tools(t -> t.callbacks(weatherToolCallback))
					.stream()
					.content();

				String content = response.collectList().block().stream().collect(Collectors.joining());
				logger.info("Response: {}", content);
				assertThat(content).contains("30", "10", "15");

				ToolCallback weatherToolCallback3 = buildToolCallback("weatherFunction3",
						context.getBean("weatherFunction3", Function.class));

				response = ChatClient.create(chatModel)
					.prompt()
					.advisors(ToolCallAdvisor.builder().build())
					.user("What's the weather like in San Francisco, in Paris, France and in Tokyo, Japan?"
							+ " Return the temperature in Celsius.")
					.tools(t -> t.callbacks(weatherToolCallback3))
					.stream()
					.content();

				content = response.collectList().block().stream().collect(Collectors.joining());
				logger.info("Response: {}", content);
				assertThat(content).contains("30", "10", "15");
			});
	}

	@SuppressWarnings("unchecked")
	private static ToolCallback buildToolCallback(String name, Function<?, ?> fn) {
		return FunctionToolCallback.builder(name, (Function<Request, Response>) fn)
			.description(WEATHER_TOOL_DESCRIPTION)
			.inputType(MockWeatherService.Request.class)
			.build();
	}

	@Configuration
	static class Config {

		@Bean
		@Description("Get the weather in location. Return temperature in 36°F or 36°C format.")
		public Function<Request, Response> weatherFunction() {
			return new MockWeatherService();
		}

		@Bean
		public Function<MockWeatherService.Request, MockWeatherService.Response> weatherFunction3() {
			MockWeatherService weatherService = new MockWeatherService();
			return weatherService::apply;
		}

	}

}
