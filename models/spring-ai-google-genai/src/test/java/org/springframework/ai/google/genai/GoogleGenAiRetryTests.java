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

package org.springframework.ai.google.genai;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.genai.Client;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry tests for {@link GoogleGenAiChatModel}. A local HTTP server plays the Gemini API
 * and answers with a scripted sequence of status codes, and the SDK's own retries are
 * turned off, so only Spring AI's {@link RetryTemplate} can retry.
 *
 * @author Mark Pollack
 * @author Shreya Wanisha
 */
public class GoogleGenAiRetryTests {

	private static final String SUCCESS_BODY = """
			{"candidates":[{"content":{"role":"model","parts":[{"text":"Hello"}]},"finishReason":"STOP","index":0}],
			"usageMetadata":{"promptTokenCount":1,"candidatesTokenCount":1,"totalTokenCount":2}}""";

	private final Deque<Integer> statuses = new ArrayDeque<>();

	private final AtomicInteger requests = new AtomicInteger();

	private HttpServer server;

	private TestRetryListener retryListener;

	private TestGoogleGenAiGeminiChatModel chatModel;

	@BeforeEach
	public void setUp() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		this.server.createContext("/", exchange -> {
			this.requests.incrementAndGet();
			int status = this.statuses.isEmpty() ? 200 : this.statuses.poll();
			byte[] body = ((status == 200) ? SUCCESS_BODY : errorBody(status)).getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		this.server.start();

		Client genAiClient = Client.builder()
			.apiKey("test-key")
			.httpOptions(HttpOptions.builder()
				.baseUrl("http://127.0.0.1:" + this.server.getAddress().getPort())
				.retryOptions(HttpRetryOptions.builder().attempts(1).httpStatusCodes(List.of()).build())
				.build())
			.build();

		RetryTemplate retryTemplate = new RetryTemplate(RetryPolicy.builder()
			.maxRetries(2)
			.includes(TransientAiException.class)
			.delay(Duration.ofMillis(1))
			.build());
		this.retryListener = new TestRetryListener();
		retryTemplate.setRetryListener(this.retryListener);

		this.chatModel = new TestGoogleGenAiGeminiChatModel(genAiClient,
				GoogleGenAiChatOptions.builder()
					.temperature(0.7)
					.topP(1.0)
					.model(GoogleGenAiChatModel.ChatModel.GEMINI_3_1_PRO_PREVIEW.getValue())
					.build(),
				retryTemplate);
	}

	@AfterEach
	public void tearDown() {
		this.server.stop(0);
	}

	@Test
	public void serverErrorIsRetried() {
		this.statuses.addAll(List.of(503, 503));

		ChatResponse response = this.chatModel.call(new Prompt("hi"));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello");
		assertThat(this.requests.get()).isEqualTo(3);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
	}

	@Test
	public void rateLimitIsRetried() {
		this.statuses.add(429);

		ChatResponse response = this.chatModel.call(new Prompt("hi"));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello");
		assertThat(this.requests.get()).isEqualTo(2);
	}

	@Test
	public void serverErrorThatPersistsFailsAsTransient() {
		this.statuses.addAll(List.of(503, 503, 503));

		assertThatThrownBy(() -> this.chatModel.call(new Prompt("hi"))).isInstanceOf(TransientAiException.class)
			.hasMessageContaining("503")
			.hasCauseInstanceOf(ServerException.class);
		assertThat(this.requests.get()).isEqualTo(3);
	}

	@Test
	public void clientErrorIsNotRetried() {
		this.statuses.add(404);

		assertThatThrownBy(() -> this.chatModel.call(new Prompt("hi"))).isInstanceOf(NonTransientAiException.class)
			.hasMessageContaining("404")
			.hasCauseInstanceOf(ClientException.class);
		assertThat(this.requests.get()).isEqualTo(1);
	}

	private static String errorBody(int status) {
		return """
				{"error":{"code":%d,"message":"Scripted error","status":"%s"}}""".formatted(status,
				(status >= 500) ? "UNAVAILABLE" : (status == 429) ? "RESOURCE_EXHAUSTED" : "NOT_FOUND");
	}

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public void beforeRetry(final RetryPolicy retryPolicy, final Retryable<?> retryable) {
			// Count each retry attempt
			this.onErrorRetryCount++;
		}

		@Override
		public void onRetrySuccess(final RetryPolicy retryPolicy, final Retryable<?> retryable, final Object result) {
			// Count successful retries - we increment when we succeed after a failure
			this.onSuccessRetryCount++;
		}

	}

}
