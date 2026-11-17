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

package org.springframework.ai.mistralai.transcription;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationContext;
import org.springframework.ai.audio.transcription.observation.TranscriptionModelObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.mistralai.api.MistralAiAudioApi;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TimestampGranularity;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionRequest;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionResponse;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionSegment;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.TranscriptionStreamEvent;
import org.springframework.ai.mistralai.api.MistralAiAudioApi.Usage;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MistralAiTranscriptionModel}.
 *
 * @author Thomas Vitale
 */
class MistralAiTranscriptionModelTests {

	private static final Resource AUDIO = new ByteArrayResource("audio-bytes".getBytes());

	private final MistralAiAudioApi audioApi = mock();

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private MistralAiTranscriptionModel model(MistralAiTranscriptionOptions options) {
		return MistralAiTranscriptionModel.builder()
			.mistralAiAudioApi(this.audioApi)
			.options(options)
			.retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
			.observationRegistry(this.observationRegistry)
			.build();
	}

	@Test
	void callMapsTheResponse() {
		var segment = new TranscriptionSegment("Hello there.", 0.0, 1.2, null, "speaker_1");
		var usage = new Usage(3, 5, 20, 2);
		given(this.audioApi.createTranscription(any())).willReturn(ResponseEntity
			.ok(new TranscriptionResponse("voxtral-mini-transcribe-3", "Hello there.", "en", List.of(segment), usage)));

		AudioTranscriptionResponse response = model(MistralAiTranscriptionOptions.builder().build())
			.call(new AudioTranscriptionPrompt(AUDIO));

		assertThat(response.getResult().getOutput()).isEqualTo("Hello there.");
		assertThat(response.getMetadata()).isInstanceOfSatisfying(MistralAiTranscriptionResponseMetadata.class,
				metadata -> {
					assertThat(metadata.getModel()).isEqualTo("voxtral-mini-transcribe-3");
					assertThat(metadata.getLanguage()).isEqualTo("en");
					assertThat(metadata.getSegments()).containsExactly(segment);
					assertThat(metadata.getUsage().getPromptTokens()).isEqualTo(3);
					assertThat(metadata.getUsage().getCompletionTokens()).isEqualTo(5);
					assertThat(metadata.getUsage().getTotalTokens()).isEqualTo(20);
					assertThat(metadata.getUsage().getNativeUsage()).isEqualTo(usage);
				});
	}

	@Test
	void callSendsMergedOptions() {
		given(this.audioApi.createTranscription(any())).willReturn(
				ResponseEntity.ok(new TranscriptionResponse("voxtral-small-transcribe-3", "Hello.", null, null, null)));
		var defaultOptions = MistralAiTranscriptionOptions.builder()
			.model("voxtral-small-transcribe-3")
			.language("fr")
			.contextBias(List.of("Northwind"))
			.build();
		var runtimeOptions = MistralAiTranscriptionOptions.builder()
			.language("en")
			.diarize(true)
			.timestampGranularities(List.of(TimestampGranularity.SEGMENT))
			.build();

		model(defaultOptions).call(new AudioTranscriptionPrompt(AUDIO, runtimeOptions));

		ArgumentCaptor<TranscriptionRequest> request = ArgumentCaptor.forClass(TranscriptionRequest.class);
		verify(this.audioApi).createTranscription(request.capture());
		assertThat(request.getValue().model()).isEqualTo("voxtral-small-transcribe-3");
		assertThat(request.getValue().file()).isSameAs(AUDIO);
		assertThat(request.getValue().language()).isEqualTo("en");
		assertThat(request.getValue().diarize()).isTrue();
		assertThat(request.getValue().contextBias()).containsExactly("Northwind");
		assertThat(request.getValue().timestampGranularities()).containsExactly(TimestampGranularity.SEGMENT);
		assertThat(request.getValue().stream()).isNull();
	}

	@Test
	void callRetriesWithReadOnceAudio() {
		List<String> sentAudio = new ArrayList<>();
		given(this.audioApi.createTranscription(any())).willAnswer(invocation -> {
			TranscriptionRequest request = invocation.getArgument(0);
			sentAudio.add(request.file().getContentAsString(StandardCharsets.UTF_8));
			if (sentAudio.size() == 1) {
				throw new TransientAiException("Transient Error 1");
			}
			return ResponseEntity.ok(new TranscriptionResponse("voxtral-mini-latest", "Hello.", null, null, null));
		});
		var readOnceAudio = new InputStreamResource(new ByteArrayInputStream("audio-bytes".getBytes()));

		var response = model(MistralAiTranscriptionOptions.builder().build())
			.call(new AudioTranscriptionPrompt(readOnceAudio));

		assertThat(response.getResult().getOutput()).isEqualTo("Hello.");
		assertThat(sentAudio).containsExactly("audio-bytes", "audio-bytes");
	}

	@Test
	void streamSendsTheAudioAsIs() {
		given(this.audioApi.createTranscriptionStream(any())).willReturn(Flux.empty());
		var readOnceAudio = new InputStreamResource(new ByteArrayInputStream("audio-bytes".getBytes()));

		model(MistralAiTranscriptionOptions.builder().build()).stream(new AudioTranscriptionPrompt(readOnceAudio))
			.blockLast(Duration.ofSeconds(5));

		// A stream is not retried, so the audio is not buffered.
		ArgumentCaptor<TranscriptionRequest> request = ArgumentCaptor.forClass(TranscriptionRequest.class);
		verify(this.audioApi).createTranscriptionStream(request.capture());
		assertThat(request.getValue().file()).isSameAs(readOnceAudio);
	}

	@Test
	void callRetriesTransientErrors() {
		given(this.audioApi.createTranscription(any())).willThrow(new TransientAiException("Transient Error 1"))
			.willReturn(
					ResponseEntity.ok(new TranscriptionResponse("voxtral-mini-latest", "Hello.", null, null, null)));

		var response = model(MistralAiTranscriptionOptions.builder().build()).call(new AudioTranscriptionPrompt(AUDIO));

		assertThat(response.getResult().getOutput()).isEqualTo("Hello.");
		verify(this.audioApi, times(2)).createTranscription(any());
	}

	@Test
	void callIsObserved() {
		given(this.audioApi.createTranscription(any())).willReturn(
				ResponseEntity.ok(new TranscriptionResponse("voxtral-mini-latest", "Hello.", null, null, null)));

		model(MistralAiTranscriptionOptions.builder().build()).call(new AudioTranscriptionPrompt(AUDIO));

		assertObservation();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.doesNotHaveHighCardinalityKeyValueWithKey(AiObservationAttributes.REQUEST_STREAM.value());
	}

	@Test
	void streamEmitsTextDeltasAndObservesTheWholeTranscription() {
		AtomicReference<AudioTranscriptionResponse> observed = observedResponse();
		var usage = new Usage(3, 5, 20, 2);
		given(this.audioApi.createTranscriptionStream(any())).willReturn(Flux.just(
				event(TranscriptionStreamEvent.LANGUAGE, null), event(TranscriptionStreamEvent.TEXT_DELTA, "Hello"),
				event(TranscriptionStreamEvent.TEXT_DELTA, " there."),
				new TranscriptionStreamEvent(TranscriptionStreamEvent.DONE, "Hello there.", null, null, null, null,
						"voxtral-mini-latest", "en", List.of(), usage)));

		var responses = model(MistralAiTranscriptionOptions.builder().build())
			.stream(new AudioTranscriptionPrompt(AUDIO))
			.collectList()
			.block(Duration.ofSeconds(5));

		assertThat(responses).extracting(response -> response.getResult().getOutput())
			.containsExactly("Hello", " there.");
		ArgumentCaptor<TranscriptionRequest> request = ArgumentCaptor.forClass(TranscriptionRequest.class);
		verify(this.audioApi).createTranscriptionStream(request.capture());
		assertThat(request.getValue().stream()).isTrue();
		assertThat(observed.get().getResult().getOutput()).isEqualTo("Hello there.");
		assertThat(observed.get().getMetadata().getUsage().getTotalTokens()).isEqualTo(20);
		assertObservation();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasHighCardinalityKeyValue(AiObservationAttributes.REQUEST_STREAM.value(), "true");
	}

	@Test
	void streamWithoutDoneEventObservesTheTextDeltas() {
		AtomicReference<AudioTranscriptionResponse> observed = observedResponse();
		given(this.audioApi.createTranscriptionStream(any()))
			.willReturn(Flux.just(event(TranscriptionStreamEvent.TEXT_DELTA, "Hello"),
					event(TranscriptionStreamEvent.TEXT_DELTA, " there.")));

		model(MistralAiTranscriptionOptions.builder().build()).stream(new AudioTranscriptionPrompt(AUDIO))
			.blockLast(Duration.ofSeconds(5));

		assertThat(observed.get().getResult().getOutput()).isEqualTo("Hello there.");
	}

	@Test
	void failingStreamEndsTheObservationWithItsError() {
		given(this.audioApi.createTranscriptionStream(any()))
			.willReturn(Flux.concat(Flux.just(event(TranscriptionStreamEvent.TEXT_DELTA, "Hello")),
					Flux.error(new IllegalStateException("boom"))));

		var model = model(MistralAiTranscriptionOptions.builder().build());

		assertThatThrownBy(() -> model.stream(new AudioTranscriptionPrompt(AUDIO)).blockLast(Duration.ofSeconds(5)))
			.hasMessageContaining("boom");
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasError()
			.hasBeenStopped();
	}

	private static TranscriptionStreamEvent event(String type, String text) {
		return new TranscriptionStreamEvent(type, text, null, null, null, null, null, null, null, null);
	}

	private AtomicReference<AudioTranscriptionResponse> observedResponse() {
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
		return observed;
	}

	private void assertObservation() {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("transcription voxtral-mini-latest")
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.TRANSCRIPTION.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.MISTRAL_AI.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(), "voxtral-mini-latest")
			.hasBeenStarted()
			.hasBeenStopped();
	}

}
