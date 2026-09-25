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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.teknek.deliverance.client.spring.api.ChatApi;
import io.teknek.deliverance.client.spring.api.ModelsApi;
import io.teknek.deliverance.client.spring.core.ApiClient;
import io.teknek.deliverance.client.spring.model.ChatCompletionMessageToolCall;
import io.teknek.deliverance.client.spring.model.ChatCompletionMessageToolCallFunction;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionRequest;
import io.teknek.deliverance.client.spring.model.CreateChatCompletionResponse;
import io.teknek.deliverance.client.spring.model.ListModelsResponse;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Low-level Deliverance chat completion API client.
 *
 * @author Edward Capriolo
 * @since 2.0.1
 */
public interface DeliveranceApi {

	String PROVIDER_NAME = "deliverance";

	CreateChatCompletionResponse createChatCompletion(CreateChatCompletionRequest request);

	Flux<ChatResponse> streamChatCompletion(CreateChatCompletionRequest request);

	ListModelsResponse listModels();

	static Builder builder() {
		return new Builder();
	}

	static DeliveranceApi create(String baseUrl, @Nullable String username, @Nullable String password) {
		return builder().baseUrl(baseUrl).username(username).password(password).build();
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
		return deliveranceToolCalls.stream().map(toolCall -> {
			ChatCompletionMessageToolCallFunction function = toolCall.getFunction();
			if (function == null) {
				return null;
			}
			return new AssistantMessage.ToolCall(nullSafe(toolCall.getId()), nullSafe(toolCall.getType()),
					nullSafe(function.getName()), nullSafe(function.getArguments()));
		}).filter(Objects::nonNull).toList();
	}

	static ChatResponseMetadata metadata(CreateChatCompletionResponse response) {
		return ChatResponseMetadata.builder()
			.id(nullSafe(response.getId()))
			.model(nullSafe(response.getModel()))
			.keyValue("created", response.getCreated())
			.build();
	}

	private static String nullSafe(@Nullable String value) {
		return value != null ? value : "";
	}

	final class Builder {

		private String baseUrl = "http://localhost:8080";

		private @Nullable String username;

		private @Nullable String password;

		private WebClient.Builder webClientBuilder = WebClient.builder();

		private Builder() {
		}

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder username(@Nullable String username) {
			this.username = username;
			return this;
		}

		public Builder password(@Nullable String password) {
			this.password = password;
			return this;
		}

		public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
			Assert.notNull(webClientBuilder, "webClientBuilder cannot be null");
			this.webClientBuilder = webClientBuilder;
			return this;
		}

		public DeliveranceApi build() {
			String normalizedBaseUrl = this.baseUrl.endsWith("/") ? this.baseUrl.substring(0, this.baseUrl.length() - 1)
					: this.baseUrl;
			JsonMapper jsonMapper = jsonMapper();
			String authorization = basicAuthorization(this.username, this.password);
			WebClient.Builder builder = this.webClientBuilder.clone()
				.baseUrl(normalizedBaseUrl)
				.exchangeStrategies(exchangeStrategies(jsonMapper));
			if (StringUtils.hasText(authorization)) {
				builder.defaultHeader(HttpHeaders.AUTHORIZATION, authorization);
			}
			ApiClient apiClient = new ApiClient(builder.build(), jsonMapper, ApiClient.createDefaultDateFormat())
				.setBasePath(normalizedBaseUrl);
			if (StringUtils.hasText(authorization)) {
				apiClient.addDefaultHeader(HttpHeaders.AUTHORIZATION, authorization);
			}
			ChatApi chatApi = new ChatApi(apiClient);
			ModelsApi modelsApi = new ModelsApi(apiClient);
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
					return Flux.defer(() -> {
						StreamingToolCallAccumulator toolCallAccumulator = new StreamingToolCallAccumulator();
						return apiClient.getWebClient()
							.post()
							.uri("/chat/completions")
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
									JsonNode choice = chunk.path("choices").path(0);
									JsonNode delta = choice.path("delta");
									String content = delta.path("content").asText("");
									List<AssistantMessage.ToolCall> toolCalls = streamToolCalls(
											delta.path("tool_calls"), toolCallAccumulator);
									if (StringUtils.hasText(content) || !toolCalls.isEmpty()) {
										AssistantMessage message = AssistantMessage.builder()
											.content(content)
											.toolCalls(toolCalls)
											.build();
										Generation generation = new Generation(message, generationMetadata(choice));
										sink.next(new ChatResponse(List.of(generation), responseMetadata(chunk)));
									}
								}
								catch (Exception ex) {
									sink.error(ex);
								}
							});
					});
				}

				@Override
				public ListModelsResponse listModels() {
					ListModelsResponse response;
					try {
						response = modelsApi.listModels().block(Duration.ofMinutes(5));
					}
					catch (WebClientResponseException ex) {
						throw new IllegalStateException("Deliverance list models failed with status "
								+ ex.getStatusCode() + ": " + ex.getResponseBodyAsString(), ex);
					}
					if (response == null) {
						throw new IllegalStateException("Deliverance list models returned no response body");
					}
					return response;
				}

				private List<AssistantMessage.ToolCall> streamToolCalls(JsonNode toolCalls,
						StreamingToolCallAccumulator accumulator) {
					if (!toolCalls.isArray()) {
						return List.of();
					}
					for (int i = 0; i < toolCalls.size(); i++) {
						JsonNode toolCall = toolCalls.get(i);
						int index = toolCall.path("index").asInt(i);
						accumulator.apply(index, toolCall);
					}
					return accumulator.toolCalls();
				}
			};
		}

		private static ExchangeStrategies exchangeStrategies(JsonMapper mapper) {
			return ExchangeStrategies.builder().codecs(configurer -> {
				configurer.defaultCodecs()
					.jacksonJsonEncoder(new JacksonJsonEncoder(mapper, MediaType.APPLICATION_JSON));
				configurer.defaultCodecs()
					.jacksonJsonDecoder(new JacksonJsonDecoder(mapper, MediaType.APPLICATION_JSON));
			}).build();
		}

		private static @Nullable String basicAuthorization(@Nullable String username, @Nullable String password) {
			if (!StringUtils.hasText(username) || password == null) {
				return null;
			}
			String token = Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
			return "Basic " + token;
		}

		private static ChatResponseMetadata responseMetadata(JsonNode chunk) {
			return ChatResponseMetadata.builder()
				.id(chunk.path("id").asText(""))
				.model(chunk.path("model").asText(""))
				.keyValue("created", chunk.path("created").isMissingNode() ? null : chunk.path("created").asLong())
				.build();
		}

		private static ChatGenerationMetadata generationMetadata(JsonNode choice) {
			return ChatGenerationMetadata.builder().finishReason(choice.path("finish_reason").asText("")).build();
		}

	}

	final class StreamingToolCallAccumulator {

		private final Map<Integer, ToolCallDelta> toolCalls = new LinkedHashMap<>();

		void apply(int index, JsonNode toolCall) {
			ToolCallDelta delta = this.toolCalls.computeIfAbsent(index, key -> new ToolCallDelta());
			if (StringUtils.hasText(toolCall.path("id").asText(""))) {
				delta.id = toolCall.path("id").asText();
			}
			if (StringUtils.hasText(toolCall.path("type").asText(""))) {
				delta.type = toolCall.path("type").asText();
			}
			JsonNode function = toolCall.path("function");
			if (function.isObject()) {
				if (StringUtils.hasText(function.path("name").asText(""))) {
					delta.name = function.path("name").asText();
				}
				if (!function.path("arguments").isMissingNode() && !function.path("arguments").isNull()) {
					delta.arguments.append(function.path("arguments").asText(""));
				}
			}
		}

		List<AssistantMessage.ToolCall> toolCalls() {
			return this.toolCalls.values()
				.stream()
				.filter(ToolCallDelta::hasFunction)
				.map(delta -> new AssistantMessage.ToolCall(nullSafe(delta.id), nullSafe(delta.type),
						nullSafe(delta.name), delta.arguments.toString()))
				.toList();
		}

	}

	final class ToolCallDelta {

		private @Nullable String id;

		private @Nullable String type = "function";

		private @Nullable String name;

		private final StringBuilder arguments = new StringBuilder();

		boolean hasFunction() {
			return StringUtils.hasText(this.name) || this.arguments.length() > 0;
		}

	}

}
