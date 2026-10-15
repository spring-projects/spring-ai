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

package org.springframework.ai.anthropic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import com.anthropic.errors.BadRequestException;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationHandler.FirstMatchingCompositeObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Hooks;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the HTTP side of {@link AnthropicChatModel} streaming against a fake Anthropic
 * server. Most tests check that the HTTP observation for a request to Anthropic is a
 * child of the chat model observation. The HTTP observation is what writes the outgoing
 * {@code traceparent} header, so when it has the right parent, Anthropic's server-side
 * spans join the caller's trace.
 *
 * @author Soby Chacko
 */
class AnthropicChatModelStreamingHttpTests {

	private static final String HTTP_OBSERVATION_NAME = "okhttp.requests";

	private static final String MESSAGE_RESPONSE = """
			{"id":"msg_1","type":"message","role":"assistant","content":[{"type":"text","text":"ok"}],\
			"model":"claude-haiku-4-5","stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}
			""";

	private static final String STREAM_RESPONSE = """
			event: message_start
			data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],\
			"model":"claude-haiku-4-5","stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":1}}}

			event: content_block_start
			data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

			event: content_block_delta
			data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"ok"}}

			event: content_block_stop
			data: {"type":"content_block_stop","index":0}

			event: message_delta
			data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":1}}

			event: message_stop
			data: {"type":"message_stop"}

			""";

	private static final String ERROR_RESPONSE = """
			{"type":"error","error":{"type":"invalid_request_error","message":"bad request"}}
			""";

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final MockWebServer server = new MockWebServer();

	private AnthropicChatModel chatModel;

	@BeforeEach
	void setUp() throws Exception {
		this.server.start();
		this.chatModel = AnthropicChatModel.builder()
			.options(AnthropicChatOptions.builder()
				.baseUrl(this.server.url("/").toString())
				.apiKey("test-key")
				.model("claude-haiku-4-5")
				.maxTokens(10)
				.build())
			.observationRegistry(this.observationRegistry)
			.build();
	}

	@AfterEach
	void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	void httpObservationIsChildOfChatObservationForCall() {
		this.server.enqueue(jsonResponse(MESSAGE_RESPONSE));

		ChatResponse response = this.chatModel.call(new Prompt("hi"));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("ok");
		assertHttpObservationIsChildOfChatObservation();
	}

	@Test
	void httpObservationIsChildOfChatObservationForStream() {
		this.server.enqueue(streamResponse());

		List<ChatResponse> responses = this.chatModel.stream(new Prompt("hi")).collectList().block();

		assertThat(responses).isNotEmpty();
		assertHttpObservationIsChildOfChatObservation();
	}

	@Test
	void streamRequestCarriesTraceparentOfChatSpan() throws Exception {
		ContextPropagators propagators = ContextPropagators.create(W3CTraceContextPropagator.getInstance());
		io.opentelemetry.api.trace.Tracer otelTracer = SdkTracerProvider.builder().build().get("test");
		OtelTracer tracer = new OtelTracer(otelTracer, new OtelCurrentTraceContext(), event -> {
		});
		List<Observation.Context> contexts = new CopyOnWriteArrayList<>();
		ObservationRegistry tracingRegistry = ObservationRegistry.create();
		tracingRegistry.observationConfig()
			.observationHandler(
					new FirstMatchingCompositeObservationHandler(
							new PropagatingSenderTracingObservationHandler<>(tracer,
									new OtelPropagator(propagators, otelTracer)),
							new DefaultTracingObservationHandler(tracer)))
			.observationHandler(new ObservationHandler<>() {

				@Override
				public void onStart(Observation.Context context) {
					contexts.add(context);
				}

				@Override
				public boolean supportsContext(Observation.Context context) {
					return true;
				}

			});
		AnthropicChatModel model = AnthropicChatModel.builder()
			.options(this.chatModel.getOptions())
			.observationRegistry(tracingRegistry)
			.build();
		this.server.enqueue(streamResponse());

		model.stream(new Prompt("hi")).collectList().block();

		TraceContext chatSpan = spanOf(contexts, DefaultChatModelObservationConvention.DEFAULT_NAME);
		TraceContext httpSpan = spanOf(contexts, HTTP_OBSERVATION_NAME);
		assertThat(httpSpan.parentId()).isEqualTo(chatSpan.spanId());
		assertThat(this.server.takeRequest().getHeader("traceparent"))
			.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
			.startsWith("00-%s-%s-".formatted(chatSpan.traceId(), httpSpan.spanId()));
	}

	@Test
	void everyRetriedStreamAttemptIsChildOfChatObservation() {
		List<Observation.Context> httpObservations = new CopyOnWriteArrayList<>();
		this.observationRegistry.observationConfig().observationHandler(new ObservationHandler<>() {

			@Override
			public void onStart(Observation.Context context) {
				if (HTTP_OBSERVATION_NAME.equals(context.getName())) {
					httpObservations.add(context);
				}
			}

			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

		});
		this.server.enqueue(new MockResponse().setResponseCode(500));
		this.server.enqueue(streamResponse());

		List<ChatResponse> responses = this.chatModel.stream(new Prompt("hi")).collectList().block();

		assertThat(responses).isNotEmpty();
		assertThat(this.server.getRequestCount()).isEqualTo(2);
		assertThat(httpObservations).hasSize(2)
			.allSatisfy(context -> assertThat(context.getParentObservation()).isNotNull()
				.satisfies(parent -> assertThat(isChatObservation(parent.getContextView())).isTrue()));
	}

	@Test
	void streamErrorReachesTheSubscriber() {
		this.server.enqueue(jsonResponse(ERROR_RESPONSE).setResponseCode(400));

		assertThatThrownBy(() -> this.chatModel.stream(new Prompt("hi")).collectList().block())
			.isInstanceOf(BadRequestException.class);
	}

	@Test
	void streamCancelledWhileOpeningIsNotReportedAsAnError() throws Exception {
		// The response headers arrive long after the subscriber cancels, so the cancel
		// lands while the stream is still being opened.
		this.server.enqueue(streamResponse().setHeadersDelay(2, TimeUnit.SECONDS));
		List<Throwable> errors = new CopyOnWriteArrayList<>();
		Hooks.onErrorDropped(errors::add);
		try {
			Disposable subscription = this.chatModel.stream(new Prompt("hi")).subscribe(response -> {
			}, errors::add);
			Thread.sleep(200);
			subscription.dispose();
			Thread.sleep(500);

			assertThat(errors).isEmpty();
		}
		finally {
			Hooks.resetOnErrorDropped();
		}
	}

	private void assertHttpObservationIsChildOfChatObservation() {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(HTTP_OBSERVATION_NAME)
			.that()
			.hasParentObservationContextMatching(AnthropicChatModelStreamingHttpTests::isChatObservation,
					"parent observation '%s'".formatted(DefaultChatModelObservationConvention.DEFAULT_NAME));
	}

	private static TraceContext spanOf(List<Observation.Context> contexts, String observationName) {
		return contexts.stream().filter(context -> observationName.equals(context.getName())).map(context -> {
			TracingObservationHandler.TracingContext tracingContext = context
				.getRequired(TracingObservationHandler.TracingContext.class);
			return tracingContext.getSpan().context();
		}).findFirst().orElseThrow();
	}

	private static boolean isChatObservation(Observation.ContextView context) {
		return DefaultChatModelObservationConvention.DEFAULT_NAME.equals(context.getName());
	}

	private static MockResponse jsonResponse(String body) {
		return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
	}

	private static MockResponse streamResponse() {
		return new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(STREAM_RESPONSE);
	}

}
