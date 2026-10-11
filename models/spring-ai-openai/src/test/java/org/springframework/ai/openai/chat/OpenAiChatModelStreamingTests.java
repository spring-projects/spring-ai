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

package org.springframework.ai.openai.chat;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Streaming tests for {@link OpenAiChatModel} against a local server that speaks the
 * OpenAI Chat Completions SSE protocol, exercising the real OpenAI SDK HTTP client.
 *
 * @author Hieu Bui Vinh
 */
class OpenAiChatModelStreamingTests {

	private HttpServer server;

	private final AtomicInteger requestCount = new AtomicInteger();

	private final List<String> requestBodies = new CopyOnWriteArrayList<>();

	private final CountDownLatch releaseRemainingChunks = new CountDownLatch(1);

	private final TestObservationRegistry observationRegistry = TestObservationRegistry.create();

	@BeforeEach
	void setUp() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.start();
	}

	@AfterEach
	void tearDown() {
		this.releaseRemainingChunks.countDown();
		this.server.stop(0);
	}

	@Test
	void streamEmitsChunksBeforeTheResponseCompletes() throws Exception {
		this.server.createContext("/v1/chat/completions", exchange -> {
			try (OutputStream body = startSse(exchange)) {
				writeEvent(body, contentChunk("Hel"));
				writeEvent(body, contentChunk("lo"));
				// Hold the rest of the response until the client has seen the first
				// tokens, like a model that is still generating.
				awaitRelease();
				writeEvent(body, contentChunk(" world"));
				writeEvent(body, finishChunk("stop"));
				writeEvent(body, usageChunk());
				writeEvent(body, "[DONE]");
			}
		});

		BlockingQueue<ChatResponse> received = new LinkedBlockingQueue<>();
		CountDownLatch completed = new CountDownLatch(1);
		Disposable subscription = chatModel().stream(new Prompt("Say hello"))
			.doOnTerminate(completed::countDown)
			.subscribe(received::add);
		List<ChatResponse> responses = new ArrayList<>();
		try {
			ChatResponse first = received.poll(10, TimeUnit.SECONDS);
			assertThat(first).as("first chunk must be emitted while the model is still generating").isNotNull();
			assertThat(first.getResult().getOutput().getText()).isEqualTo("Hel");
			responses.add(first);

			this.releaseRemainingChunks.countDown();
			assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
		}
		finally {
			subscription.dispose();
		}

		received.drainTo(responses);
		assertThat(concatText(responses)).isEqualTo("Hello world");
		assertThat(responses.get(responses.size() - 1).getMetadata().getUsage().getTotalTokens()).isEqualTo(7);

		TestObservationRegistryAssert.assertThat(this.observationRegistry)
			.hasObservationWithNameEqualTo(DefaultChatModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.RESPONSE_ID.asString(), "chatcmpl-1")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.RESPONSE_FINISH_REASONS.asString(), "[\"STOP\"]")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(), "5")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_OUTPUT_TOKENS.asString(), "2")
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(), "7")
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@Test
	void streamExecutesStreamedToolCallsAndStreamsTheFollowUpAnswer() throws Exception {
		this.releaseRemainingChunks.countDown();
		this.server.createContext("/v1/chat/completions", exchange -> {
			this.requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			try (OutputStream body = startSse(exchange)) {
				if (this.requestCount.getAndIncrement() == 0) {
					writeEvent(body, toolCallChunk("{\"id\":\"call_1\",\"type\":\"function\","
							+ "\"function\":{\"name\":\"getWeather\",\"arguments\":\"\"}}"));
					writeEvent(body, toolCallChunk("{\"function\":{\"arguments\":\"{\\\"city\\\":\"}}"));
					writeEvent(body, toolCallChunk("{\"function\":{\"arguments\":\"\\\"Paris\\\"}\"}}"));
					writeEvent(body, finishChunk("tool_calls"));
				}
				else {
					writeEvent(body, contentChunk("Sunny"));
					writeEvent(body, contentChunk(" in Paris"));
					writeEvent(body, finishChunk("stop"));
				}
				writeEvent(body, usageChunk());
				writeEvent(body, "[DONE]");
			}
		});

		List<String> toolInputs = new CopyOnWriteArrayList<>();
		OpenAiChatOptions options = baseOptions()
			.toolCallbacks(FunctionToolCallback.builder("getWeather", (WeatherRequest request) -> {
				toolInputs.add(request.city());
				return "sunny";
			}).description("Get the weather").inputType(WeatherRequest.class).build())
			.build();

		List<ChatResponse> responses = chatModel().stream(new Prompt("Weather in Paris?", options))
			.collectList()
			.block(Duration.ofSeconds(10));

		assertThat(toolInputs).containsExactly("Paris");
		assertThat(this.requestCount).hasValue(2);
		assertThat(this.requestBodies.get(1)).contains("\"tool_call_id\":\"call_1\"").contains("sunny");
		assertThat(responses).isNotNull();
		assertThat(concatText(responses)).isEqualTo("Sunny in Paris");
		assertThat(responses.stream().map(ChatResponse::getResult).filter(Objects::nonNull))
			.noneMatch(g -> g.getOutput().hasToolCalls());
		// Usage accumulates across both model round trips.
		assertThat(responses.get(responses.size() - 1).getMetadata().getUsage().getTotalTokens()).isEqualTo(14);
	}

	private static String concatText(List<ChatResponse> responses) {
		return responses.stream()
			.map(ChatResponse::getResult)
			.filter(Objects::nonNull)
			.map(Generation::getOutput)
			.map(message -> Objects.requireNonNullElse(message.getText(), ""))
			.reduce("", String::concat);
	}

	private OpenAiChatModel chatModel() {
		return OpenAiChatModel.builder()
			.options(baseOptions().build())
			.observationRegistry(this.observationRegistry)
			.build();
	}

	private OpenAiChatOptions.Builder baseOptions() {
		return OpenAiChatOptions.builder()
			.baseUrl("http://127.0.0.1:" + this.server.getAddress().getPort() + "/v1")
			.apiKey("test-key")
			.model("gpt-4o-mini")
			.maxRetries(0);
	}

	private void awaitRelease() {
		try {
			this.releaseRemainingChunks.await(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static OutputStream startSse(HttpExchange exchange) throws IOException {
		exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
		exchange.sendResponseHeaders(200, 0);
		return exchange.getResponseBody();
	}

	private static void writeEvent(OutputStream body, String data) throws IOException {
		body.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
		body.flush();
	}

	private static String chunk(String choices) {
		return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
				+ "\"model\":\"gpt-4o-mini\",\"choices\":" + choices + "}";
	}

	private static String contentChunk(String content) {
		return chunk("[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"" + content
				+ "\"},\"finish_reason\":null}]");
	}

	private static String toolCallChunk(String toolCall) {
		return chunk("[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"tool_calls\":[{\"index\":0,"
				+ toolCall.substring(1) + "]},\"finish_reason\":null}]");
	}

	private static String finishChunk(String finishReason) {
		return chunk("[{\"index\":0,\"delta\":{},\"finish_reason\":\"" + finishReason + "\"}]");
	}

	private static String usageChunk() {
		return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"created\":1,"
				+ "\"model\":\"gpt-4o-mini\",\"choices\":[],"
				+ "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":2,\"total_tokens\":7}}";
	}

	record WeatherRequest(String city) {
	}

}
