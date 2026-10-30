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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.errors.BadRequestException;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionStreamEvent;
import com.openai.services.blocking.AudioService;
import com.openai.services.blocking.audio.TranscriptionService;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests the HTTP side of {@link OpenAiAudioTranscriptionModel} streaming against a fake
 * OpenAI server: the HTTP observation, which writes the outgoing {@code traceparent}
 * header, is a child of the transcription model observation.
 *
 * @author sajeevKR
 */
class OpenAiAudioTranscriptionModelStreamingHttpTests {

	private static final String HTTP_OBSERVATION_NAME = "okhttp.requests";

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	private static final String STREAM_RESPONSE = """
			data: {"type":"transcript.text.delta","delta":"What is the weather?"}

			data: {"type":"transcript.text.done","text":"What is the weather?"}

			""";

	private static final String ERROR_RESPONSE = """
			{"error":{"message":"bad request","type":"invalid_request_error","param":null,"code":null}}
			""";

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final MockWebServer server = new MockWebServer();

	private OpenAiAudioTranscriptionModel model;

	@BeforeEach
	void setUp() throws Exception {
		this.server.start();
		this.model = OpenAiAudioTranscriptionModel.builder()
			.options(OpenAiAudioTranscriptionOptions.builder()
				.baseUrl(this.server.url("/v1").toString())
				.apiKey("test-key")
				.model("gpt-4o-mini-transcribe")
				.build())
			.observationRegistry(this.observationRegistry)
			.build();
	}

	@AfterEach
	void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	void httpObservationIsChildOfTranscriptionObservationForStream() {
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(STREAM_RESPONSE));

		List<AudioTranscriptionResponse> responses = this.model.stream(prompt()).collectList().block(TIMEOUT);

		assertThat(responses).isNotEmpty();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(HTTP_OBSERVATION_NAME)
			.that()
			.hasParentObservationContextMatching(
					context -> DefaultTranscriptionModelObservationConvention.DEFAULT_NAME.equals(context.getName()),
					"parent observation '%s'".formatted(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME));
	}

	@Test
	void streamErrorReachesTheSubscriberAndTheObservation() {
		this.server.enqueue(new MockResponse().setResponseCode(400)
			.setHeader("Content-Type", "application/json")
			.setBody(ERROR_RESPONSE));

		assertThatThrownBy(() -> this.model.stream(prompt()).collectList().block(TIMEOUT))
			.isInstanceOf(BadRequestException.class);
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasError();
	}

	@Test
	void streamSendsTheRequestOnTheStreamSchedulerAndClosesTheResponse() {
		Scheduler scheduler = Schedulers.newSingle("transcription-stream");
		try {
			AtomicReference<String> requestThread = new AtomicReference<>();
			AtomicBoolean closed = new AtomicBoolean();
			OpenAIClient openAiClient = mock(OpenAIClient.class);
			AudioService audioService = mock(AudioService.class);
			TranscriptionService transcriptionService = mock(TranscriptionService.class);
			given(openAiClient.audio()).willReturn(audioService);
			given(audioService.transcriptions()).willReturn(transcriptionService);
			given(transcriptionService.createStreaming(any(TranscriptionCreateParams.class), any(RequestOptions.class)))
				.willAnswer(invocation -> {
					requestThread.set(Thread.currentThread().getName());
					return new StreamResponse<TranscriptionStreamEvent>() {

						@Override
						public Stream<TranscriptionStreamEvent> stream() {
							return Stream.empty();
						}

						@Override
						public void close() {
							closed.set(true);
						}

					};
				});
			OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
				.openAiClient(openAiClient)
				.streamScheduler(scheduler)
				.options(OpenAiAudioTranscriptionOptions.builder().model("gpt-4o-mini-transcribe").build())
				.build();

			model.stream(prompt()).blockLast(TIMEOUT);

			assertThat(requestThread.get()).startsWith("transcription-stream");
			assertThat(closed).isTrue();
		}
		finally {
			scheduler.dispose();
		}
	}

	private static AudioTranscriptionPrompt prompt() {
		return new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1, 2, 3 }) {

			@Override
			public String getFilename() {
				return "question.wav";
			}

		});
	}

}
