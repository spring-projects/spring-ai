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

package org.springframework.ai.openai.api;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.Embedding;
import org.springframework.ai.openai.api.OpenAiApi.EmbeddingList;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiApiIT {

	OpenAiApi openAiApi = OpenAiApi.builder().apiKey(System.getenv("OPENAI_API_KEY")).build();

	@Test
	void chatCompletionEntity() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(
				new ChatCompletionRequest(List.of(chatCompletionMessage), "gpt-3.5-turbo", 0.8, false));

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
	}

	@Test
	void chatCompletionStream() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage("Hello world", Role.USER);
		Flux<ChatCompletionChunk> response = this.openAiApi.chatCompletionStream(
				new ChatCompletionRequest(List.of(chatCompletionMessage), "gpt-3.5-turbo", 0.8, true));

		assertThat(response).isNotNull();
		assertThat(response.collectList().block()).isNotNull();
	}

	@Test
	@Disabled("The reasoning_effort option is only available in o1 models.")
	void validateReasoningTokens() {
		ChatCompletionMessage userMessage = new ChatCompletionMessage(
				"If a train travels 100 miles in 2 hours, what is its average speed?", ChatCompletionMessage.Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(userMessage), "o1", null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, false, null, null, null, null,
				null, null, null, "low", null);
		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();

		OpenAiApi.Usage.CompletionTokenDetails completionTokenDetails = response.getBody()
			.usage()
			.completionTokenDetails();
		assertThat(completionTokenDetails).isNotNull();
		assertThat(completionTokenDetails.reasoningTokens()).isPositive();
	}

	@Test
	void embeddings() {
		ResponseEntity<EmbeddingList<Embedding>> response = this.openAiApi
			.embeddings(new OpenAiApi.EmbeddingRequest<String>("Hello world"));

		assertThat(response).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).embedding()).hasSize(1536);
	}

	@Test
	void inputAudio() throws IOException {
		var audioData = new ClassPathResource("speech1.mp3").getContentAsByteArray();
		List<ChatCompletionMessage.MediaContent> content = List
			.of(new ChatCompletionMessage.MediaContent("What is this recording about?"),
					new ChatCompletionMessage.MediaContent(new ChatCompletionMessage.MediaContent.InputAudio(
							Base64.getEncoder().encodeToString(audioData),
							ChatCompletionMessage.MediaContent.InputAudio.Format.MP3)));
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(content, Role.USER);
		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(List.of(chatCompletionMessage),
				OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW.getValue(), 0.0);
		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();

		assertThat(response.getBody().usage().promptTokensDetails().audioTokens()).isGreaterThan(0);
		assertThat(response.getBody().usage().completionTokenDetails().audioTokens()).isEqualTo(0);

		assertThat(response.getBody().choices().get(0).message().content()).containsIgnoringCase("hobbits");
	}

	@Test
	void outputAudio() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(
				"What is the magic spell to make objects fly?", Role.USER);
		ChatCompletionRequest.AudioParameters audioParameters = new ChatCompletionRequest.AudioParameters(
				ChatCompletionRequest.AudioParameters.Voice.NOVA,
				ChatCompletionRequest.AudioParameters.AudioResponseFormat.MP3);
		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(List.of(chatCompletionMessage),
				OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW.getValue(), audioParameters, false);
		ResponseEntity<ChatCompletion> response = this.openAiApi.chatCompletionEntity(chatCompletionRequest);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();

		assertThat(response.getBody().usage().promptTokensDetails().audioTokens()).isEqualTo(0);
		assertThat(response.getBody().usage().completionTokenDetails().audioTokens()).isGreaterThan(0);

		assertThat(response.getBody().choices().get(0).message().audioOutput().data()).isNotNull();
		assertThat(response.getBody().choices().get(0).message().audioOutput().transcript())
			.containsIgnoringCase("leviosa");
	}

	@Test
	void streamOutputAudio() {
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(
				"What is the magic spell to make objects fly?", Role.USER);
		ChatCompletionRequest.AudioParameters audioParameters = new ChatCompletionRequest.AudioParameters(
				ChatCompletionRequest.AudioParameters.Voice.NOVA,
				ChatCompletionRequest.AudioParameters.AudioResponseFormat.MP3);
		ChatCompletionRequest chatCompletionRequest = new ChatCompletionRequest(List.of(chatCompletionMessage),
				OpenAiApi.ChatModel.GPT_4_O_AUDIO_PREVIEW.getValue(), audioParameters, true);

		assertThatThrownBy(() -> this.openAiApi.chatCompletionStream(chatCompletionRequest).collectList().block())
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("400 Bad Request from POST https://api.openai.com/v1/chat/completions");
	}

}
