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

package org.springframework.ai.model.openai.autoconfigure;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Issam El-atif
 * @author Yanming Zhou
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class ChatClientAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(ChatClientAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.api-key=" + System.getenv("OPENAI_API_KEY"),
				"spring.ai.openai.chat.options.model=gpt-4o")
		.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class,
				ChatClientAutoConfiguration.class));

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
	void multipleModelsEnabled() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(AnotherAiChatAutoConfiguration.class))
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
			logger.info("Response: " + response);
		});
	}

	@Test
	void testChatClientCustomizers() {
		this.contextRunner.withUserConfiguration(Config.class).run(context -> {

			ChatClient.Builder builder = context.getBean(ChatClient.Builder.class);

			ChatClient chatClient = builder.build();

			assertThat(chatClient).isNotNull();

			ActorsFilms actorsFilms = chatClient.prompt()
				.user(u -> u.param("actor", "Tom Hanks"))
				.call()
				.entity(ActorsFilms.class);

			logger.info("" + actorsFilms);
			assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
			assertThat(actorsFilms.movies()).hasSize(5);
		});
	}

	record ActorsFilms(String actor, List<String> movies) {

	}

	@Configuration
	static class Config {

		@Bean
		public ChatClientCustomizer chatClientCustomizer() {
			return b -> b.defaultSystem("You are a movie expert.")
				.defaultUser("Generate the filmography of 5 movies for {actor}.");
		}

	}

	@AutoConfiguration(before = ChatClientAutoConfiguration.class)
	static class AnotherAiChatAutoConfiguration {

		@Bean
		ChatModel anotherAiChatModel() {
			return Mockito.mock(ChatModel.class);
		}

	}

}
