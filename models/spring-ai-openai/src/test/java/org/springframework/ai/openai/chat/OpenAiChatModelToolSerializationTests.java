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

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatModelToolSerializationTests {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final String CHAT_COMPLETION_RESPONSE = """
			{"id":"chatcmpl-test","object":"chat.completion","created":1700000000,"model":"gpt-4","choices":[{"index":0,"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":1,"total_tokens":6}}
			""";

	@Test
	void strictToolCallingFlagIsSerializedOnFunctionDefinition() throws Exception {
		try (MockWebServer server = new MockWebServer()) {
			server.enqueue(new MockResponse().setResponseCode(200)
				.setHeader("Content-Type", "application/json")
				.setBody(CHAT_COMPLETION_RESPONSE));
			server.start();

			ToolCallback weatherTool = FunctionToolCallback.builder("getCurrentWeather", request -> "sunny")
				.description("Get the weather in a city")
				.inputType(WeatherRequest.class)
				.build();

			OpenAiChatOptions options = OpenAiChatOptions.builder()
				.baseUrl(server.url("/v1").toString())
				.apiKey(new NoopApiKey())
				.model("gpt-4")
				.toolCallbacks(List.of(weatherTool))
				.build();

			OpenAiChatModel chatModel = OpenAiChatModel.builder().options(options).build();

			chatModel.call(new Prompt("What's the weather in Seoul?"));

			RecordedRequest request = server.takeRequest();
			var body = OBJECT_MAPPER.readTree(request.getBody().readUtf8());

			assertThat(body.at("/tools/0/function/strict").isBoolean()).isTrue();
			assertThat(body.at("/tools/0/function/strict").booleanValue()).isTrue();
			assertThat(body.at("/tools/0/function/parameters/strict").isMissingNode()).isTrue();
		}
	}

	record WeatherRequest(String location) {
	}

}
