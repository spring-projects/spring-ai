/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.openai.client;

import java.util.List;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.prompt.Prompt;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;

public class OpenAiStreamClient implements AiStreamClient {

	private Double temperature = 0.7;

	private String model = "gpt-3.5-turbo";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final WebClient webClient;

	private final ObjectMapper objectMapper;

	private final ParameterizedTypeReference<ServerSentEvent<String>> sseType;

	public OpenAiStreamClient(String openAiApiToken) {
		this("https://api.openai.com/", openAiApiToken);
	}

	public OpenAiStreamClient(String openAiEndpoint, String openAiApiToken) {
		this.webClient = WebClient.builder()
			.baseUrl(openAiEndpoint)
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiToken)
			.build();
		this.objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
		this.sseType = new ParameterizedTypeReference<>() {
		};
	}

	private WebClient.ResponseSpec createChatCompletion(ChatCompletionsRequest chatCompletionsRequest) {
		return this.webClient.post()
			.uri("/v1/chat/completions")
			.bodyValue(objectMapper.convertValue(chatCompletionsRequest, JsonNode.class))
			.retrieve();
	}

	@Override
	public Flux<OpenAiSseResponse> generateStream(Prompt prompt) {

		List<OpenAiChatMessage> openAiChatMessages = prompt.getMessages()
			.stream()
			.map(message -> new OpenAiChatMessage.Builder().role(message.getMessageTypeValue())
				.content(message.getContent())
				.build())
			.toList();

		ChatCompletionsRequest chatCompletionsRequest = new ChatCompletionsRequest.Builder().stream(true)
			.model(this.model)
			.temperature(this.temperature)
			.messages(openAiChatMessages)
			.build();

		logger.trace("ChatMessages: {}", chatCompletionsRequest.getMessages());

		return createChatCompletion(chatCompletionsRequest).bodyToFlux(sseType)
			.map(ServerSentEvent::data)
			.filter(Predicate.not("[DONE]"::equals))
			.handle((data, sink) -> {
				try {
					sink.next(objectMapper.readValue(data, OpenAiSseResponse.class));
				}
				catch (JsonProcessingException e) {
					sink.error(new RuntimeException(e));
				}
			});
	}

}
