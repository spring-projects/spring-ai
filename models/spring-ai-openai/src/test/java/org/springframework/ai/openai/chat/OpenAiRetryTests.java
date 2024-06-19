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
package org.springframework.ai.openai.chat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.image.ImageMessage;
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
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class OpenAiRetryTests {

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
			onSuccessRetryCount = context.getRetryCount();
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			onErrorRetryCount = context.getRetryCount();
		}

	}

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
		retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);

		chatModel = new OpenAiChatModel(openAiApi, OpenAiChatOptions.builder().build(), null, retryTemplate);
		embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED,
				OpenAiEmbeddingOptions.builder().build(), retryTemplate);
		audioTranscriptionModel = new OpenAiAudioTranscriptionModel(openAiAudioApi,
				OpenAiAudioTranscriptionOptions.builder()
					.withModel("model")
					.withResponseFormat(TranscriptResponseFormat.JSON)
					.build(),
				retryTemplate);
		imageModel = new OpenAiImageModel(openAiImageApi, OpenAiImageOptions.builder().build(), retryTemplate);
	}

	@Test
	public void openAiChatTransientError() {

		var choice = new ChatCompletion.Choice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", List.of(choice), 666l, "model", null, null,
				new OpenAiApi.Usage(10, 10, 10));

		when(openAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void openAiChatNonTransientError() {
		when(openAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.call(new Prompt("text")));
	}

	@Test
	public void openAiChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", List.of(choice), 666l, "model", null,
				null, null);

		when(openAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(Flux.just(expectedChatCompletion));

		var result = chatModel.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void openAiChatStreamNonTransientError() {
		when(openAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.stream(new Prompt("text")));
	}

	@Test
	public void openAiEmbeddingTransientError() {

		EmbeddingList<Embedding> expectedEmbeddings = new EmbeddingList<>("list",
				List.of(new Embedding(0, List.of(9.9, 8.8))), "model", new OpenAiApi.Usage(10, 10, 10));

		when(openAiApi.embeddings(isA(EmbeddingRequest.class))).thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(List.of(9.9, 8.8));
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void openAiEmbeddingNonTransientError() {
		when(openAiApi.embeddings(isA(EmbeddingRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> embeddingModel
				.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
	}

	@Test
	public void openAiAudioTranscriptionTransientError() {

		var expectedResponse = new StructuredResponse("nl", 6.7f, "Transcription Text", List.of(), List.of());

		when(openAiAudioApi.createTranscription(isA(TranscriptionRequest.class), isA(Class.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedResponse)));

		AudioTranscriptionResponse result = audioTranscriptionModel
			.call(new AudioTranscriptionPrompt(new ClassPathResource("speech/jfk.flac")));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(expectedResponse.text());
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void openAiAudioTranscriptionNonTransientError() {
		when(openAiAudioApi.createTranscription(isA(TranscriptionRequest.class), isA(Class.class)))
				.thenThrow(new RuntimeException("Transient Error 1"));
		assertThrows(RuntimeException.class, () -> audioTranscriptionModel
				.call(new AudioTranscriptionPrompt(new ClassPathResource("speech/jfk.flac"))));
	}

	@Test
	public void openAiImageTransientError() {

		var expectedResponse = new OpenAiImageResponse(678l, List.of(new Data("url678", "b64", "prompt")));

		when(openAiImageApi.createImage(isA(OpenAiImageRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedResponse)));

		var result = imageModel.call(new ImagePrompt(List.of(new ImageMessage("Image Message"))));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getUrl()).isEqualTo("url678");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void openAiImageNonTransientError() {
		when(openAiImageApi.createImage(isA(OpenAiImageRequest.class)))
				.thenThrow(new RuntimeException("Transient Error 1"));
		assertThrows(RuntimeException.class,
				() -> imageModel.call(new ImagePrompt(List.of(new ImageMessage("Image Message")))));
	}

}
