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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import io.micrometer.observation.ObservationRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.AnthropicSetup;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AnthropicHttpClientBuilderCustomizer}.
 *
 * @author Ilayaperumal Gopinathan
 */
class AnthropicHttpClientBuilderCustomizerTests {

	private static final String MESSAGES_RESPONSE = """
			{"id":"msg-test","type":"message","role":"assistant","content":[{"type":"text","text":"Hello"}],"model":"claude-3-5-sonnet-20241022","stop_reason":"end_turn","usage":{"input_tokens":5,"output_tokens":1}}
			""";

	@Test
	void userInterceptorCanOverrideApiKeyHeader() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(mockMessagesResponse());
			server.start();

			AnthropicHttpClientBuilderCustomizer customizer = builder -> builder.interceptor(
					chain -> chain.proceed(chain.request().newBuilder().header("x-api-key", "custom-token").build()));

			AnthropicClient client = AnthropicSetup.setupSyncClient(server.url("/").toString(), "original-api-key",
					Duration.ofSeconds(10), 0, null, null, ObservationRegistry.NOOP, null, null, List.of(customizer));

			client.messages()
				.create(MessageCreateParams.builder()
					.model(Model.CLAUDE_HAIKU_4_5)
					.maxTokens(10)
					.addUserMessage("Hi")
					.build());

			RecordedRequest request = server.takeRequest();
			assertThat(request.getHeader("x-api-key"))
				.as("User interceptor runs last so it can override the API key header")
				.isEqualTo("custom-token");
		}
	}

	@Test
	void multipleCustomizersAppliedInOrder() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(mockMessagesResponse());
			server.start();

			AnthropicHttpClientBuilderCustomizer first = builder -> builder
				.interceptor(chain -> chain.proceed(chain.request().newBuilder().header("x-custom", "first").build()));
			AnthropicHttpClientBuilderCustomizer second = builder -> builder
				.interceptor(chain -> chain.proceed(chain.request().newBuilder().header("x-custom", "second").build()));

			AnthropicClient client = AnthropicSetup.setupSyncClient(server.url("/").toString(), "api-key",
					Duration.ofSeconds(10), 0, null, null, ObservationRegistry.NOOP, null, null,
					List.of(first, second));

			client.messages()
				.create(MessageCreateParams.builder()
					.model(Model.CLAUDE_HAIKU_4_5)
					.maxTokens(10)
					.addUserMessage("Hi")
					.build());

			RecordedRequest request = server.takeRequest();
			assertThat(request.getHeader("x-custom")).as("Second customizer runs after first, so its value wins")
				.isEqualTo("second");
		}
	}

	@Test
	void customizerIsInvokedWhenBuildingViaChatModelBuilder() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(mockMessagesResponse());
			server.start();

			AtomicInteger invocations = new AtomicInteger();
			AnthropicChatModel chatModel = AnthropicChatModel.builder()
				.options(AnthropicChatOptions.builder()
					.baseUrl(server.url("/").toString())
					.apiKey("test-key")
					.model(Model.CLAUDE_HAIKU_4_5.asString())
					.maxTokens(10)
					.build())
				.observationRegistry(ObservationRegistry.NOOP)
				.httpClientBuilderCustomizer(builder -> invocations.incrementAndGet())
				.build();

			chatModel.call(new Prompt("Hi"));

			assertThat(invocations.get())
				.as("customizer must be invoked twice — once for the sync client and once for the async client")
				.isEqualTo(2);
		}
	}

	@Test
	void customizerCanAddCustomRequestHeader() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(mockMessagesResponse());
			server.start();

			AnthropicHttpClientBuilderCustomizer customizer = builder -> builder.interceptor(
					chain -> chain.proceed(chain.request().newBuilder().header("x-tenant-id", "acme").build()));

			AnthropicClient client = AnthropicSetup.setupSyncClient(server.url("/").toString(), "api-key",
					Duration.ofSeconds(10), 0, null, null, ObservationRegistry.NOOP, null, null, List.of(customizer));

			client.messages()
				.create(MessageCreateParams.builder()
					.model(Model.CLAUDE_HAIKU_4_5)
					.maxTokens(10)
					.addUserMessage("Hi")
					.build());

			RecordedRequest request = server.takeRequest();
			assertThat(request.getHeader("x-tenant-id"))
				.as("customizer-registered interceptor must attach the tenant header")
				.isEqualTo("acme");
		}
	}

	private static MockResponse mockMessagesResponse() {
		return new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody(MESSAGES_RESPONSE);
	}

}
