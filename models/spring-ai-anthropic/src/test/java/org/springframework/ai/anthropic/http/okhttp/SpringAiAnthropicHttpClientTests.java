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

package org.springframework.ai.anthropic.http.okhttp;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.anthropic.backends.AnthropicBackend;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.HttpMethod;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpResponse;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests for {@link SpringAiAnthropicHttpClient} connection handling.
 *
 * @author Christian Tzolov
 */
class SpringAiAnthropicHttpClientTests {

	private static final String MESSAGES_RESPONSE = """
			{"id":"msg-test","type":"message","role":"assistant",\
			"content":[{"type":"text","text":"Hello"}],\
			"model":"claude-haiku-4-5","stop_reason":"end_turn",\
			"usage":{"input_tokens":5,"output_tokens":1}}""";

	@Test
	void retryOnConnectionFailureIsEnabledByDefault() {
		// OkHttp's transparent recovery from broken/stale pooled connections must stay on
		// so that servers which close idle keep-alive connections do not surface
		// "unexpected end of stream" / EOFException to callers. See gh-6318.
		AnthropicBackend backend = AnthropicBackend.builder().apiKey("test-key").build();
		OkHttpClient client = SpringAiAnthropicHttpClient.builder().backend(backend).build().getOkHttpClient();

		assertThat(client.retryOnConnectionFailure()).isTrue();
	}

	@Test
	void recoversWhenReusedConnectionWasClosedByServer() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			// First response closes the keep-alive connection, leaving a stale entry in
			// OkHttp's pool; the second request must transparently recover (gh-6318).
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(MESSAGES_RESPONSE)
				.setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(MESSAGES_RESPONSE));
			server.start();

			AnthropicBackend backend = AnthropicBackend.builder().apiKey("test-key").build();
			OkHttpClient client = SpringAiAnthropicHttpClient.builder().backend(backend).build().getOkHttpClient();

			// Pin to the bound loopback address: "localhost" may resolve to both
			// 127.0.0.1 and ::1, and a retry against the address family MockWebServer is
			// not bound to would fail with a spurious "connection refused".
			String url = "http://127.0.0.1:" + server.getPort() + "/v1/messages";

			try (Response first = client.newCall(newPost(url)).execute()) {
				assertThat(first.code()).isEqualTo(200);
				// Drain the body so the connection is eligible for pooling/reuse.
				first.body().string();
			}

			try (Response second = client.newCall(newPost(url)).execute()) {
				assertThat(second.code()).isEqualTo(200);
			}
		}
	}

	@Test
	void followsRedirectsUnlessTheRequestOptionsSayNot() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/target"));
			server.enqueue(new MockResponse().setResponseCode(200));
			server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", "/target"));
			// Only reached if the second redirect is wrongly followed
			server.enqueue(new MockResponse().setResponseCode(200));
			server.start();

			AnthropicBackend backend = AnthropicBackend.builder().apiKey("test-key").build();
			SpringAiAnthropicHttpClient client = SpringAiAnthropicHttpClient.builder().backend(backend).build();
			HttpRequest request = HttpRequest.builder()
				.method(HttpMethod.GET)
				.baseUrl(server.url("/").toString())
				.addPathSegment("start")
				.build();

			try (HttpResponse followed = client.execute(request, RequestOptions.none())) {
				assertThat(followed.statusCode()).isEqualTo(200);
			}
			// The SDK sets this for requests that must stay on the original host, such as
			// the workload identity token exchange.
			try (HttpResponse notFollowed = client.execute(request,
					RequestOptions.builder().followRedirects(false).build())) {
				assertThat(notFollowed.statusCode()).isEqualTo(302);
			}
			assertThat(server.getRequestCount()).isEqualTo(3);
		}
	}

	@Test
	void closeFromAnotherThreadEndsAReadInFlight() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			// A few bytes at a time, so the reading thread is blocked in a read most of
			// the time, as it is between two events of a stream.
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "text/event-stream")
				.setChunkedBody("data: {}\n\n".repeat(500), 10)
				.throttleBody(10, 500, TimeUnit.MILLISECONDS));
			server.start();

			AnthropicBackend backend = AnthropicBackend.builder().apiKey("test-key").build();
			SpringAiAnthropicHttpClient client = SpringAiAnthropicHttpClient.builder().backend(backend).build();
			HttpRequest request = HttpRequest.builder()
				.method(HttpMethod.GET)
				.baseUrl(server.url("/").toString())
				.addPathSegment("stream")
				.build();
			HttpResponse response = client.execute(request, RequestOptions.none());
			InputStream body = response.body();
			CountDownLatch firstByte = new CountDownLatch(1);
			CompletableFuture<Throwable> readEnded = new CompletableFuture<>();
			Thread reader = new Thread(() -> {
				try {
					while (body.read() != -1) {
						firstByte.countDown();
					}
					readEnded.complete(null);
				}
				catch (Throwable ex) {
					readEnded.complete(ex);
				}
			});
			reader.start();
			assertThat(firstByte.await(5, TimeUnit.SECONDS)).isTrue();
			// The first bytes are read by now, and the reader waits for the next ones.
			Thread.sleep(100);

			response.close();

			assertThat(readEnded).succeedsWithin(Duration.ofSeconds(5)).isInstanceOf(IOException.class);
			await()
				.untilAsserted(() -> assertThat(client.getOkHttpClient().connectionPool().connectionCount()).isZero());
		}
	}

	@Test
	void closeWithoutAReadInFlightKeepsTheConnectionForReuse() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(MESSAGES_RESPONSE));
			server.start();

			AnthropicBackend backend = AnthropicBackend.builder().apiKey("test-key").build();
			SpringAiAnthropicHttpClient client = SpringAiAnthropicHttpClient.builder().backend(backend).build();
			HttpRequest request = HttpRequest.builder()
				.method(HttpMethod.GET)
				.baseUrl(server.url("/").toString())
				.addPathSegment("message")
				.build();

			try (HttpResponse response = client.execute(request, RequestOptions.none())) {
				assertThat(new String(response.body().readAllBytes(), StandardCharsets.UTF_8))
					.isEqualTo(MESSAGES_RESPONSE);
			}

			assertThat(client.getOkHttpClient().connectionPool().idleConnectionCount()).isEqualTo(1);
		}
	}

	@Test
	void observationInterceptorDoesNotPinTheObservationRegistry() throws Exception {
		// The SDK Cleaner can retain the HTTP client after the Spring context
		// closes. The interceptor must not keep a strong ref to the context's
		// ObservationRegistry (gh-7455).
		ObservationRegistry registry = ObservationRegistry.create();
		WeakReference<ObservationRegistry> ref = new WeakReference<>(registry);
		SpringAiAnthropicHttpClient client = SpringAiAnthropicHttpClient.builder()
			.backend(AnthropicBackend.builder().apiKey("test-key").build())
			.observationRegistry(registry)
			.build();
		registry = null;

		assertThat(awaitCleared(ref)).as("registry should be collectable once only weakly held by the interceptor")
			.isTrue();
		client.close();
	}

	@Test
	void observationsAreRecordedWhileRegistryIsReachable() throws Exception {
		ObservationRegistry registry = ObservationRegistry.create();
		AtomicInteger starts = new AtomicInteger();
		registry.observationConfig().observationHandler(new ObservationHandler<>() {
			@Override
			public boolean supportsContext(Observation.Context context) {
				return true;
			}

			@Override
			public void onStart(Observation.Context context) {
				starts.incrementAndGet();
			}
		});

		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(MESSAGES_RESPONSE));
			server.start();

			OkHttpClient client = SpringAiAnthropicHttpClient.builder()
				.backend(AnthropicBackend.builder().apiKey("test-key").build())
				.observationRegistry(registry)
				.build()
				.getOkHttpClient();
			String url = "http://127.0.0.1:" + server.getPort() + "/v1/messages";
			try (Response response = client.newCall(newPost(url)).execute()) {
				assertThat(response.code()).isEqualTo(200);
			}
		}

		assertThat(starts.get()).isGreaterThan(0);
	}

	private static boolean awaitCleared(WeakReference<?> ref) throws InterruptedException {
		for (int i = 0; i < 50 && ref.get() != null; i++) {
			System.gc();
			System.runFinalization();
			Thread.sleep(20);
		}
		return ref.get() == null;
	}

	private static Request newPost(String url) {
		return new Request.Builder().url(url).post(RequestBody.create("{}", null)).build();
	}

}
