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

package org.springframework.ai.deliverance.api;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.teknek.deliverance.client.spring.api.ChatApi;
import io.teknek.deliverance.client.spring.core.ApiClient;
import io.teknek.deliverance.client.spring.model.ChatCompletionMessageToolCall;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionRequest;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionResponse;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Low-level Deliverance chat completion API client.
 *
 * @author Edward Capriolo
 * @since 2.0.1
 */
public interface DeliveranceApi {

	CreateChatCompletionResponse createChatCompletion(CreateChatCompletionRequest request);

	Flux<ChatResponse> streamChatCompletion(CreateChatCompletionRequest request);

	static DeliveranceApi create(String baseUrl, @Nullable String apiKey) {
		String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		ApiClient apiClient = new ApiClient(jsonMapper(), ApiClient.createDefaultDateFormat())
			.setBasePath(normalizedBaseUrl);
		if (StringUtils.hasText(apiKey)) {
			apiClient.addDefaultHeader("Authorization", "Bearer " + apiKey);
		}
		ChatApi chatApi = new ChatApi(apiClient);
		ObjectMapper objectMapper = new ObjectMapper();
		return new DeliveranceApi() {

			@Override
			public CreateChatCompletionResponse createChatCompletion(CreateChatCompletionRequest request) {
				CreateChatCompletionResponse response;
				try {
					response = chatApi.createChatCompletion(request).block(Duration.ofMinutes(5));
				}
				catch (WebClientResponseException ex) {
					throw new IllegalStateException("Deliverance chat completion failed with status "
							+ ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
				}
				if (response == null) {
					throw new IllegalStateException("Deliverance chat completion returned no response body");
				}
				return response;
			}

			@Override
			public Flux<ChatResponse> streamChatCompletion(CreateChatCompletionRequest request) {
				return apiClient.getWebClient()
					.post()
					.uri(normalizedBaseUrl + "/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.TEXT_EVENT_STREAM)
					.bodyValue(request)
					.retrieve()
					.bodyToFlux(String.class)
					.handle((data, sink) -> {
						try {
							String event = data.startsWith("data:") ? data.substring("data:".length()).trim()
									: data.trim();
							if (!StringUtils.hasText(event) || "[DONE]".equals(event)) {
								return;
							}
							JsonNode chunk = objectMapper.readTree(event);
							JsonNode delta = chunk.path("choices").path(0).path("delta");
							String content = delta.path("content").asText("");
							List<AssistantMessage.ToolCall> toolCalls = streamToolCalls(delta.path("tool_calls"));
							if (StringUtils.hasText(content) || !toolCalls.isEmpty()) {
								AssistantMessage message = AssistantMessage.builder()
									.content(content)
									.toolCalls(toolCalls)
									.build();
								sink.next(new ChatResponse(List.of(new Generation(message))));
							}
						}
						catch (Exception ex) {
							sink.error(ex);
						}
					});
			}

			private List<AssistantMessage.ToolCall> streamToolCalls(JsonNode toolCalls) {
				if (!toolCalls.isArray()) {
					return List.of();
				}
				return java.util.stream.StreamSupport.stream(toolCalls.spliterator(), false).map(toolCall -> {
					JsonNode function = toolCall.path("function");
					return new AssistantMessage.ToolCall(toolCall.path("id").asText(""),
							toolCall.path("type").asText("function"), function.path("name").asText(""),
							function.path("arguments").asText(""));
				}).toList();
			}
		};
	}

	static JsonMapper jsonMapper() {
		return JsonMapper.builder()
			.defaultDateFormat(ApiClient.createDefaultDateFormat())
			.changeDefaultPropertyInclusion(
					value -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();
	}

	static List<AssistantMessage.ToolCall> toolCalls(
			@Nullable List<ChatCompletionMessageToolCall> deliveranceToolCalls) {
		if (deliveranceToolCalls == null || deliveranceToolCalls.isEmpty()) {
			return List.of();
		}
		return deliveranceToolCalls.stream()
			.map(toolCall -> new AssistantMessage.ToolCall(toolCall.getId(), toolCall.getType(),
					toolCall.getFunction().getName(), toolCall.getFunction().getArguments()))
			.toList();
	}

}
