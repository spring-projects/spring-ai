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

package org.springframework.ai.openai.responses;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import com.openai.client.OpenAIClient;
import com.openai.core.ObjectMappers;
import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.errors.BadRequestException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCompletedEvent;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStreamEvent;
import com.openai.models.responses.ResponseTextDeltaEvent;
import com.openai.services.blocking.ResponseService;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests the HTTP side of {@link OpenAiResponsesChatModel} streaming against a fake OpenAI
 * server: the HTTP observation, which writes the outgoing {@code traceparent} header, is
 * a child of the chat model observation.
 *
 * @author sajeevKR
 */
class OpenAiResponsesChatModelStreamingHttpTests {

	private static final String HTTP_OBSERVATION_NAME = "okhttp.requests";

	private static final String ERROR_RESPONSE = """
			{"error":{"message":"bad request","type":"invalid_request_error","param":null,"code":null}}
			""";

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final MockWebServer server = new MockWebServer();

	private OpenAiResponsesChatModel chatModel;

	@BeforeEach
	void setUp() throws Exception {
		this.server.start();
		this.chatModel = OpenAiResponsesChatModel.builder()
			.options(OpenAiResponsesChatOptions.builder()
				.baseUrl(this.server.url("/v1").toString())
				.apiKey("test-key")
				.model("gpt-5-mini")
				.build())
			.observationRegistry(this.observationRegistry)
			.build();
	}

	@AfterEach
	void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	void httpObservationIsChildOfChatObservationForStream() throws Exception {
		Response response = ResponsesTestFixtures.response("text-with-reasoning-summary.json");
		this.server.enqueue(streamResponse(
				ResponseStreamEvent.ofOutputTextDelta(ResponseTextDeltaEvent.builder()
					.contentIndex(0)
					.delta("It is 18 degrees in Paris.")
					.itemId("msg_1")
					.outputIndex(1)
					.logprobs(List.of())
					.sequenceNumber(1)
					.build()),
				ResponseStreamEvent
					.ofCompleted(ResponseCompletedEvent.builder().response(response).sequenceNumber(2).build())));

		List<ChatResponse> responses = this.chatModel.stream(new Prompt("Weather in Paris?")).collectList().block();

		assertThat(responses).isNotEmpty();
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(HTTP_OBSERVATION_NAME)
			.that()
			.hasParentObservationContextMatching(
					context -> DefaultChatModelObservationConvention.DEFAULT_NAME.equals(context.getName()),
					"parent observation '%s'".formatted(DefaultChatModelObservationConvention.DEFAULT_NAME));
	}

	@Test
	void streamSendsTheRequestOnTheStreamSchedulerAndClosesTheResponse() {
		Scheduler scheduler = Schedulers.newSingle("responses-stream");
		try {
			AtomicReference<String> requestThread = new AtomicReference<>();
			AtomicBoolean closed = new AtomicBoolean();
			OpenAIClient openAiClient = mock(OpenAIClient.class);
			ResponseService responseService = mock(ResponseService.class);
			given(openAiClient.responses()).willReturn(responseService);
			given(responseService.createStreaming(any(ResponseCreateParams.class), any(RequestOptions.class)))
				.willAnswer(invocation -> {
					requestThread.set(Thread.currentThread().getName());
					return new StreamResponse<ResponseStreamEvent>() {

						@Override
						public Stream<ResponseStreamEvent> stream() {
							return Stream.empty();
						}

						@Override
						public void close() {
							closed.set(true);
						}

					};
				});
			OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
				.openAiClient(openAiClient)
				.streamScheduler(scheduler)
				.options(OpenAiResponsesChatOptions.builder().model("gpt-5-mini").build())
				.build();

			model.stream(new Prompt("hi")).blockLast(Duration.ofSeconds(5));

			assertThat(requestThread.get()).startsWith("responses-stream");
			assertThat(closed).isTrue();
		}
		finally {
			scheduler.dispose();
		}
	}

	@Test
	void streamErrorReachesTheSubscriber() {
		this.server.enqueue(new MockResponse().setResponseCode(400)
			.setHeader("Content-Type", "application/json")
			.setBody(ERROR_RESPONSE));

		assertThatThrownBy(() -> this.chatModel.stream(new Prompt("hi")).collectList().block())
			.isInstanceOf(BadRequestException.class);
	}

	private static MockResponse streamResponse(ResponseStreamEvent... events) throws Exception {
		StringBuilder body = new StringBuilder();
		for (ResponseStreamEvent event : events) {
			body.append("data: ").append(ObjectMappers.jsonMapper().writeValueAsString(event)).append("\n\n");
		}
		return new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body.toString());
	}

}
