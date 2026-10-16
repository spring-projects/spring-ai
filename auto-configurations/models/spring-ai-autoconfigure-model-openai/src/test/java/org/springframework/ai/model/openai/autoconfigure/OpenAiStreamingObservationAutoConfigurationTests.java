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

package org.springframework.ai.model.openai.autoconfigure;

import java.time.Duration;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.observation.DefaultTranscriptionModelObservationConvention;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.responses.OpenAiResponsesChatModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ByteArrayResource;

/**
 * Tests that the streaming requests of the auto-configured OpenAI models are children of
 * the model observation, so the {@code traceparent} header they send carries the caller's
 * trace.
 *
 * @author sajeevKR
 */
class OpenAiStreamingObservationAutoConfigurationTests {

	private static final String HTTP_OBSERVATION_NAME = "okhttp.requests";

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private static final String CHAT_STREAM = """
			data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4o-mini",\
			"choices":[{"index":0,"delta":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}

			data: [DONE]

			""";

	private static final String TRANSCRIPTION_STREAM = """
			data: {"type":"transcript.text.delta","delta":"ok"}

			""";

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final MockWebServer server = new MockWebServer();

	private ApplicationContextRunner contextRunner;

	@BeforeEach
	void setUp() throws Exception {
		this.server.start();
		this.contextRunner = new ApplicationContextRunner()
			.withPropertyValues("spring.ai.openai.api-key=test-key",
					"spring.ai.openai.base-url=" + this.server.url("/v1"))
			.withBean(ObservationRegistry.class, () -> this.observationRegistry)
			.withConfiguration(
					AutoConfigurations.of(OpenAiChatAutoConfiguration.class, OpenAiResponsesChatAutoConfiguration.class,
							OpenAiAudioTranscriptionAutoConfiguration.class, ToolCallingAutoConfiguration.class));
	}

	@AfterEach
	void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	void chatCompletionsStreamingRequestIsChildOfTheChatObservation() {
		this.server.enqueue(eventStream(CHAT_STREAM));

		this.contextRunner.run(context -> {
			context.getBean(OpenAiChatModel.class).stream(new Prompt("hi")).blockLast(TIMEOUT);

			assertHttpObservationIsChildOf(DefaultChatModelObservationConvention.DEFAULT_NAME);
		});
	}

	@Test
	void responsesStreamingRequestIsChildOfTheChatObservation() {
		this.server.enqueue(eventStream(""));

		this.contextRunner.withPropertyValues("spring.ai.openai.chat.api=responses").run(context -> {
			context.getBean(OpenAiResponsesChatModel.class).stream(new Prompt("hi")).blockLast(TIMEOUT);

			assertHttpObservationIsChildOf(DefaultChatModelObservationConvention.DEFAULT_NAME);
		});
	}

	@Test
	void transcriptionStreamingRequestIsChildOfTheTranscriptionObservation() {
		this.server.enqueue(eventStream(TRANSCRIPTION_STREAM));

		this.contextRunner.run(context -> {
			context.getBean(OpenAiAudioTranscriptionModel.class).stream(transcriptionPrompt()).blockLast(TIMEOUT);

			assertHttpObservationIsChildOf(DefaultTranscriptionModelObservationConvention.DEFAULT_NAME);
		});
	}

	private void assertHttpObservationIsChildOf(String parentName) {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(HTTP_OBSERVATION_NAME)
			.that()
			.hasParentObservationContextMatching(context -> parentName.equals(context.getName()),
					"parent observation '%s'".formatted(parentName));
	}

	private static MockResponse eventStream(String body) {
		return new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body);
	}

	private static AudioTranscriptionPrompt transcriptionPrompt() {
		return new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1, 2, 3 }) {

			@Override
			public String getFilename() {
				return "question.wav";
			}

		});
	}

}
