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

package org.springframework.ai.openai.http.okhttp;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.openai.client.OpenAIClient;
import com.openai.core.Timeout;
import com.openai.errors.OpenAIIoException;
import com.openai.models.ChatModel;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.setup.OpenAiSetup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OpenAiHttpClientBuilderCustomizer}.
 *
 * @author Thomas Vitale
 */
class OpenAiHttpClientBuilderCustomizerTests {

	private static final String CHAT_COMPLETION_RESPONSE = """
			{"id":"chatcmpl-test","object":"chat.completion","created":1700000000,"model":"gpt-4","choices":[{"index":0,"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":1,"total_tokens":6}}
			""";

	@Test
	void userInterceptorRunsAfterNoAuthStripper() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(mockChatCompletion());
			server.start();

			OpenAiHttpClientBuilderCustomizer customizer = builder -> builder.interceptor(chain -> chain
				.proceed(chain.request().newBuilder().header("Authorization", "Bearer oauth2-token").build()));

			OpenAIClient client = OpenAiSetup.setupSyncClient(server.url("/v1").toString(), "", null, null, null, null,
					false, false, "gpt-4", Duration.ofSeconds(10), 0, null, null, ObservationRegistry.NOOP, null,
					List.of(customizer));

			client.chat()
				.completions()
				.create(ChatCompletionCreateParams.builder().model(ChatModel.GPT_4).addUserMessage("Hi").build());

			RecordedRequest request = server.takeRequest();
			assertThat(request.getHeader("Authorization"))
				.as("User interceptor sets Authorization after the no-auth stripper, so the bearer token is preserved")
				.isEqualTo("Bearer oauth2-token");
		}
	}

	@Test
	void userInterceptorRunsWithApiKeyAndCanOverrideAuthorization() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(mockChatCompletion());
			server.start();

			OpenAiHttpClientBuilderCustomizer customizer = builder -> builder.interceptor(chain -> chain
				.proceed(chain.request().newBuilder().header("Authorization", "Bearer user-override").build()));

			OpenAIClient client = OpenAiSetup.setupSyncClient(server.url("/v1").toString(), "real-api-key", null, null,
					null, null, false, false, "gpt-4", Duration.ofSeconds(10), 0, null, null, ObservationRegistry.NOOP,
					null, List.of(customizer));

			client.chat()
				.completions()
				.create(ChatCompletionCreateParams.builder().model(ChatModel.GPT_4).addUserMessage("Hi").build());

			RecordedRequest request = server.takeRequest();
			assertThat(request.getHeader("Authorization"))
				.as("User interceptor runs last so it can override the SDK-attached API key header")
				.isEqualTo("Bearer user-override");
		}
	}

	@Test
	void clientTimeoutSetByCustomizerIsNotDiscarded() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			// The server accepts the request, then delays its response past the 1s
			// timeout. Bounded, so the test fails rather than hangs if the timeout is
			// not honoured.
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(CHAT_COMPLETION_RESPONSE)
				.setHeadersDelay(5, TimeUnit.SECONDS));
			server.start();

			OpenAiHttpClientBuilderCustomizer customizer = builder -> builder.timeout(Timeout.builder()
				.connect(Duration.ofSeconds(1))
				.read(Duration.ofSeconds(1))
				.write(Duration.ofSeconds(1))
				.request(Duration.ofSeconds(1))
				.build());

			// The ClientOptions timeout is deliberately far larger: the SDK
			// copies it into every call's RequestOptions, and it must not clobber the
			// customizer's value.
			OpenAIClient client = OpenAiSetup.setupSyncClient(server.url("/v1").toString(), "real-api-key", null, null,
					null, null, false, false, "gpt-4", Duration.ofMinutes(10), 0, null, null, ObservationRegistry.NOOP,
					null, List.of(customizer));

			assertThatThrownBy(() -> client.chat()
				.completions()
				.create(ChatCompletionCreateParams.builder().model(ChatModel.GPT_4).addUserMessage("Hi").build()))
				.as("the 1s timeout configured through the customizer must bound the call")
				.isInstanceOf(OpenAIIoException.class);
		}
	}

	private static MockResponse mockChatCompletion() {
		return new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody(CHAT_COMPLETION_RESPONSE);
	}

}
