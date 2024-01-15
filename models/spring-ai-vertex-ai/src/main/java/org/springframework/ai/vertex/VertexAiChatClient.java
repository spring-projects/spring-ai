/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.vertex;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.vertex.api.VertexAiApi;
import org.springframework.ai.vertex.api.VertexAiApi.GenerateMessageRequest;
import org.springframework.ai.vertex.api.VertexAiApi.GenerateMessageResponse;
import org.springframework.ai.vertex.api.VertexAiApi.MessagePrompt;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
public class VertexAiChatClient implements ChatClient {

	private final VertexAiApi vertexAiApi;

	private Float temperature;

	private Float topP;

	private Integer topK;

	private Integer candidateCount;

	public VertexAiChatClient(VertexAiApi vertexAiApi) {
		this.vertexAiApi = vertexAiApi;
	}

	public VertexAiChatClient withTemperature(Float temperature) {
		this.temperature = temperature;
		return this;
	}

	public VertexAiChatClient withTopP(Float topP) {
		this.topP = topP;
		return this;
	}

	public VertexAiChatClient withTopK(Integer topK) {
		this.topK = topK;
		return this;
	}

	public VertexAiChatClient withCandidateCount(Integer maxTokens) {
		this.candidateCount = maxTokens;
		return this;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		String vertexContext = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.map(m -> m.getContent())
			.collect(Collectors.joining("\n"));

		List<VertexAiApi.Message> vertexMessages = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
			.map(m -> new VertexAiApi.Message(m.getMessageType().getValue(), m.getContent()))
			.toList();

		Assert.isTrue(!CollectionUtils.isEmpty(vertexMessages), "No user or assistant messages found in the prompt!");

		var vertexPrompt = new MessagePrompt(vertexContext, vertexMessages);

		GenerateMessageRequest request = new GenerateMessageRequest(vertexPrompt, this.temperature, this.candidateCount,
				this.topP, this.topK);

		GenerateMessageResponse response = this.vertexAiApi.generateMessage(request);

		List<Generation> generations = response.candidates()
			.stream()
			.map(vmsg -> new Generation(vmsg.content()))
			.toList();

		return new ChatResponse(generations);
	}

}
