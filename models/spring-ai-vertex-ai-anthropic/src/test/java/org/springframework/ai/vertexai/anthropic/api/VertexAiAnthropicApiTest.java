/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.vertexai.anthropic.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.auth.Credentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link VertexAiAnthropicApi}.
 */
@ExtendWith(MockitoExtension.class)
public class VertexAiAnthropicApiTest {

	@Mock
	Credentials credentials;

	MockRestServiceServer server;

	RestClient.Builder restClientBuilder;

	Map<String, List<String>> requestMetadata;

	@BeforeEach
	void beforeEach() throws Exception {
		this.restClientBuilder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(this.restClientBuilder).build();
		this.requestMetadata = new HashMap<>();
		this.requestMetadata.put("Authorization", List.of("Bearer DUMMY"));
		doReturn(this.requestMetadata).when(this.credentials).getRequestMetadata();
	}

	/**
	 * Tests chat completion with minimal options.
	 */
	@Test
	void testChatCompletionWithMinimalOptions() {
		var api = VertexAiAnthropicApi.builderForVertexAi()
			.credentials(this.credentials)
			.restClientBuilder(this.restClientBuilder)
			.projectId("dummy_pj")
			.build();
		var request = AnthropicApi.ChatCompletionRequest.builder().build();
		this.server.expect(requestTo(
				"https://aiplatform.googleapis.com/v1/projects/dummy_pj/locations/global/publishers/anthropic/models/claude-sonnet-4-5@20250929:streamRawPredict"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer DUMMY"))
			.andExpect(jsonPath("anthropic_version").value("vertex-2023-10-16"))
			.andExpect(jsonPath("model").doesNotExist())
			.andExpect(jsonPath("messages").doesNotExist())
			.andRespond(withSuccess());
		api.chatCompletionEntity(request);
		this.server.verify();
	}

	/**
	 * Tests various Vertex AI endpoint configurations with different locations and
	 * models.
	 */
	@ParameterizedTest
	@CsvSource({
	// @formatter:off
			"        ,                           , https://aiplatform.googleapis.com/v1/projects/dummy_pj/locations/global/publishers/anthropic/models/claude-sonnet-4-5@20250929:streamRawPredict",
			"global  , claude-sonnet-4-5@20250929, https://aiplatform.googleapis.com/v1/projects/dummy_pj/locations/global/publishers/anthropic/models/claude-sonnet-4-5@20250929:streamRawPredict",
			"us-east5,                           , https://us-east5-aiplatform.googleapis.com/v1/projects/dummy_pj/locations/us-east5/publishers/anthropic/models/claude-sonnet-4-5@20250929:streamRawPredict",
			"        , claude-haiku-4-5@20251001 , https://aiplatform.googleapis.com/v1/projects/dummy_pj/locations/global/publishers/anthropic/models/claude-haiku-4-5@20251001:streamRawPredict"
			// @formatter:on
	})
	void testVertexEndpoint(String location, String model, String expected) {
		var api = VertexAiAnthropicApi.builderForVertexAi()
			.credentials(this.credentials)
			.restClientBuilder(this.restClientBuilder)
			.projectId("dummy_pj");
		if (location != null) {
			api.location(location);
		}
		if (model != null) {
			api.model(model);
		}
		var request = AnthropicApi.ChatCompletionRequest.builder();
		this.server.expect(requestTo(expected)).andRespond(withSuccess());
		api.build().chatCompletionEntity(request.build());
		this.server.verify();
	}

	/**
	 * Tests that request body properties are correctly mapped to JSON paths.
	 */
	@ParameterizedTest
	@CsvSource({
	// @formatter:off
			"role                , assistant, messages[0].role",
			"content             , hi!!     , messages[0].content[0].text",
			"maxTokens           , 2048     , max_tokens",
			"thinkingBudgetTokens, 4096     , thinking.budget_tokens"
			// @formatter:on
	})
	void testChatCompletion(String property, String value, String jsonPath) {
		var api = VertexAiAnthropicApi.builderForVertexAi()
			.credentials(this.credentials)
			.restClientBuilder(this.restClientBuilder)
			.projectId("dummy_pj");
		var request = AnthropicApi.ChatCompletionRequest.builder();

		var role = property.equals("role") ? value : "user";
		var content = property.equals("content") ? value : "";
		var messageContent = List.of(new AnthropicApi.ContentBlock(content));
		var messageRole = AnthropicApi.Role.valueOf(role.toUpperCase());
		request.messages(List.of(new AnthropicApi.AnthropicMessage(messageContent, messageRole)));

		var maxTokens = property.equals("maxTokens") ? Integer.valueOf(value) : null;
		if (maxTokens != null) {
			request.maxTokens(maxTokens);
		}
		var thinkingBudgetTokens = property.equals("thinkingBudgetTokens") ? Integer.valueOf(value) : null;
		if (thinkingBudgetTokens != null) {
			request.thinking(AnthropicApi.ThinkingType.ENABLED, thinkingBudgetTokens);
		}
		this.server.expect(requestTo(
				"https://aiplatform.googleapis.com/v1/projects/dummy_pj/locations/global/publishers/anthropic/models/claude-sonnet-4-5@20250929:streamRawPredict"))
			.andExpect(jsonPath(jsonPath).value(value))
			.andRespond(withSuccess());
		api.build().chatCompletionEntity(request.build());
		this.server.verify();
	}

	/**
	 * Tests chat completion request with tool calling.
	 */
	@Test
	void testChatCompletionWithToolCalling() {
		var api = VertexAiAnthropicApi.builderForVertexAi()
			.credentials(this.credentials)
			.restClientBuilder(this.restClientBuilder)
			.projectId("dummy_pj")
			.build();
		var tool = new AnthropicApi.Tool("get_weather", "Get current weather",
				Map.of("type", "object", "properties", Map.of("location", Map.of("type", "string"))));
		var request = AnthropicApi.ChatCompletionRequest.builder()
			.messages(List.of(new AnthropicApi.AnthropicMessage(
					List.of(new AnthropicApi.ContentBlock("What's the weather?")), AnthropicApi.Role.USER)))
			.tools(List.of(tool))
			.build();
		this.server.expect(requestTo(
				"https://aiplatform.googleapis.com/v1/projects/dummy_pj/locations/global/publishers/anthropic/models/claude-sonnet-4-5@20250929:streamRawPredict"))
			.andExpect(jsonPath("tools[0].name").value("get_weather"))
			.andExpect(jsonPath("tools[0].description").value("Get current weather"))
			.andExpect(jsonPath("messages[0].content[0].text").value("What's the weather?"))
			.andRespond(withSuccess());
		api.chatCompletionEntity(request);
		this.server.verify();
	}

}
