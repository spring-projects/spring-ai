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

import java.io.IOException;
import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests that connection options ({@code baseUrl}, {@code apiKey}, {@code organizationId})
 * passed with the prompt override the ones the model was built with.
 *
 * @author Kshitij Kaithal
 */
class OpenAiChatModelConnectionOptionsTests {

	private static final String CHAT_COMPLETION_RESPONSE = """
			{"id":"chatcmpl-test","object":"chat.completion","created":1700000000,"model":"gpt-4o-mini","choices":[{"index":0,"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":1,"total_tokens":6}}
			""";

	private static final String CHAT_COMPLETION_STREAM_RESPONSE = """
			data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}

			data: {"id":"chatcmpl-test","object":"chat.completion.chunk","created":1700000000,"model":"gpt-4o-mini","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}

			data: [DONE]

			""";

	private MockWebServer defaultServer;

	private MockWebServer overrideServer;

	private OpenAiChatModel chatModel;

	@BeforeEach
	void setUp() throws IOException {
		this.defaultServer = new MockWebServer();
		this.defaultServer.start();
		this.overrideServer = new MockWebServer();
		this.overrideServer.start();
		this.chatModel = OpenAiChatModel.builder()
			.options(OpenAiChatOptions.builder()
				.baseUrl(this.defaultServer.url("/v1").toString())
				.apiKey("default-key")
				.organizationId("default-org")
				.model("gpt-4o-mini")
				.maxRetries(0)
				.build())
			.build();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.defaultServer.shutdown();
		this.overrideServer.shutdown();
	}

	@Test
	void callUsesDefaultConnectionWhenNotOverridden() throws InterruptedException {
		this.defaultServer.enqueue(jsonResponse());

		this.chatModel.call(new Prompt("Hi"));

		RecordedRequest request = this.defaultServer.takeRequest();
		assertThat(request.getHeader("Authorization")).isEqualTo("Bearer default-key");
		assertThat(request.getHeader("OpenAI-Organization")).isEqualTo("default-org");
		assertThat(this.overrideServer.getRequestCount()).isZero();
	}

	@Test
	void callUsesConnectionOptionsFromPrompt() throws InterruptedException {
		this.overrideServer.enqueue(jsonResponse());

		ChatResponse response = this.chatModel.call(new Prompt("Hi", overrideOptions()));

		assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello");
		RecordedRequest request = this.overrideServer.takeRequest();
		assertThat(request.getPath()).isEqualTo("/override/chat/completions");
		assertThat(request.getHeader("Authorization")).isEqualTo("Bearer override-key");
		assertThat(request.getHeader("OpenAI-Organization")).isEqualTo("override-org");
		assertThat(this.defaultServer.getRequestCount()).isZero();
	}

	@Test
	void streamUsesConnectionOptionsFromPrompt() throws InterruptedException {
		this.overrideServer.enqueue(new MockResponse().setHeader("Content-Type", "text/event-stream")
			.setBody(CHAT_COMPLETION_STREAM_RESPONSE));

		List<ChatResponse> responses = this.chatModel.stream(new Prompt("Hi", overrideOptions())).collectList().block();

		assertThat(responses).isNotEmpty();
		RecordedRequest request = this.overrideServer.takeRequest();
		assertThat(request.getPath()).isEqualTo("/override/chat/completions");
		assertThat(request.getHeader("Authorization")).isEqualTo("Bearer override-key");
		assertThat(this.defaultServer.getRequestCount()).isZero();
	}

	@Test
	void chatClientUsesConnectionOptionsFromRequest() throws InterruptedException {
		this.overrideServer.enqueue(jsonResponse());

		String content = ChatClient.create(this.chatModel)
			.prompt("Hi")
			.options(OpenAiChatOptions.builder()
				.baseUrl(this.overrideServer.url("/override").toString())
				.apiKey("override-key"))
			.call()
			.content();

		assertThat(content).isEqualTo("Hello");
		RecordedRequest request = this.overrideServer.takeRequest();
		assertThat(request.getHeader("Authorization")).isEqualTo("Bearer override-key");
		// Not overridden on the request, so the default from the model still applies.
		assertThat(request.getHeader("OpenAI-Organization")).isEqualTo("default-org");
		assertThat(this.defaultServer.getRequestCount()).isZero();
	}

	@Test
	void callUsesMicrosoftFoundryApiKeyFromPrompt() throws InterruptedException {
		OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
			.baseUrl(this.defaultServer.url("/").toString())
			.apiKey("default-key")
			.microsoftFoundry(true)
			.deploymentName("gpt-4o-mini")
			.model("gpt-4o-mini")
			.maxRetries(0)
			.build();
		OpenAiChatModel foundryChatModel = OpenAiChatModel.builder().options(defaultOptions).build();
		this.defaultServer.enqueue(jsonResponse());

		foundryChatModel.call(new Prompt("Hi", defaultOptions.mutate().apiKey("override-key").build()));

		RecordedRequest request = this.defaultServer.takeRequest();
		assertThat(request.getHeader("api-key")).isEqualTo("override-key");
		assertThat(request.getHeader("Authorization")).isNull();
	}

	@Test
	void callRejectsEmptyApiKeyFromPrompt() {
		Prompt prompt = new Prompt("Hi", OpenAiChatOptions.builder().apiKey("").model("gpt-4o-mini").build());

		assertThatIllegalArgumentException().isThrownBy(() -> this.chatModel.call(prompt))
			.withMessageContaining("An empty API key is not supported in the prompt options");
		assertThat(this.defaultServer.getRequestCount()).isZero();
	}

	private OpenAiChatOptions overrideOptions() {
		return OpenAiChatOptions.builder()
			.baseUrl(this.overrideServer.url("/override").toString())
			.apiKey("override-key")
			.organizationId("override-org")
			.model("gpt-4o-mini")
			.build();
	}

	private static MockResponse jsonResponse() {
		return new MockResponse().setHeader("Content-Type", "application/json").setBody(CHAT_COMPLETION_RESPONSE);
	}

}
