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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpResponseFor;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.services.async.MessageServiceAsync;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Verifies that {@link AnthropicChatOptions#getTimeout()} actually bounds the HTTP call
 * whether it's set at {@link AnthropicChatModel} construction time, as a
 * {@link ChatClient} default option, or as a per-request {@code ChatClient} override —
 * not just at construction time.
 *
 * @author seeun0210
 */
class AnthropicChatModelTimeoutTests {

	private static final String MESSAGES_RESPONSE = """
			{"id":"msg-test","type":"message","role":"assistant","content":[{"type":"text","text":"ok"}],"model":"claude-haiku-4-5-20251001","stop_reason":"end_turn","usage":{"input_tokens":1,"output_tokens":1}}
			""";

	@Test
	void constructionTimeTimeoutBoundsTheCall() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setHeadersDelay(3, TimeUnit.SECONDS)
				.setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(MESSAGES_RESPONSE));
			server.start();

			AnthropicChatModel chatModel = AnthropicChatModel.builder()
				.options(AnthropicChatOptions.builder()
					.baseUrl(server.url("/").toString())
					.apiKey("test-key")
					.model("claude-haiku-4-5-20251001")
					.maxTokens(10)
					.timeout(Duration.ofMillis(500))
					.build())
				.build();

			// Root cause class varies across the SDK's automatic retries
			// (SocketTimeoutException
			// on a fresh connection vs. SocketException "Socket closed" when a retry
			// races a
			// connection the timeout already tore down) — assert on the stable wrapper
			// type
			// instead of pinning an exact root cause class.
			assertThatThrownBy(() -> chatModel.call(new Prompt("hi")))
				.as("a 500ms construction-time timeout must interrupt a call whose response is delayed 3s")
				.isInstanceOf(AnthropicIoException.class);
		}
	}

	@Test
	void perRequestChatClientTimeoutOverridesConstructionTimeTimeout() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			// Construction-time timeout is intentionally too short (500ms) for the 2s
			// delayed response below. A per-request ChatClient override of 5s should
			// let the call succeed instead of failing at the construction-time bound.
			server.enqueue(new MockResponse().setHeadersDelay(2, TimeUnit.SECONDS)
				.setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(MESSAGES_RESPONSE));
			server.start();

			AnthropicChatModel chatModel = AnthropicChatModel.builder()
				.options(AnthropicChatOptions.builder()
					.baseUrl(server.url("/").toString())
					.apiKey("test-key")
					.model("claude-haiku-4-5-20251001")
					.maxTokens(10)
					.timeout(Duration.ofMillis(500))
					.build())
				.build();
			ChatClient chatClient = ChatClient.builder(chatModel).build();

			assertThatCode(() -> {
				String content = chatClient.prompt()
					.user("hi")
					.options(AnthropicChatOptions.builder().timeout(Duration.ofSeconds(5)))
					.call()
					.content();
				assertThat(content).isEqualTo("ok");
			}).as("a 5s per-request ChatClient timeout must override the 500ms construction-time default")
				.doesNotThrowAnyException();
		}
	}

	@Test
	void chatClientDefaultOptionsTimeoutOverridesConstructionTimeTimeout() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setHeadersDelay(2, TimeUnit.SECONDS)
				.setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(MESSAGES_RESPONSE));
			server.start();

			AnthropicChatModel chatModel = AnthropicChatModel.builder()
				.options(AnthropicChatOptions.builder()
					.baseUrl(server.url("/").toString())
					.apiKey("test-key")
					.model("claude-haiku-4-5-20251001")
					.maxTokens(10)
					.timeout(Duration.ofMillis(500))
					.build())
				.build();
			ChatClient chatClient = ChatClient.builder(chatModel)
				.defaultOptions(AnthropicChatOptions.builder().timeout(Duration.ofSeconds(5)))
				.build();

			assertThatCode(() -> chatClient.prompt().user("hi").call().content())
				.as("a 5s ChatClient default-options timeout must override the 500ms construction-time default")
				.doesNotThrowAnyException();
		}
	}

	/**
	 * Mirrors {@link #perRequestChatClientTimeoutOverridesConstructionTimeTimeout()} for
	 * the streaming ({@code internalStream()}) code path. A real delayed-response
	 * MockWebServer setup isn't used here because it would additionally require
	 * fabricating a valid Anthropic SSE event stream; asserting the
	 * {@link RequestOptions} actually passed to {@code createStreaming(...)} is a more
	 * direct and no less faithful check that the fix applies to both call sites.
	 */
	@Test
	@SuppressWarnings("unchecked")
	void perRequestChatClientTimeoutIsForwardedOnTheStreamingPath() {
		AnthropicClientAsync anthropicClientAsync = mock(AnthropicClientAsync.class);
		MessageServiceAsync messageServiceAsync = mock(MessageServiceAsync.class);
		MessageServiceAsync.WithRawResponse messageServiceAsyncWithRawResponse = mock(
				MessageServiceAsync.WithRawResponse.class);

		StreamResponse<RawMessageStreamEvent> streamResponse = mock(StreamResponse.class);
		given(streamResponse.stream()).willReturn(Stream.empty());
		HttpResponseFor<StreamResponse<RawMessageStreamEvent>> rawResponse = mock(HttpResponseFor.class);
		given(rawResponse.parse()).willReturn(streamResponse);
		given(rawResponse.headers()).willReturn(Headers.builder().build());

		given(anthropicClientAsync.messages()).willReturn(messageServiceAsync);
		given(messageServiceAsync.withRawResponse()).willReturn(messageServiceAsyncWithRawResponse);
		given(messageServiceAsyncWithRawResponse.createStreaming(any(MessageCreateParams.class),
				any(RequestOptions.class)))
			.willReturn(CompletableFuture.completedFuture(rawResponse));

		AnthropicChatModel chatModel = AnthropicChatModel.builder()
			.anthropicClientAsync(anthropicClientAsync)
			.options(AnthropicChatOptions.builder()
				.model("claude-haiku-4-5-20251001")
				.maxTokens(10)
				.timeout(Duration.ofMillis(500))
				.build())
			.build();
		ChatClient chatClient = ChatClient.builder(chatModel).build();

		Prompt prompt = new Prompt(List.of(new UserMessage("hi")),
				AnthropicChatOptions.builder().timeout(Duration.ofSeconds(5)).build());
		new MessageAggregator().aggregate(chatClient.prompt(prompt).stream().chatResponse(), response -> {
		}).collectList().block();

		ArgumentCaptor<RequestOptions> captor = ArgumentCaptor.forClass(RequestOptions.class);
		verify(messageServiceAsyncWithRawResponse).createStreaming(any(MessageCreateParams.class), captor.capture());
		assertThat(captor.getValue().getTimeout().request()).isEqualTo(Duration.ofSeconds(5));
	}

}
