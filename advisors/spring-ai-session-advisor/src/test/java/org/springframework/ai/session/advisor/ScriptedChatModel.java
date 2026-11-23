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

package org.springframework.ai.session.advisor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/**
 * A model for {@code ChatClient} tests: answers each prompt with the next scripted reply
 * and keeps the prompts it got.
 *
 * @author Christian Tzolov
 */
class ScriptedChatModel implements ChatModel {

	private final Deque<Function<Prompt, ChatResponse>> replies = new ArrayDeque<>();

	private final List<Prompt> prompts = new ArrayList<>();

	ScriptedChatModel then(Function<Prompt, ChatResponse> reply) {
		this.replies.add(reply);
		return this;
	}

	/** A reply that asks for the {@code get_weather} tool for the city. */
	ScriptedChatModel thenCall(String id, String city) {
		return then(prompt -> new ChatResponse(List.of(new Generation(toolCall(id, city)))));
	}

	ScriptedChatModel thenAnswer(String text) {
		return then(prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
	}

	static AssistantMessage toolCall(String id, String city) {
		return AssistantMessage.builder()
			.content("")
			.toolCalls(List
				.of(new AssistantMessage.ToolCall(id, "function", "get_weather", "{\"city\":\"" + city + "\"}")))
			.build();
	}

	@Override
	public synchronized ChatResponse call(Prompt prompt) {
		this.prompts.add(prompt);
		Function<Prompt, ChatResponse> reply = this.replies.poll();
		if (reply == null) {
			throw new IllegalStateException("No scripted reply left for prompt " + this.prompts.size());
		}
		return reply.apply(prompt);
	}

	/** Streams the next reply as one chunk. */
	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return Flux.defer(() -> Flux.just(call(prompt)));
	}

	List<Prompt> prompts() {
		return this.prompts;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return ToolCallingChatOptions.builder().build();
	}

	@Override
	public ChatOptions getOptions() {
		return ToolCallingChatOptions.builder().build();
	}

	/** The text, "call:id" for an assistant tool call, "result:id" for a tool result. */
	static List<String> describe(Prompt prompt) {
		return prompt.getInstructions().stream().map(m -> {
			if (m instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
				return "call:" + assistant.getToolCalls().get(0).id();
			}
			if (m instanceof ToolResponseMessage responses) {
				return "result:" + responses.getResponses().get(0).id();
			}
			return m.getText();
		}).toList();
	}

}
