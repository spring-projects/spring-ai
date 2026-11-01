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
import java.util.concurrent.atomic.AtomicReference;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.core.RequestOptions;
import com.openai.core.http.AsyncStreamResponse;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionStreamEvent;
import com.openai.models.audio.transcriptions.TranscriptionTextDeltaEvent;
import com.openai.services.async.AudioServiceAsync;
import com.openai.services.async.audio.TranscriptionServiceAsync;
import com.openai.services.blocking.AudioService;
import com.openai.services.blocking.audio.TranscriptionService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Observation tests for {@link OpenAiAudioTranscriptionModel}, against a mocked client.
 *
 * @author Christian Tzolov
 */
class OpenAiAudioTranscriptionModelObservationTests {

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private static OpenAIClient mockClient() {
		OpenAIClient client = mock(OpenAIClient.class);
		AudioService audioService = mock(AudioService.class);
		TranscriptionService transcriptionService = mock(TranscriptionService.class);
		when(client.audio()).thenReturn(audioService);
		when(audioService.transcriptions()).thenReturn(transcriptionService);
		when(transcriptionService.create(any(TranscriptionCreateParams.class), any(RequestOptions.class)))
			.thenReturn(TranscriptionCreateResponse
				.ofTranscription(Transcription.builder().text("What is the weather?").build()));
		return client;
	}

	/**
	 * An async client whose transcription stream delivers the given deltas on
	 * subscription, then completes, or fails with the given error.
	 */
	private static OpenAIClientAsync mockAsyncClient(List<String> deltas, @Nullable Throwable error) {
		OpenAIClientAsync client = mock(OpenAIClientAsync.class);
		AudioServiceAsync audioService = mock(AudioServiceAsync.class);
		TranscriptionServiceAsync transcriptionService = mock(TranscriptionServiceAsync.class);
		when(client.audio()).thenReturn(audioService);
		when(audioService.transcriptions()).thenReturn(transcriptionService);
		when(transcriptionService.createStreaming(any(TranscriptionCreateParams.class), any(RequestOptions.class)))
			.thenReturn(new AsyncStreamResponse<>() {

				private final CompletableFuture<Void> completion = new CompletableFuture<>();

				@Override
				public AsyncStreamResponse<TranscriptionStreamEvent> subscribe(
						Handler<? super TranscriptionStreamEvent> handler) {
					for (String delta : deltas) {
						handler.onNext(TranscriptionStreamEvent
							.ofTranscriptTextDelta(TranscriptionTextDeltaEvent.builder().delta(delta).build()));
					}
					if (error != null) {
						this.completion.completeExceptionally(error);
					}
					else {
						this.completion.complete(null);
					}
					handler.onComplete(Optional.ofNullable(error));
					return this;
				}

				@Override
				public AsyncStreamResponse<TranscriptionStreamEvent> subscribe(
						Handler<? super TranscriptionStreamEvent> handler, Executor executor) {
					return subscribe(handler);
				}

				@Override
				public CompletableFuture<Void> onCompleteFuture() {
					return this.completion;
				}

				@Override
				public void close() {
				}

			});
		return client;
	}

	private static AudioTranscriptionPrompt prompt() {
		return new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1, 2, 3 }) {
			@Override
			public String getFilename() {
				return "question.wav";
			}
		});
	}

	private OpenAiAudioTranscriptionModel model(OpenAIClientAsync asyncClient) {
		return OpenAiAudioTranscriptionModel.builder()
			.openAiClient(mockClient())
			.openAiClientAsync(asyncClient)
			.options(OpenAiAudioTranscriptionOptions.builder().model("whisper-1").build())
			.observationRegistry(this.observationRegistry)
			.build();
	}

	@Test
	void observationForCall() {
		var response = model(mock(OpenAIClientAsync.class)).call(prompt());

		assertThat(response.getResult().getOutput()).isEqualTo("What is the weather?");
		assertObservation();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.doesNotHaveHighCardinalityKeyValueWithKey(AiObservationAttributes.REQUEST_STREAM.value());
	}

	@Test
	void observationForStreamCarriesTheWholeTranscript() {
		// A handler sees the response the stream was put together into.
		AtomicReference<AudioTranscriptionResponse> observed = new AtomicReference<>();
		this.observationRegistry.observationConfig()
			.observationHandler(new ObservationHandler<TranscriptionModelObservationContext>() {
				@Override
				public boolean supportsContext(Observation.Context context) {
					return context instanceof TranscriptionModelObservationContext;
				}

				@Override
				public void onStop(TranscriptionModelObservationContext context) {
					observed.set(context.getResponse());
				}
			});

		var responses = model(mockAsyncClient(List.of("What is ", "the weather?"), null)).stream(prompt())
			.collectList()
			.block(Duration.ofSeconds(5));

		assertThat(responses).extracting(response -> response.getResult().getOutput())
			.containsExactly("What is ", "the weather?");
		assertThat(observed.get()).isNotNull();
		assertThat(observed.get().getResult().getOutput()).isEqualTo("What is the weather?");
		assertObservation();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasHighCardinalityKeyValue(AiObservationAttributes.REQUEST_STREAM.value(), "true");
	}

	@Test
	void aFailingStreamEndsTheObservationWithItsError() {
		var model = model(mockAsyncClient(List.of("What is "), new IllegalStateException("boom")));

		assertThatThrownBy(() -> model.stream(prompt()).blockLast(Duration.ofSeconds(5))).hasMessageContaining("boom");

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasError()
			.hasBeenStopped();
	}

	private void assertObservation() {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("transcription whisper-1")
			.hasLowCardinalityKeyValue(
					TranscriptionModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.TRANSCRIPTION.value())
			.hasLowCardinalityKeyValue(
					TranscriptionModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER.asString(),
					AiProvider.OPENAI.value())
			.hasLowCardinalityKeyValue(
					TranscriptionModelObservationDocumentation.LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					"whisper-1")
			.hasBeenStarted()
			.hasBeenStopped();
	}

}
