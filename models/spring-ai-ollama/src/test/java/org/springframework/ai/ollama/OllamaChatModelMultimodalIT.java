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

package org.springframework.ai.ollama;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
class OllamaChatModelMultimodalIT extends BaseOllamaIT {

	private static final Logger logger = LoggerFactory.getLogger(OllamaChatModelMultimodalIT.class);

	private static final String MODEL = OllamaModel.GEMMA3.getName();

	@Autowired
	private OllamaChatModel chatModel;

	@Test
	void unsupportedMediaType() {
		var imageData = new ClassPathResource("/something.adoc");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see in this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		assertThatThrownBy(() -> this.chatModel.call(new Prompt(List.of(userMessage))))
			.isInstanceOf(RuntimeException.class);
	}

	@Test
	void multiModalityTest() {
		var imageData = new ClassPathResource("/test.png");

		var userMessage = UserMessage.builder()
			.text("Explain what do you see in this picture?")
			.media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageData)))
			.build();

		var response = this.chatModel.call(new Prompt(List.of(userMessage)));

		logger.info(response.getResult().getOutput().getText());
		assertThat(response.getResult().getOutput().getText()).containsAnyOf("bananas", "apple", "bowl", "basket",
				"fruit stand");
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OllamaApi ollamaApi() {
			return initializeOllama(MODEL);
		}

		@Bean
		public OllamaChatModel ollamaChat(OllamaApi ollamaApi) {
			RetryPolicy retryPolicy = RetryPolicy.builder()
				.maxRetries(1)
				.includes(TransientAiException.class)
				.delay(Duration.ofSeconds(1))
				.build();

			RetryTemplate retryTemplate = new RetryTemplate(retryPolicy);
			retryTemplate.setRetryListener(new RetryListener() {

				@Override
				public void onRetryFailure(final RetryPolicy policy, final Retryable<?> retryable,
						final Throwable throwable) {
					logger.warn("Retry error. Retry count:" + (throwable.getSuppressed().length + 1), throwable);
				}
			});
			return OllamaChatModel.builder()
				.ollamaApi(ollamaApi)
				.defaultOptions(OllamaChatOptions.builder().model(MODEL).temperature(0.9).build())
				.retryTemplate(retryTemplate)
				.build();
		}

	}

}
