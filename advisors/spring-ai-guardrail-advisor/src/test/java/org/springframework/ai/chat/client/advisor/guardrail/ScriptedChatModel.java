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

package org.springframework.ai.chat.client.advisor.guardrail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A model for {@code ChatClient} tests: answers each prompt with the next scripted reply
 * and keeps the prompts it got. A reply is a list of responses: one for a call, one per
 * chunk for a stream.
 *
 * @author Christian Tzolov
 */
final class ScriptedChatModel implements ChatModel {

	private final Deque<List<ChatResponse>> replies = new ArrayDeque<>();

	private final List<Prompt> prompts = new ArrayList<>();

	/** A reply of one text. */
	ScriptedChatModel thenAnswer(String text) {
		return thenGenerations(new Generation(new AssistantMessage(text)));
	}

	/** A reply that holds several generations, as some providers return. */
	ScriptedChatModel thenGenerations(Generation... generations) {
		this.replies.add(List.of(new ChatResponse(List.of(generations))));
		return this;
	}

	/** A reply given as its responses: one for a call, one per chunk for a stream. */
	ScriptedChatModel thenResponses(ChatResponse... responses) {
		this.replies.add(List.of(responses));
		return this;
	}

	/** A reply streamed in several chunks. */
	ScriptedChatModel thenChunks(String... chunks) {
		this.replies.add(Stream.of(chunks)
			.map(chunk -> new ChatResponse(List.of(new Generation(new AssistantMessage(chunk)))))
			.toList());
		return this;
	}

	@Override
	public synchronized ChatResponse call(Prompt prompt) {
		return next(prompt).get(0);
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		return Flux.defer(() -> Flux.fromIterable(next(prompt)));
	}

	private synchronized List<ChatResponse> next(Prompt prompt) {
		this.prompts.add(prompt);
		List<ChatResponse> reply = this.replies.poll();
		if (reply == null) {
			throw new IllegalStateException("No scripted reply left for prompt " + this.prompts.size());
		}
		return reply;
	}

	List<Prompt> prompts() {
		return this.prompts;
	}

	/** The texts of the messages of a prompt, in order. */
	static List<String> describe(Prompt prompt) {
		return prompt.getInstructions().stream().map(Message::getText).toList();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return ChatOptions.builder().build();
	}

	@Override
	public ChatOptions getOptions() {
		return ChatOptions.builder().build();
	}

}
