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

package org.springframework.ai.ollama;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.ollama.management.PullModelStrategy;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Nicolas Krier
 */
@SpringBootTest
class OllamaChatClientIT extends BaseOllamaIT {

	private static final String MODEL = "ministral-3:3b";

	@Autowired
	private ChatModel chatModel;

	@Test
	void callEntityWithUserProviderStructuredOutputAndValidateSchema() {
		var person = ChatClient.create(this.chatModel)
			.prompt("Who is the 9th president of French 5th Republic?")
			.call()
			.entity(Person.class, entityParamSpec -> entityParamSpec.useProviderStructuredOutput().validateSchema());
		assertThat(person).isNotNull();
		assertThat(person.lastName()).isEqualTo("Macron");
		assertThat(person.firstName()).isEqualTo("Emmanuel");
	}

	record Person(Civility civility, @NotBlank String firstName, @NotBlank String lastName,
			@Nullable @Size(min = 1, max = 50) String nickName, @Email String email, @PositiveOrZero int age,
			@NotEmpty List<@NotBlank String> nationalities) {
		enum Civility {

			MISTER, MADAM

		}
	}

	@SpringBootConfiguration
	static class TestConfiguration {

		@Bean
		OllamaApi ollamaApi() {
			return initializeOllama(MODEL);
		}

		@Bean
		OllamaChatModel ollamaChat(OllamaApi ollamaApi) {
			return OllamaChatModel.builder()
				.ollamaApi(ollamaApi)
				.options(OllamaChatOptions.builder().model(MODEL).temperature(0.0).build())
				.modelManagementOptions(
						ModelManagementOptions.builder().pullModelStrategy(PullModelStrategy.WHEN_MISSING).build())
				.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
				.build();
		}

	}

}
