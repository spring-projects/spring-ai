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

import com.openai.client.OpenAIClient;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the HTTP side of {@link OpenAiAudioTranscriptionModel} streaming against a fake
 * OpenAI server: the HTTP observation, which writes the outgoing {@code traceparent}
 * header, is a child of the transcription model observation.
 *
 * @author sajeevKR
 */
class OpenAiAudioTranscriptionModelStreamingHttpTests {

	private static final String HTTP_OBSERVATION_NAME = "okhttp.requests";

	private static final String STREAM_RESPONSE = """
			data: {"type":"transcript.text.delta","delta":"What is the weather?"}

			data: {"type":"transcript.text.done","text":"What is the weather?"}

			""";

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final MockWebServer server = new MockWebServer();

	@BeforeEach
	void setUp() throws Exception {
		this.server.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	void httpObservationIsChildOfTranscriptionObservationForStream() {
		// The model's default client records no HTTP observations, so the client is
		// set up the way the auto-configuration sets it up.
		OpenAIClient openAiClient = OpenAiSetup.setupSyncClient(this.server.url("/v1").toString(), "test-key", null,
				null, null, null, false, false, "gpt-4o-mini-transcribe", Duration.ofSeconds(10), 0, null, null,
				this.observationRegistry, null, List.of());
		OpenAiAudioTranscriptionModel model = OpenAiAudioTranscriptionModel.builder()
			.openAiClient(openAiClient)
			.options(OpenAiAudioTranscriptionOptions.builder().model("gpt-4o-mini-transcribe").build())
			.observationRegistry(this.observationRegistry)
			.build();
		this.server.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(STREAM_RESPONSE));

		List<AudioTranscriptionResponse> responses = model.stream(prompt()).collectList().block();

		assertThat(responses).isNotEmpty();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(HTTP_OBSERVATION_NAME)
			.that()
			.hasParentObservationContextMatching(
					context -> DefaultTranscriptionModelObservationConvention.DEFAULT_NAME.equals(context.getName()),
					"parent observation '%s'".formatted(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME));
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
