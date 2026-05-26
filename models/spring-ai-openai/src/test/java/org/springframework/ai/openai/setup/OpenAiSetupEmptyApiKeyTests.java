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

package org.springframework.ai.openai.setup;

import java.time.Duration;

import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that an empty API key (or {@link NoopApiKey}) results in no {@code Authorization}
 * header being sent to the server.
 *
 * @author Ilayaperumal Gopinathan
 */
public class OpenAiSetupEmptyApiKeyTests {

	private static final String CHAT_COMPLETION_RESPONSE = "{\"id\":\"chatcmpl-test\",\"object\":\"chat.completion\","
			+ "\"created\":1700000000,\"model\":\"gpt-4\",\"choices\":[{\"index\":0,"
			+ "\"message\":{\"role\":\"assistant\",\"content\":\"Hello\"},\"finish_reason\":\"stop\"}],"
			+ "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":1,\"total_tokens\":6}}";

	@Test
	void emptyApiKeyDoesNotSendAuthorizationHeader() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(CHAT_COMPLETION_RESPONSE));
			server.start();

			OpenAIClient client = OpenAiSetup.setupSyncClient(server.url("/v1").toString(), "", null, null, null, null,
					false, false, "gpt-4", Duration.ofSeconds(10), 0, null, null);

			assertThat(client).isNotNull();

			// Trigger an actual HTTP request so we can inspect received headers.
			client.chat()
				.completions()
				.create(ChatCompletionCreateParams.builder()
					.model(com.openai.models.ChatModel.GPT_4)
					.addUserMessage("Hi")
					.build());

			RecordedRequest request = server.takeRequest();
			assertThat(request.getHeader("Authorization")).as("Authorization header must be absent in no-auth mode")
				.isNull();
		}
	}

	@Test
	void noopApiKeyDoesNotSendAuthorizationHeader() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(CHAT_COMPLETION_RESPONSE));
			server.start();

			// NoopApiKey is the 1.x migration path: users who previously relied on
			// NoopApiKey can now pass it via the ApiKey-typed builder overload.
			OpenAiChatOptions options = OpenAiChatOptions.builder()
				.baseUrl(server.url("/v1").toString())
				.apiKey(new NoopApiKey())
				.model("gpt-4")
				.build();

			assertThat(options.getApiKey()).as("NoopApiKey.getValue() should return empty string").isEmpty();

			OpenAIClient client = OpenAiSetup.setupSyncClient(options.getBaseUrl(), options.getApiKey(), null, null,
					null, null, false, false, "gpt-4", Duration.ofSeconds(10), 0, null, null);

			client.chat()
				.completions()
				.create(ChatCompletionCreateParams.builder()
					.model(com.openai.models.ChatModel.GPT_4)
					.addUserMessage("Hi")
					.build());

			RecordedRequest request = server.takeRequest();
			assertThat(request.getHeader("Authorization"))
				.as("Authorization header must be absent when NoopApiKey is used")
				.isNull();
		}
	}

}
