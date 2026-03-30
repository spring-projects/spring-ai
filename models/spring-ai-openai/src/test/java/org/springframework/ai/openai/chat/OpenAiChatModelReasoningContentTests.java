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

import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for verifying that reasoningContent is included in AssistantMessage metadata for
 * non-streaming (internalCall) API calls.
 *
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5693">GitHub Issue
 * #5693</a>
 */
@RestClientTest(OpenAiChatModelReasoningContentTests.Config.class)
public class OpenAiChatModelReasoningContentTests {

	private static final String TEST_API_KEY = "sk-1234567890";

	@Autowired
	private OpenAiChatModel openAiChatModel;

	@Autowired
	private MockRestServiceServer server;

	@AfterEach
	void resetMockServer() {
		this.server.reset();
	}

	/**
	 * Test that verifies reasoningContent is included in the metadata for non-streaming
	 * API calls (internalCall method). This test reproduces the bug reported in GitHub
	 * issue #5693 where the non-streaming API does not include reasoningContent in the
	 * AssistantMessage metadata, while the streaming API correctly includes it.
	 */
	@Test
	void nonStreamingCallShouldIncludeReasoningContentInMetadata() {
		prepareMockWithReasoningContent();

		Prompt prompt = new Prompt("What is 2 + 2? Think step by step.");

		ChatResponse response = this.openAiChatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotNull();

		var metadata = response.getResult().getOutput().getMetadata();
		assertThat(metadata).isNotNull();

		// This assertion will FAIL before the fix is applied
		assertThat(metadata).containsKey("reasoningContent");

		String reasoningContent = (String) metadata.get("reasoningContent");
		assertThat(reasoningContent).isEqualTo("Let me think about this step by step. 2 + 2 = 4.");
	}

	/**
	 * Test that verifies empty reasoningContent is handled correctly when the model does
	 * not return reasoning content.
	 */
	@Test
	void nonStreamingCallShouldHandleEmptyReasoningContent() {
		prepareMockWithoutReasoningContent();

		Prompt prompt = new Prompt("Hello");

		ChatResponse response = this.openAiChatModel.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getResult()).isNotNull();
		assertThat(response.getResult().getOutput()).isNotNull();

		var metadata = response.getResult().getOutput().getMetadata();
		assertThat(metadata).isNotNull();

		// This assertion will FAIL before the fix is applied
		assertThat(metadata).containsKey("reasoningContent");

		String reasoningContent = (String) metadata.get("reasoningContent");
		assertThat(reasoningContent).isEmpty();
	}

	private void prepareMockWithReasoningContent() {
		String jsonResponse = "{" + "\"id\": \"chatcmpl-123\"," + "\"object\": \"chat.completion\","
				+ "\"created\": 1677652288," + "\"model\": \"deepseek-r1\"," + "\"choices\": [{" + "\"index\": 0,"
				+ "\"message\": {" + "\"role\": \"assistant\"," + "\"content\": \"The answer is 4.\","
				+ "\"reasoning_content\": \"Let me think about this step by step. 2 + 2 = 4.\"" + "},"
				+ "\"finish_reason\": \"stop\"" + "}]," + "\"usage\": {" + "\"prompt_tokens\": 10,"
				+ "\"completion_tokens\": 20," + "\"total_tokens\": 30" + "}" + "}";

		this.server.expect(requestTo(StringContains.containsString("/v1/chat/completions")))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));
	}

	private void prepareMockWithoutReasoningContent() {
		String jsonResponse = "{" + "\"id\": \"chatcmpl-456\"," + "\"object\": \"chat.completion\","
				+ "\"created\": 1677652288," + "\"model\": \"gpt-4\"," + "\"choices\": [{" + "\"index\": 0,"
				+ "\"message\": {" + "\"role\": \"assistant\"," + "\"content\": \"Hello! How can I help you today?\""
				+ "}," + "\"finish_reason\": \"stop\"" + "}]," + "\"usage\": {" + "\"prompt_tokens\": 5,"
				+ "\"completion_tokens\": 10," + "\"total_tokens\": 15" + "}" + "}";

		this.server.expect(requestTo(StringContains.containsString("/v1/chat/completions")))
			.andExpect(method(HttpMethod.POST))
			.andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiApi chatCompletionApi(RestClient.Builder builder) {
			return OpenAiApi.builder()
				.apiKey(TEST_API_KEY)
				.restClientBuilder(builder)
				.webClientBuilder(WebClient.builder())
				.build();
		}

		@Bean
		public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
			return OpenAiChatModel.builder().openAiApi(openAiApi).build();
		}

	}

}
