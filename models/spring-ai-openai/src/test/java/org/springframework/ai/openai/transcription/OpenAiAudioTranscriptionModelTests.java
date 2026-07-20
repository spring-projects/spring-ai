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

package org.springframework.ai.openai.transcription;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionStreamEvent;
import com.openai.models.audio.transcriptions.TranscriptionTextDeltaEvent;
import com.openai.services.async.AudioServiceAsync;
import com.openai.services.async.audio.TranscriptionServiceAsync;
import com.openai.services.blocking.AudioService;
import com.openai.services.blocking.audio.TranscriptionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiAudioTranscriptionModel} and
 * {@link OpenAiAudioTranscriptionOptions}.
 *
 * @author Michael Lavelle
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Sebastien Deleuze
 * @author guan xu
 */
class OpenAiAudioTranscriptionModelTests {

	@Test
	void callReturnsTranscriptionText() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Hello, transcribed text").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(client)
			.openAiClientAsync(mock(OpenAIClientAsync.class))
			.build();
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"));
		AudioTranscriptionResponse response = model.call(prompt);

		assertThat(response.getResult().getOutput()).isEqualTo("Hello, transcribed text");
	}

	@Test
	void callWithPrompt() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Hello, this is a test transcription.").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(client)
			.openAiClientAsync(mock(OpenAIClientAsync.class))
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"));
		AudioTranscriptionResponse response = model.call(prompt);

		assertThat(response.getResult().getOutput()).isEqualTo("Hello, this is a test transcription.");
		assertThat(response.getResults()).hasSize(1);
	}

	@Test
	void callWithPromptOptions() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Hello, this is a test transcription with options.").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.temperature(0.5f)
			.responseFormat(AudioResponseFormat.JSON)
			.build();

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(client)
			.openAiClientAsync(mock(OpenAIClientAsync.class))
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);
		AudioTranscriptionResponse response = model.call(prompt);

		assertThat(response.getResult().getOutput()).isEqualTo("Hello, this is a test transcription with options.");
	}

	@Test
	void transcribeWithResourceReturnsText() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Simple output").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(client)
			.openAiClientAsync(mock(OpenAIClientAsync.class))
			.build();
		String text = model.transcribe(new ClassPathResource("/speech.flac"));

		assertThat(text).isEqualTo("Simple output");
	}

	@Test
	void transcribeWithOptionsUsesMergedOptions() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("With options").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.build();
		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(client)
			.openAiClientAsync(mock(OpenAIClientAsync.class))
			.options(options)
			.build();
		String text = model.transcribe(new ClassPathResource("/speech.flac"), options);

		assertThat(text).isEqualTo("With options");
	}

	@Test
	void optionsBuilderFromCopiesAllFields() {
		OpenAiAudioTranscriptionOptions original = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.responseFormat(AudioResponseFormat.VERBOSE_JSON)
			.language("en")
			.prompt("test prompt")
			.temperature(0.5f)
			.baseUrl("https://custom.api.com")
			.apiKey("test-key")
			.organizationId("org-123")
			.build();

		OpenAiAudioTranscriptionOptions copied = OpenAiAudioTranscriptionOptions.builder().from(original).build();

		assertThat(copied.getModel()).isEqualTo("whisper-1");
		assertThat(copied.getResponseFormat()).isEqualTo(AudioResponseFormat.VERBOSE_JSON);
		assertThat(copied.getLanguage()).isEqualTo("en");
		assertThat(copied.getPrompt()).isEqualTo("test prompt");
		assertThat(copied.getTemperature()).isEqualTo(0.5f);
		assertThat(copied.getBaseUrl()).isEqualTo("https://custom.api.com");
		assertThat(copied.getApiKey()).isEqualTo("test-key");
		assertThat(copied.getOrganizationId()).isEqualTo("org-123");
	}

	@Test
	void optionsBuilderMergeOverridesNonNullValues() {
		OpenAiAudioTranscriptionOptions base = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.temperature(0.5f)
			.build();

		OpenAiAudioTranscriptionOptions override = OpenAiAudioTranscriptionOptions.builder()
			.language("de")
			.prompt("new prompt")
			.build();

		OpenAiAudioTranscriptionOptions merged = OpenAiAudioTranscriptionOptions.builder()
			.from(base)
			.merge(override)
			.build();

		assertThat(merged.getModel()).isEqualTo("whisper-1");
		assertThat(merged.getLanguage()).isEqualTo("de");
		assertThat(merged.getPrompt()).isEqualTo("new prompt");
		assertThat(merged.getTemperature()).isEqualTo(0.5f);
	}

	@Test
	void optionsEqualsAndHashCode() {
		OpenAiAudioTranscriptionOptions options1 = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.temperature(0.5f)
			.build();

		OpenAiAudioTranscriptionOptions options2 = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.temperature(0.5f)
			.build();

		OpenAiAudioTranscriptionOptions options3 = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("de")
			.temperature(0.5f)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
		assertThat(options1).isNotEqualTo(options3);
	}

	@Test
	void optionsBuilderWithAzureConfiguration() {
		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.deploymentName("my-deployment")
			.microsoftFoundry(true)
			.baseUrl("https://my-resource.openai.azure.com")
			.build();

		assertThat(options.getDeploymentName()).isEqualTo("my-deployment");
		assertThat(options.isMicrosoftFoundry()).isTrue();
		assertThat(options.getBaseUrl()).isEqualTo("https://my-resource.openai.azure.com");
	}

	@Test
	void mutateCreatesBuilderWithSameConfiguration() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Mutated model output").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.build();

		OpenAiAudioTranscriptionModel originalModel = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(client)
			.openAiClientAsync(mock(OpenAIClientAsync.class))
			.options(options)
			.build();

		OpenAiAudioTranscriptionModel mutatedModel = originalModel.mutate().build();

		assertThat(mutatedModel.getOptions().getModel()).isEqualTo("whisper-1");
		assertThat(mutatedModel.getOptions().getLanguage()).isEqualTo("en");

		String text = mutatedModel.transcribe(new ClassPathResource("/speech.flac"));
		assertThat(text).isEqualTo("Mutated model output");
	}

	@Test
	void mutateAllowsOverridingOptions() {
		TranscriptionCreateResponse mockResponse = TranscriptionCreateResponse
			.ofTranscription(Transcription.builder().text("Modified options output").build());

		OpenAIClient client = createMockClient(mockResponse);

		OpenAiAudioTranscriptionOptions originalOptions = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("en")
			.build();

		OpenAiAudioTranscriptionModel originalModel = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(client)
			.openAiClientAsync(mock(OpenAIClientAsync.class))
			.options(originalOptions)
			.build();

		OpenAiAudioTranscriptionOptions newOptions = OpenAiAudioTranscriptionOptions.builder()
			.model("whisper-1")
			.language("de")
			.temperature(0.5f)
			.build();

		OpenAiAudioTranscriptionModel mutatedModel = originalModel.mutate().options(newOptions).build();

		assertThat(mutatedModel.getOptions().getLanguage()).isEqualTo("de");
		assertThat(mutatedModel.getOptions().getTemperature()).isEqualTo(0.5f);
	}

	@Test
	void streamReturnsTranscriptionTextChunks() {
		AsyncStreamResponse<TranscriptionStreamEvent> mockAsyncResponse = asyncStreamResponse(
				TranscriptionStreamEvent
					.ofTranscriptTextDelta(TranscriptionTextDeltaEvent.builder().delta("Hello, ").build()),
				TranscriptionStreamEvent.ofTranscriptTextDelta(
						TranscriptionTextDeltaEvent.builder().delta("streamed transcription result").build()));

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"));

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(mock(OpenAIClient.class))
			.openAiClientAsync(createMockAsyncClient(mockAsyncResponse))
			.build();

		List<String> chunks = model.stream(prompt)
			.map(response -> response.getResult().getOutput())
			.collectList()
			.block();

		assertThat(chunks).isNotNull();
		String text = String.join("", chunks);
		assertThat(text).isEqualTo("Hello, streamed transcription result");
	}

	@Test
	void streamWithPromptOptions() {
		AsyncStreamResponse<TranscriptionStreamEvent> mockAsyncResponse = asyncStreamResponse(
				TranscriptionStreamEvent
					.ofTranscriptTextDelta(TranscriptionTextDeltaEvent.builder().delta("Hello, ").build()),
				TranscriptionStreamEvent.ofTranscriptTextDelta(
						TranscriptionTextDeltaEvent.builder().delta("streamed transcription result").build()));

		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.temperature(0.5f)
			.responseFormat(AudioResponseFormat.JSON)
			.build();

		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(new ClassPathResource("/speech.flac"), options);

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(mock(OpenAIClient.class))
			.openAiClientAsync(createMockAsyncClient(mockAsyncResponse))
			.build();

		List<String> chunks = model.stream(prompt)
			.map(response -> response.getResult().getOutput())
			.collectList()
			.block();

		assertThat(chunks).isNotNull();
		String text = String.join("", chunks);
		assertThat(text).isEqualTo("Hello, streamed transcription result");
	}

	@Test
	void streamTranscribeWithResource() {
		AsyncStreamResponse<TranscriptionStreamEvent> mockAsyncResponse = asyncStreamResponse(
				TranscriptionStreamEvent
					.ofTranscriptTextDelta(TranscriptionTextDeltaEvent.builder().delta("Hello, ").build()),
				TranscriptionStreamEvent.ofTranscriptTextDelta(
						TranscriptionTextDeltaEvent.builder().delta("streamed transcription result").build()));

		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.temperature(0.5f)
			.responseFormat(AudioResponseFormat.JSON)
			.build();

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(mock(OpenAIClient.class))
			.openAiClientAsync(createMockAsyncClient(mockAsyncResponse))
			.build();

		List<String> chunks = model.streamTranscribe(new ClassPathResource("/speech.flac"), options)
			.collectList()
			.block();

		assertThat(chunks).isNotNull();
		String text = String.join("", chunks);
		assertThat(text).isEqualTo("Hello, streamed transcription result");
	}

	@Test
	void streamTranscribeWithResourceAndOptions() {
		AsyncStreamResponse<TranscriptionStreamEvent> mockAsyncResponse = asyncStreamResponse(
				TranscriptionStreamEvent
					.ofTranscriptTextDelta(TranscriptionTextDeltaEvent.builder().delta("Hello, ").build()),
				TranscriptionStreamEvent.ofTranscriptTextDelta(
						TranscriptionTextDeltaEvent.builder().delta("streamed transcription result").build()));

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(mock(OpenAIClient.class))
			.openAiClientAsync(createMockAsyncClient(mockAsyncResponse))
			.build();

		List<String> chunks = model.streamTranscribe(new ClassPathResource("/speech.flac")).collectList().block();

		assertThat(chunks).isNotNull();
		String text = String.join("", chunks);
		assertThat(text).isEqualTo("Hello, streamed transcription result");
	}

	@Test
	void testPropagatesTimeoutFromRequestOptions() {
		Duration expectedTimeout = Duration.ofSeconds(30);

		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		TranscriptionCreateResponse mockResponse = mock(TranscriptionCreateResponse.class);
		when(mockClient.audio()
			.transcriptions()
			.create(any(TranscriptionCreateParams.class), any(RequestOptions.class))).thenReturn(mockResponse);

		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(mockClient)
			.openAiClientAsync(mock(OpenAIClientAsync.class))
			.build();

		OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
			.timeout(expectedTimeout)
			.build();

		model.call(new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1 }), options));

		ArgumentCaptor<RequestOptions> argumentCaptor = ArgumentCaptor.forClass(RequestOptions.class);
		verify(mockClient.audio().transcriptions()).create(any(TranscriptionCreateParams.class),
				argumentCaptor.capture());
		RequestOptions value = argumentCaptor.getValue();
		assertThat(value.getTimeout()).isNotNull();
		assertThat(value.getTimeout().request()).isEqualTo(expectedTimeout);
	}

	private OpenAIClient createMockClient(TranscriptionCreateResponse mockResponse) {
		OpenAIClient client = mock(OpenAIClient.class);
		AudioService audioService = mock(AudioService.class);
		TranscriptionService transcriptionService = mock(TranscriptionService.class);
		when(client.audio()).thenReturn(audioService);
		when(audioService.transcriptions()).thenReturn(transcriptionService);
		when(transcriptionService.create(any(TranscriptionCreateParams.class), any(RequestOptions.class)))
			.thenReturn(mockResponse);
		return client;
	}

	private OpenAIClientAsync createMockAsyncClient(AsyncStreamResponse<TranscriptionStreamEvent> mockAsyncResponse) {
		OpenAIClientAsync clientAsync = mock(OpenAIClientAsync.class);
		AudioServiceAsync audioServiceAsync = mock(AudioServiceAsync.class);
		TranscriptionServiceAsync transcriptionServiceAsync = mock(TranscriptionServiceAsync.class);
		when(clientAsync.audio()).thenReturn(audioServiceAsync);
		when(audioServiceAsync.transcriptions()).thenReturn(transcriptionServiceAsync);
		when(transcriptionServiceAsync.createStreaming(any(TranscriptionCreateParams.class), any(RequestOptions.class)))
			.thenReturn(mockAsyncResponse);
		return clientAsync;
	}

	private <T> AsyncStreamResponse<T> asyncStreamResponse(T... chunks) {
		return new AsyncStreamResponse<>() {
			private final CompletableFuture<Void> completion = new CompletableFuture<>();

			@Override
			public AsyncStreamResponse<T> subscribe(AsyncStreamResponse.Handler<? super T> handler) {
				try {
					for (T chunk : chunks) {
						handler.onNext(chunk);
					}
					handler.onComplete(Optional.empty());
					this.completion.complete(null);
				}
				catch (Throwable throwable) {
					handler.onComplete(Optional.of(throwable));
					this.completion.completeExceptionally(throwable);
				}
				return this;
			}

			@Override
			public AsyncStreamResponse<T> subscribe(AsyncStreamResponse.Handler<? super T> handler, Executor executor) {
				executor.execute(() -> subscribe(handler));
				return this;
			}

			@Override
			public CompletableFuture<Void> onCompleteFuture() {
				return this.completion;
			}

			@Override
			public void close() {
			}
		};
	}

}
