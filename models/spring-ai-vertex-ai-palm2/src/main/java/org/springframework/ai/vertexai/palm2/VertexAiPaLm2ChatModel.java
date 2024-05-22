/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vertexai.palm2;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.vertexai.palm2.api.VertexAiPaLm2Api;
import org.springframework.ai.vertexai.palm2.api.VertexAiPaLm2Api.GenerateMessageRequest;
import org.springframework.ai.vertexai.palm2.api.VertexAiPaLm2Api.GenerateMessageResponse;
import org.springframework.ai.vertexai.palm2.api.VertexAiPaLm2Api.MessagePrompt;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
public class VertexAiPaLm2ChatModel implements ChatModel {

	private final VertexAiPaLm2Api vertexAiApi;

	private final VertexAiPaLm2ChatOptions defaultOptions;

	public VertexAiPaLm2ChatModel(VertexAiPaLm2Api vertexAiApi) {
		this(vertexAiApi,
				VertexAiPaLm2ChatOptions.builder().withTemperature(0.7f).withCandidateCount(1).withTopK(20).build());
	}

	public VertexAiPaLm2ChatModel(VertexAiPaLm2Api vertexAiApi, VertexAiPaLm2ChatOptions defaultOptions) {
		Assert.notNull(defaultOptions, "Default options must not be null!");
		Assert.notNull(vertexAiApi, "VertexAiPaLm2Api must not be null!");

		this.vertexAiApi = vertexAiApi;
		this.defaultOptions = defaultOptions;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		GenerateMessageRequest request = createRequest(prompt);

		GenerateMessageResponse response = this.vertexAiApi.generateMessage(request);

		List<Generation> generations = response.candidates()
			.stream()
			.map(vmsg -> new Generation(vmsg.content()))
			.toList();

		return new ChatResponse(generations);
	}

	/**
	 * Accessible for testing.
	 */
	GenerateMessageRequest createRequest(Prompt prompt) {

		String vertexContext = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.map(m -> m.getContent())
			.collect(Collectors.joining(System.lineSeparator()));

		List<VertexAiPaLm2Api.Message> vertexMessages = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(m -> new VertexAiPaLm2Api.Message(m.getMessageType().getValue(), m.getContent()))
			.toList();

		Assert.isTrue(!CollectionUtils.isEmpty(vertexMessages), "No user or assistant messages found in the prompt!");

		var vertexPrompt = new MessagePrompt(vertexContext, vertexMessages);

		GenerateMessageRequest request = new GenerateMessageRequest(vertexPrompt);

		if (this.defaultOptions != null) {
			request = ModelOptionsUtils.merge(request, this.defaultOptions, GenerateMessageRequest.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				VertexAiPaLm2ChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions,
						ChatOptions.class, VertexAiPaLm2ChatOptions.class);
				request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, GenerateMessageRequest.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		return request;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return VertexAiPaLm2ChatOptions.fromOptions(this.defaultOptions);
	}

}
