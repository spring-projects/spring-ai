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

package org.springframework.ai.model.openai.autoconfigure;

import java.util.stream.Collectors;

import com.openai.models.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
/**
 * @author Sebastien Deleuze
 */
public class OpenAiFunctionCallback2IT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
		.withUserConfiguration(Config.class);

	@Test
	void functionCallTest() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.chat.temperature=0.1",
					"spring.ai.openai.chat.model=" + ChatModel.GPT_4O_MINI.asString())
			.run(context -> {

				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

			// @formatter:off
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			ToolCallback weatherFunctionInfo = context.getBean("weatherFunctionInfo", ToolCallback.class);

			ChatClient chatClient = ChatClient.builder(chatModel)
				.defaultAdvisors(ToolCallAdvisor.builder().toolCallingManager(toolCallingManager).build())
				.defaultTools(weatherFunctionInfo)
				.defaultUser(u -> u.text("What's the weather like in {cities}? Please use the provided tools to get the weather for all 3 cities."))
				.build();

			String content = chatClient.prompt()
				.user(u -> u.param("cities", "San Francisco, Tokyo, Paris"))
				.call().content();
			// @formatter:on

				assertThat(content).contains("30", "10", "15");
			});
	}

	@Test
	void streamFunctionCallTest() {
		this.contextRunner
			.withPropertyValues("spring.ai.openai.chat.temperature=0.2",
					"spring.ai.openai.chat.model=" + ChatModel.GPT_4O_MINI.asString())
			.run(context -> {

				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
				ToolCallback weatherFunctionInfo = context.getBean("weatherFunctionInfo", ToolCallback.class);

			// @formatter:off
			ToolCallingManager toolCallingManager = context.getBean(ToolCallingManager.class);
			String content = ChatClient.builder(chatModel)
				.defaultAdvisors(ToolCallAdvisor.builder().toolCallingManager(toolCallingManager).build())
				.build().prompt()
				.tools(weatherFunctionInfo)
				.user("What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided tools to get the weather for all 3 cities.")
				.stream().content()
				.collectList().block().stream().collect(Collectors.joining());
			// @formatter:on

				assertThat(content).contains("30", "10", "15");
			});
	}

	@Configuration
	static class Config {

		@Bean
		public ToolCallback weatherFunctionInfo() {

			return FunctionToolCallback.builder("WeatherInfo", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build();
		}

	}

}
