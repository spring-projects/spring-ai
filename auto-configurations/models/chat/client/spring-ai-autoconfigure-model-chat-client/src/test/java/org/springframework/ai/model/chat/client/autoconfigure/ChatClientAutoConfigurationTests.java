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

package org.springframework.ai.model.chat.client.autoconfigure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Content;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Filip Hrisafov
 */
@ExtendWith(OutputCaptureExtension.class)
class ChatClientAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatClientAutoConfiguration.class))
		.withUserConfiguration(MockConfig.class);

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
			ChatModel chatModel = context.getBean(ChatModel.class);

			ChatResponse response = ChatResponse.builder()
				.generations(List.of(new Generation(new AssistantMessage("Test"))))
				.build();
			when(chatModel.call(any(Prompt.class))).thenReturn(response);

			ChatResponse chatResponse = chatClient.prompt().user("Hello").call().chatResponse();
			assertThat(chatResponse).isSameAs(response);
		});
	}

	@Test
	void testChatClientCustomizers() {
		this.contextRunner.withUserConfiguration(Config.class).run(context -> {

			ChatClient.Builder builder = context.getBean(ChatClient.Builder.class);

			ChatClient chatClient = builder.build();

			assertThat(chatClient).isNotNull();

			ChatModel chatModel = context.getBean(ChatModel.class);

			ChatResponse response = ChatResponse.builder()
				.generations(List.of(new Generation(new AssistantMessage("Test"))))
				.build();
			when(chatModel.call(any(Prompt.class))).thenReturn(response);
			chatClient.prompt().user(u -> u.param("actor", "Tom Hanks")).call().chatResponse();

			ArgumentCaptor<Prompt> promptArgument = ArgumentCaptor.forClass(Prompt.class);

			verify(chatModel).call(promptArgument.capture());

			Prompt prompt = promptArgument.getValue();
			assertThat(prompt.getInstructions()).extracting(Message::getMessageType, Content::getText)
				.containsExactly(tuple(MessageType.SYSTEM, "You are a movie expert."),
						tuple(MessageType.USER, "Generate the filmography of 5 movies for Tom Hanks."));
		});
	}

	@Test
	void withMultipleChatModels() {
		this.contextRunner.withUserConfiguration(SecondChatModelConfig.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBeansOfType(ChatClient.Builder.class)).isEmpty();
		});
	}

	record ActorsFilms(String actor, List<String> movies) {

	}

	@Configuration
	static class MockConfig {

		@Bean
		ChatModel chatModel() {
			return mock(ChatModel.class);
		}

	}

	@Configuration
	static class SecondChatModelConfig {

		@Bean
		ChatModel secondChatModel() {
			return mock(ChatModel.class);
		}

	}

	@Configuration
	static class Config {

		@Bean
		public ChatClientCustomizer chatClientCustomizer() {
			return b -> b.defaultSystem("You are a movie expert.")
				.defaultUser("Generate the filmography of 5 movies for {actor}.");
		}

	}

}
