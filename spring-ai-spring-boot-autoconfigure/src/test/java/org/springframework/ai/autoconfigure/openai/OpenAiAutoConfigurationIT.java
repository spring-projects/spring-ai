/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.openai;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;
import org.springframework.ai.openai.OpenAiAudioSpeechClient;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionClient;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OpenAiAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"))
		.withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
				RestClientAutoConfiguration.class, OpenAiAutoConfiguration.class));

	@Test
	void generate() {
		contextRunner.run(context -> {
			OpenAiChatClient client = context.getBean(OpenAiChatClient.class);
			String response = client.call("Hello");
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void transcribe() {
		contextRunner.run(context -> {
			OpenAiAudioTranscriptionClient client = context.getBean(OpenAiAudioTranscriptionClient.class);
			Resource audioFile = new ClassPathResource("/speech/jfk.flac");
			String response = client.call(audioFile);
			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void synthesize() {
		contextRunner.run(context -> {
			OpenAiAudioSpeechClient client = context.getBean(OpenAiAudioSpeechClient.class);
			byte[] response = client.call("H");
			assertThat(response).isNotNull();
			assertThat(verifyMp3FrameHeader(response))
				.withFailMessage("Expected MP3 frame header to be present in the response, but it was not found.")
				.isTrue();
			assertThat(response.length).isNotEqualTo(0);

			logger.info("Response: " + Arrays.toString(response));
		});
	}

	public boolean verifyMp3FrameHeader(byte[] audioResponse) {
		// Check if the response is null or too short to contain a frame header
		if (audioResponse == null || audioResponse.length < 2) {
			return false;
		}
		// Check for the MP3 frame header
		// 0xFFE0 is the sync word for an MP3 frame (11 bits set to 1 followed by 3 bits
		// set to 0)
		return (audioResponse[0] & 0xFF) == 0xFF && (audioResponse[1] & 0xE0) == 0xE0;
	}

	@Test
	void generateStreaming() {
		contextRunner.run(context -> {
			OpenAiChatClient client = context.getBean(OpenAiChatClient.class);
			Flux<ChatResponse> responseFlux = client.stream(new Prompt(new UserMessage("Hello")));
			String response = responseFlux.collectList().block().stream().map(chatResponse -> {
				return chatResponse.getResults().get(0).getOutput().getContent();
			}).collect(Collectors.joining());

			assertThat(response).isNotEmpty();
			logger.info("Response: " + response);
		});
	}

	@Test
	void embedding() {
		contextRunner.run(context -> {
			OpenAiEmbeddingClient embeddingClient = context.getBean(OpenAiEmbeddingClient.class);

			EmbeddingResponse embeddingResponse = embeddingClient
				.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
			assertThat(embeddingResponse.getResults()).hasSize(2);
			assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
			assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
			assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

			assertThat(embeddingClient.dimensions()).isEqualTo(1536);
		});
	}

	@Test
	void generateImage() {
		contextRunner.withPropertyValues("spring.ai.openai.image.options.size=1024x1024").run(context -> {
			OpenAiImageClient client = context.getBean(OpenAiImageClient.class);
			ImageResponse imageResponse = client.call(new ImagePrompt("forest"));
			assertThat(imageResponse.getResults()).hasSize(1);
			assertThat(imageResponse.getResult().getOutput().getUrl()).isNotEmpty();
			logger.info("Generated image: " + imageResponse.getResult().getOutput().getUrl());
		});
	}

}
