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

import com.anthropic.backends.AnthropicBackend;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

	private static Request newPost(String url) {
		return new Request.Builder().url(url).post(RequestBody.create("{}", null)).build();
	}

}
