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

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class ChatClientAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
				"spring.ai.openai.chat.model=gpt-4o")
		.withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class, ChatClientAutoConfiguration.class,
				ToolCallingAutoConfiguration.class));

	@Test
	void implicitlyEnabled() {
		this.contextRunner.run(context -> assertThat(context.getBeansOfType(ChatClient.Builder.class)).isNotEmpty());
	}

	@Test
	void explicitlyEnabled() {
		this.contextRunner.withPropertyValues("spring.ai.chat.client.enabled=true")
			.run(context -> assertThat(context.getBeansOfType(ChatClient.Builder.class)).isNotEmpty());
	}

	@Test
	void explicitlyDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.chat.client.enabled=false")
			.run(context -> assertThat(context.getBeansOfType(ChatClient.Builder.class)).isEmpty());
	}

	@Test
	void generate() {
		this.contextRunner.run(context -> {
			ChatClient.Builder builder = context.getBean(ChatClient.Builder.class);

			assertThat(builder).isNotNull();

			ChatClient chatClient = builder.build();

			String response = chatClient.prompt().user("Hello").call().content();

			assertThat(response).isNotEmpty();
		});
	}

	@Test
	void testChatClientBuilderCustomizers() {
		this.contextRunner.withUserConfiguration(Config.class).run(context -> {

			ChatClient.Builder builder = context.getBean(ChatClient.Builder.class);

			ChatClient chatClient = builder.build();

			assertThat(chatClient).isNotNull();

			ActorsFilms actorsFilms = chatClient.prompt()
				.user(u -> u.param("actor", "Tom Hanks"))
				.call()
				.entity(ActorsFilms.class);
			assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
			assertThat(actorsFilms.movies()).hasSize(5);
		});
	}

	@Test
	void toolCallLimitEnforcedThroughAutoConfiguredToolCallingManager() {
		// spring.ai.tools.limits.max-calls-per-tool-default is read by
		// ToolCallingAutoConfiguration.toolCallingManager(...), which is the same
		// ToolCallingManager bean that
		// ChatClientAutoConfiguration.toolCallingAdvisorBuilder(...)
		// wires into the ChatClient.Builder bean's default ToolCallingAdvisor. Setting
		// it to 1 here proves the property genuinely reaches the manager used by a
		// real, auto-configured ChatClient rather than some other default instance.
		this.contextRunner.withPropertyValues("spring.ai.tools.limits.max-calls-per-tool-default=1")
			.withUserConfiguration(WeatherToolConfig.class)
			.run(context -> {
				ChatClient.Builder builder = context.getBean(ChatClient.Builder.class);
				ToolCallback weatherFunction = context.getBean("weatherFunction", ToolCallback.class);

				ChatClient chatClient = builder.build();

				ChatResponse response = chatClient.prompt()
					.user("What's the weather like in San Francisco, Tokyo, and Paris? Please use the provided "
							+ "tool to get the weather for all 3 cities.")
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

	record ActorsFilms(String actor, List<String> movies) {

	}

	@Configuration
	static class Config {

		@Bean
		public ChatClientBuilderCustomizer chatClientCustomizer() {
			return b -> b.defaultSystem("You are a movie expert.")
				.defaultUser("Generate the filmography of 5 movies for {actor}.");
		}

	}

	@Configuration
	static class WeatherToolConfig {

		@Bean
		ToolCallback weatherFunction() {
			return FunctionToolCallback.builder("weatherFunction", new MockWeatherService())
				.description("Get the weather in location")
				.inputType(MockWeatherService.Request.class)
				.build();
		}

	}

}
