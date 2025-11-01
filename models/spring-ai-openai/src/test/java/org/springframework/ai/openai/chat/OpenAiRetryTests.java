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

package org.springframework.ai.openai.chat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletion;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionFinishReason;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.openai.api.OpenAiApi.Embedding;
import org.springframework.ai.openai.api.OpenAiApi.EmbeddingList;
import org.springframework.ai.openai.api.OpenAiApi.EmbeddingRequest;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.api.OpenAiAudioApi.StructuredResponse;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptResponseFormat;
import org.springframework.ai.openai.api.OpenAiAudioApi.TranscriptionRequest;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.api.OpenAiImageApi.Data;
import org.springframework.ai.openai.api.OpenAiImageApi.OpenAiImageRequest;
import org.springframework.ai.openai.api.OpenAiImageApi.OpenAiImageResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class OpenAiRetryTests {

	private static final Logger logger = LoggerFactory.getLogger(OpenAiRetryTests.class);

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private @Mock OpenAiApi openAiApi;

	private @Mock OpenAiAudioApi openAiAudioApi;

	private @Mock OpenAiImageApi openAiImageApi;

	private OpenAiChatModel chatModel;

	private OpenAiEmbeddingModel embeddingModel;

	private OpenAiAudioTranscriptionModel audioTranscriptionModel;

	private OpenAiImageModel imageModel;

	@BeforeEach
	public void beforeEach() {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.setRetryListener(this.retryListener);

		this.chatModel = OpenAiChatModel.builder()
			.openAiApi(this.openAiApi)
			.defaultOptions(OpenAiChatOptions.builder().build())
			.retryTemplate(this.retryTemplate)
			.build();
		this.embeddingModel = new OpenAiEmbeddingModel(this.openAiApi, MetadataMode.EMBED,
				OpenAiEmbeddingOptions.builder().build(), this.retryTemplate);
		this.audioTranscriptionModel = new OpenAiAudioTranscriptionModel(this.openAiAudioApi,
				OpenAiAudioTranscriptionOptions.builder()
					.model("model")
					.responseFormat(TranscriptResponseFormat.JSON)
					.build(),
				this.retryTemplate);
		this.imageModel = new OpenAiImageModel(this.openAiImageApi, OpenAiImageOptions.builder().build(),
				this.retryTemplate);
	}

	@Test
	public void openAiChatTransientError() {

		var choice = new ChatCompletion.Choice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", List.of(choice), 666L, "model", null, null,
				null, new OpenAiApi.Usage(10, 10, 10));

		given(this.openAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class), any()))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = this.chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.retryCount).isEqualTo(2);
	}

	@Test
	public void openAiChatNonTransientError() {
		given(this.openAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class), any()))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.call(new Prompt("text")));
	}

	@Test
	@Disabled("Currently stream() does not implement retry")
	public void openAiChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", List.of(choice), 666L, "model", null,
				null, null, null);

		given(this.openAiApi.chatCompletionStream(isA(ChatCompletionRequest.class), any()))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(Flux.just(expectedChatCompletion));

		var result = this.chatModel.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.retryCount).isEqualTo(2);
	}

	@Test
	@Disabled("Currently stream() does not implement retry")
	public void openAiChatStreamNonTransientError() {
		given(this.openAiApi.chatCompletionStream(isA(ChatCompletionRequest.class), any()))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.stream(new Prompt("text")).subscribe());
	}

	@Test
	public void openAiEmbeddingTransientError() {

		EmbeddingList<Embedding> expectedEmbeddings = new EmbeddingList<>("list",
				List.of(new Embedding(0, new float[] { 9.9f, 8.8f })), "model", new OpenAiApi.Usage(10, 10, 10));

		given(this.openAiApi.embeddings(isA(EmbeddingRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.retryCount).isEqualTo(2);
	}

	@Test
	public void openAiEmbeddingNonTransientError() {
		given(this.openAiApi.embeddings(isA(EmbeddingRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
	}

	@Test
	public void openAiAudioTranscriptionTransientError() {

		var expectedResponse = new StructuredResponse("nl", 6.7f, "Transcription Text", List.of(), List.of());

		given(this.openAiAudioApi.createTranscription(isA(TranscriptionRequest.class), isA(Class.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedResponse)));

		AudioTranscriptionResponse result = this.audioTranscriptionModel
			.call(new AudioTranscriptionPrompt(new ClassPathResource("speech/jfk.flac")));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(expectedResponse.text());
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.retryCount).isEqualTo(2);
	}

	@Test
	public void openAiAudioTranscriptionNonTransientError() {
		given(this.openAiAudioApi.createTranscription(isA(TranscriptionRequest.class), isA(Class.class)))
			.willThrow(new RuntimeException("Transient Error 1"));
		assertThrows(RuntimeException.class, () -> this.audioTranscriptionModel
			.call(new AudioTranscriptionPrompt(new ClassPathResource("speech/jfk.flac"))));
	}

	@Test
	public void openAiImageTransientError() {

		var expectedResponse = new OpenAiImageResponse(678L, List.of(new Data("url678", "b64", "prompt")));

		given(this.openAiImageApi.createImage(isA(OpenAiImageRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedResponse)));

		var result = this.imageModel
			.call(new ImagePrompt(List.of(new ImageMessage("Image Message")), ImageOptionsBuilder.builder().build()));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getUrl()).isEqualTo("url678");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.retryCount).isEqualTo(2);
	}

	@Test
	public void openAiImageNonTransientError() {
		given(this.openAiImageApi.createImage(isA(OpenAiImageRequest.class)))
			.willThrow(new RuntimeException("Transient Error 1"));
		assertThrows(RuntimeException.class, () -> this.imageModel
			.call(new ImagePrompt(List.of(new ImageMessage("Image Message")), ImageOptionsBuilder.builder().build())));
	}

	private static class TestRetryListener implements RetryListener {

		int retryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public void onRetrySuccess(final RetryPolicy retryPolicy, final Retryable<?> retryable, final Object result) {
			// Count successful retries - we increment when we succeed after a failure
			this.onSuccessRetryCount++;
		}

		@Override
		public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable) {
			this.retryCount++;
		}

	}

}
