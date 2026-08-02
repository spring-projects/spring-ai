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

import java.util.stream.Collectors;

import com.anthropic.models.messages.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for tool calling via Spring bean-registered function callbacks using
 * user-controlled tool execution with {@link ToolCallingAdvisor}.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class FunctionCallWithFunctionBeanIT {

	private static final String WEATHER_TOOL_DESCRIPTION = "Get the weather in location. Return temperature in 36°F or 36°C format.";

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
				ToolCallback weatherFunction = context.getBean("weatherFunction", ToolCallback.class);
				ToolCallback weatherFunction3 = context.getBean("weatherFunction3", ToolCallback.class);

				String content = ChatClient.create(chatModel)
					.prompt()
					.advisors(ToolCallingAdvisor.builder().build())
					.user("What's the weather like in San Francisco, in Paris, France and in Tokyo, Japan?"
							+ " Return the temperature in Celsius.")
					.tools(weatherFunction)
					.call()
					.content();
				assertThat(content).contains("30", "10", "15");

				content = ChatClient.create(chatModel)
					.prompt()
					.advisors(ToolCallingAdvisor.builder().build())
					.user("What's the weather like in San Francisco, in Paris, France and in Tokyo, Japan?"
							+ " Return the temperature in Celsius.")
					.tools(weatherFunction3)
					.call()
					.content();
				assertThat(content).contains("30", "10", "15");
			});
	}

	@Test
	void streamFunctionCallTest() {
		this.contextRunner.withPropertyValues("spring.ai.anthropic.chat.model=" + Model.CLAUDE_HAIKU_4_5.asString())
			.run(context -> {

				AnthropicChatModel chatModel = context.getBean(AnthropicChatModel.class);
				ToolCallback weatherFunction = context.getBean("weatherFunction", ToolCallback.class);
				ToolCallback weatherFunction3 = context.getBean("weatherFunction3", ToolCallback.class);

				Flux<String> response = ChatClient.create(chatModel)
					.prompt()
					.advisors(ToolCallingAdvisor.builder().build())
					.user("What's the weather like in San Francisco, in Paris, France and in Tokyo, Japan?"
							+ " Return the temperature in Celsius.")
					.tools(weatherFunction)
					.stream()
					.content();

				String content = response.collectList().block().stream().collect(Collectors.joining());
				assertThat(content).contains("30", "10", "15");

				response = ChatClient.create(chatModel)
					.prompt()
					.advisors(ToolCallingAdvisor.builder().build())
					.user("What's the weather like in San Francisco, in Paris, France and in Tokyo, Japan?"
							+ " Return the temperature in Celsius.")
					.tools(weatherFunction3)
					.stream()
					.content();

				content = response.collectList().block().stream().collect(Collectors.joining());
				assertThat(content).contains("30", "10", "15");
			});
	}

	@Test
	void toolCallLimitEnforcedThroughAutoConfiguredToolCallingManager() {
		// spring.ai.tools.limits.max-calls-per-tool is read by
		// ToolCallingAutoConfiguration.toolCallingManager(...). This module has no
		// ChatClientAutoConfiguration on its test classpath, so the real bean is
		// fetched explicitly here and wired into the ToolCallingAdvisor used for the
		// call, proving the property reaches the manager that actually executes tool
		// calls, rather than some other default instance.
		this.contextRunner
			.withPropertyValues("spring.ai.anthropic.chat.model=" + Model.CLAUDE_HAIKU_4_5.asString(),
					"spring.ai.tools.limits.max-calls-per-tool=1")
			.run(context -> {

				AnthropicChatModel chatModel = context.getBean(AnthropicChatModel.class);
				ToolCallback weatherFunction = context.getBean("weatherFunction", ToolCallback.class);
				ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);

				ChatResponse response = ChatClient.create(chatModel)
					.prompt()
					.advisors(ToolCallingAdvisor.builder().toolCallingManager(toolCallingManager).build())
					.user("What's the weather like in San Francisco, in Paris, France and in Tokyo, Japan?"
							+ " Call the weather tool once for each of the 3 locations.")
					.tools(weatherFunction)
					.call()
					.chatResponse();

				String allResults = response.getResults()
					.stream()
					.map(Generation::getOutput)
					.map(AssistantMessage::getText)
					.collect(Collectors.joining("\n"));

				// Only the first call to weatherFunction is allowed; the rest are
				// rejected with the message DefaultToolCallingManager synthesizes when
				// a configured limit is exceeded.
				assertThat(allResults).contains("limit");
			});
	}

	@Configuration
	static class Config {

		@Bean
		ToolCallback weatherFunction() {
			return FunctionToolCallback.builder("weatherFunction", new MockWeatherService())
				.description(WEATHER_TOOL_DESCRIPTION)
				.inputType(MockWeatherService.Request.class)
				.build();
		}

		@Bean
		ToolCallback weatherFunction3() {
			MockWeatherService weatherService = new MockWeatherService();
			return FunctionToolCallback.builder("weatherFunction3", weatherService::apply)
				.description(WEATHER_TOOL_DESCRIPTION)
				.inputType(MockWeatherService.Request.class)
				.build();
		}

	}

}
