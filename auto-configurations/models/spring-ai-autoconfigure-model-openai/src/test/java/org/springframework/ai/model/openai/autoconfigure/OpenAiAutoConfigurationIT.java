/*
 * Copyright 2023-2024 the original author or authors.
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.utils.SpringAiTestAutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".*")
public class OpenAiAutoConfigurationIT {

	private static final Log logger = LogFactory.getLog(OpenAiAutoConfigurationIT.class);

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withPropertyValues("spring.ai.openai.apiKey=" + System.getenv("OPENAI_API_KEY"));

	@Test
	void chatCall() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
				String response = chatModel.call("Hello");
				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void chatCallAudioResponse() {
		this.contextRunner
			.withPropertyValues(
					"spring.ai.openai.chat.options.model=" + OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW.getValue(),
					"spring.ai.openai.chat.options.output-modalities=text,audio",
					"spring.ai.openai.chat.options.output-audio.voice=ALLOY",
					"spring.ai.openai.chat.options.output-audio.format=WAV")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

				ChatResponse response = chatModel
					.call(new Prompt(new UserMessage("Tell me joke about Spring Framework")));
				assertThat(response).isNotNull();
				logger.info("Response: " + response);
				// AudioPlayer.play(response.getResult().getOutput().getMedia().get(0).getDataAsByteArray());
			});
	}

	@Test
	void transcribe() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioTranscriptionAutoConfiguration.class))
			.run(context -> {
				OpenAiAudioTranscriptionModel transcriptionModel = context.getBean(OpenAiAudioTranscriptionModel.class);
				Resource audioFile = new ClassPathResource("/speech/jfk.flac");
				String response = transcriptionModel.call(audioFile);
				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void speech() {
		this.contextRunner
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiAudioSpeechAutoConfiguration.class))
			.run(context -> {
				OpenAiAudioSpeechModel speechModel = context.getBean(OpenAiAudioSpeechModel.class);
				byte[] response = speechModel.call("H");
				assertThat(response).isNotNull();
				assertThat(verifyMp3FrameHeader(response))
					.withFailMessage("Expected MP3 frame header to be present in the response, but it was not found.")
					.isTrue();
				assertThat(response.length).isNotEqualTo(0);

				logger.debug("Response: " + Arrays.toString(response));
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
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);
				Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));
				String response = responseFlux.collectList()
					.block()
					.stream()
					.map(chatResponse -> chatResponse.getResults().get(0).getOutput().getText())
					.collect(Collectors.joining());

				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void streamingWithTokenUsage() {
		this.contextRunner.withPropertyValues("spring.ai.openai.chat.options.stream-usage=true")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiChatAutoConfiguration.class))
			.run(context -> {
				OpenAiChatModel chatModel = context.getBean(OpenAiChatModel.class);

				Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(new UserMessage("Hello")));

				Usage[] streamingTokenUsage = new Usage[1];
				String response = responseFlux.collectList().block().stream().map(chatResponse -> {
					streamingTokenUsage[0] = chatResponse.getMetadata().getUsage();
					return (chatResponse.getResult() != null) ? chatResponse.getResult().getOutput().getText() : "";
				}).collect(Collectors.joining());

				assertThat(streamingTokenUsage[0].getPromptTokens()).isGreaterThan(0);
				assertThat(streamingTokenUsage[0].getCompletionTokens()).isGreaterThan(0);
				assertThat(streamingTokenUsage[0].getTotalTokens()).isGreaterThan(0);

				assertThat(response).isNotEmpty();
				logger.info("Response: " + response);
			});
	}

	@Test
	void embedding() {
		this.contextRunner.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiEmbeddingAutoConfiguration.class))
			.run(context -> {
				OpenAiEmbeddingModel embeddingModel = context.getBean(OpenAiEmbeddingModel.class);

				EmbeddingResponse embeddingResponse = embeddingModel
					.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
				assertThat(embeddingResponse.getResults()).hasSize(2);
				assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
				assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
				assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

				assertThat(embeddingModel.dimensions()).isEqualTo(1536);
			});
	}

	@Test
	void generateImage() {
		this.contextRunner.withPropertyValues("spring.ai.openai.image.options.size=1024x1024")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				OpenAiImageModel imageModel = context.getBean(OpenAiImageModel.class);
				ImageResponse imageResponse = imageModel.call(new ImagePrompt("forest"));
				assertThat(imageResponse.getResults()).hasSize(1);
				assertThat(imageResponse.getResult().getOutput().getUrl()).isNotEmpty();
				logger.info("Generated image: " + imageResponse.getResult().getOutput().getUrl());
			});
	}

	@Test
	void generateImageWithModel() {
		// The 256x256 size is supported by dall-e-2, but not by dall-e-3.
		this.contextRunner
			.withPropertyValues("spring.ai.openai.image.options.model=dall-e-2",
					"spring.ai.openai.image.options.size=256x256")
			.withConfiguration(SpringAiTestAutoConfigurations.of(OpenAiImageAutoConfiguration.class))
			.run(context -> {
				OpenAiImageModel imageModel = context.getBean(OpenAiImageModel.class);
				ImageResponse imageResponse = imageModel.call(new ImagePrompt("forest"));
				assertThat(imageResponse.getResults()).hasSize(1);
				assertThat(imageResponse.getResult().getOutput().getUrl()).isNotEmpty();
				logger.info("Generated image: " + imageResponse.getResult().getOutput().getUrl());
			});
	}

}
