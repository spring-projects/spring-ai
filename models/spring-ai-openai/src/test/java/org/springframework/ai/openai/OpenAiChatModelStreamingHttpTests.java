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

package org.springframework.ai.openai;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.core.http.StreamResponse;
import com.openai.errors.BadRequestException;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.services.blocking.ChatService;
import com.openai.services.blocking.chat.ChatCompletionService;
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
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
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
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests the HTTP side of {@link OpenAiChatModel} streaming against a fake OpenAI server.
 * Most tests check that the HTTP observation for a request to OpenAI is a child of the
 * chat model observation. The HTTP observation is what writes the outgoing
 * {@code traceparent} header, so when it has the right parent, the request joins the
 * caller's trace.
 *
 * @author sajeevKR
 */
class OpenAiChatModelStreamingHttpTests {

	private static final String HTTP_OBSERVATION_NAME = "okhttp.requests";

	private static final String COMPLETION_RESPONSE = """
			{"id":"chatcmpl-1","object":"chat.completion","created":1700000000,"model":"gpt-4o-mini",\
			"choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}],\
			"usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
			""";

	private static final String STREAM_RESPONSE = """
			data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4o-mini",\
			"choices":[{"index":0,"delta":{"role":"assistant","content":"ok"},"finish_reason":null}]}

			data: {"id":"chatcmpl-1","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4o-mini",\
			"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

			data: [DONE]

			""";

	private static final String ERROR_RESPONSE = """
			{"error":{"message":"bad request","type":"invalid_request_error","param":null,"code":null}}
			""";

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	private final MockWebServer server = new MockWebServer();

	private OpenAiChatModel chatModel;

	@BeforeEach
	void setUp() throws Exception {
		this.server.start();
		this.chatModel = chatModel(this.observationRegistry);
	}

	@AfterEach
	void tearDown() throws Exception {
		this.server.shutdown();
	}

	@Test
	void httpObservationIsChildOfChatObservationForCall() {
		this.server.enqueue(jsonResponse(COMPLETION_RESPONSE));

		ChatResponse response = this.chatModel.call(new Prompt("hi"));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("ok");
		assertHttpObservationIsChildOfChatObservation();
	}

	@Test
	void httpObservationIsChildOfChatObservationForStream() {
		this.server.enqueue(streamResponse());

		List<ChatResponse> responses = this.chatModel.stream(new Prompt("hi")).collectList().block();

		assertThat(responses).isNotEmpty();
		assertThat(responses.stream().map(response -> response.getResult().getOutput().getText()))
			.containsSubsequence("ok");
		assertHttpObservationIsChildOfChatObservation();
	}

	@Test
	void streamRequestCarriesTraceparentOfChatSpan() throws Exception {
		ContextPropagators propagators = ContextPropagators.create(W3CTraceContextPropagator.getInstance());
		// Without a span processor, the SDK hands out a no-op tracer whose spans are
		// all invalid.
		io.opentelemetry.api.trace.Tracer otelTracer = SdkTracerProvider.builder()
			.addSpanProcessor(SimpleSpanProcessor.create(SpanExporter.composite()))
			.build()
			.get("test");
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
		this.server.enqueue(streamResponse());

		chatModel(tracingRegistry).stream(new Prompt("hi")).collectList().block();

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

	@Test
	@SuppressWarnings("unchecked")
	void streamOpenedAfterCancelIsClosed() throws Exception {
		CountDownLatch requestSent = new CountDownLatch(1);
		CountDownLatch replyAllowed = new CountDownLatch(1);
		AtomicBoolean closed = new AtomicBoolean();
		OpenAIClient openAiClient = mock(OpenAIClient.class);
		ChatService chatService = mock(ChatService.class);
		ChatCompletionService completionService = mock(ChatCompletionService.class);
		given(openAiClient.chat()).willReturn(chatService);
		given(chatService.completions()).willReturn(completionService);
		given(completionService.createStreaming(any(ChatCompletionCreateParams.class), any(RequestOptions.class)))
			.willAnswer(invocation -> {
				requestSent.countDown();
				// The cancel may interrupt this thread: keep waiting, as a request that
				// is already on the wire would.
				awaitIgnoringInterrupts(replyAllowed);
				return new StreamResponse<ChatCompletionChunk>() {

					@Override
					public Stream<ChatCompletionChunk> stream() {
						return Stream.empty();
					}

					@Override
					public void close() {
						closed.set(true);
					}

				};
			});
		OpenAiChatModel model = OpenAiChatModel.builder()
			.openAiClient(openAiClient)
			.options(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
			.build();

		Disposable subscription = model.stream(new Prompt("hi")).subscribe();
		assertThat(requestSent.await(5, TimeUnit.SECONDS)).isTrue();
		subscription.dispose();
		replyAllowed.countDown();

		await().atMost(Duration.ofSeconds(5)).untilTrue(closed);
	}

	private OpenAiChatModel chatModel(ObservationRegistry observationRegistry) {
		return OpenAiChatModel.builder()
			.options(OpenAiChatOptions.builder()
				.baseUrl(this.server.url("/v1").toString())
				.apiKey("test-key")
				.model("gpt-4o-mini")
				.build())
			.observationRegistry(observationRegistry)
			.build();
	}

	private void assertHttpObservationIsChildOfChatObservation() {
		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(HTTP_OBSERVATION_NAME)
			.that()
			.hasParentObservationContextMatching(OpenAiChatModelStreamingHttpTests::isChatObservation,
					"parent observation '%s'".formatted(DefaultChatModelObservationConvention.DEFAULT_NAME));
	}

	private static void awaitIgnoringInterrupts(CountDownLatch latch) {
		boolean interrupted = false;
		while (true) {
			try {
				latch.await();
				break;
			}
			catch (InterruptedException ex) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
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
