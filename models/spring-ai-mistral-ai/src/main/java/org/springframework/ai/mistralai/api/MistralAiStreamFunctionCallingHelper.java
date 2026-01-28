/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.mistralai.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionFinishReason;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.mistralai.api.MistralAiApi.LogProbs;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Helper class to support Streaming function calling.
 *
 * It can merge the streamed ChatCompletionChunk in case of function calling message.
 *
 * @author Christian Tzolov
 * @since 0.8.1
 */
public class MistralAiStreamFunctionCallingHelper {

	/**
	 * Merge the previous and current ChatCompletionChunk into a single one.
	 * @param previous the previous ChatCompletionChunk
	 * @param current the current ChatCompletionChunk
	 * @return the merged ChatCompletionChunk
	 */
	public ChatCompletionChunk merge(@Nullable ChatCompletionChunk previous, ChatCompletionChunk current) {

		if (previous == null) {
			return current;
		}

		String id = (current.id() != null ? current.id() : previous.id());
		Long created = (current.created() != null ? current.created() : previous.created());
		String model = (current.model() != null ? current.model() : previous.model());
		String object = (current.object() != null ? current.object() : previous.object());

		ChunkChoice previousChoice0 = (CollectionUtils.isEmpty(previous.choices()) ? null : previous.choices().get(0));
		ChunkChoice currentChoice0 = (CollectionUtils.isEmpty(current.choices()) ? null : current.choices().get(0));

		Assert.state(currentChoice0 != null, "Current choices must not be null or empty");
		ChunkChoice choice = merge(previousChoice0, currentChoice0);

		MistralAiApi.Usage usage = (current.usage() != null ? current.usage() : previous.usage());

		return new ChatCompletionChunk(id, object, created, model, List.of(choice), usage);
	}

	private ChunkChoice merge(@Nullable ChunkChoice previous, ChunkChoice current) {
		if (previous == null) {
			if (current.delta() != null && current.delta().toolCalls() != null) {
				Optional<String> id = current.delta()
					.toolCalls()
					.stream()
					.map(ToolCall::id)
					.filter(Objects::nonNull)
					.findFirst();
				if (id.isEmpty()) {
					var newId = UUID.randomUUID().toString();

					var toolCallsWithID = current.delta()
						.toolCalls()
						.stream()
						.map(toolCall -> new ToolCall(newId, "function", toolCall.function(), toolCall.index()))
						.toList();

					var role = current.delta().role() != null ? current.delta().role() : Role.ASSISTANT;
					current = new ChunkChoice(
							current.index(), new ChatCompletionMessage(current.delta().content(), role,
									current.delta().name(), toolCallsWithID),
							current.finishReason(), current.logprobs());
				}
			}
			return current;
		}

		ChatCompletionFinishReason finishReason = (current.finishReason() != null ? current.finishReason()
				: previous.finishReason());
		Integer index = (current.index() != null ? current.index() : previous.index());

		ChatCompletionMessage message = merge(previous.delta(), current.delta());
		LogProbs logprobs = (current.logprobs() != null ? current.logprobs() : previous.logprobs());

		return new ChunkChoice(index, message, finishReason, logprobs);
	}

	private ChatCompletionMessage merge(ChatCompletionMessage previous, ChatCompletionMessage current) {
		String content = (current.content() != null ? current.content()
				: (previous.content() != null) ? previous.content() : "");
		Role role = (current.role() != null ? current.role() : previous.role());
		role = (role != null ? role : Role.ASSISTANT); // default to ASSISTANT (if null
		String name = (current.name() != null ? current.name() : previous.name());

		List<ToolCall> toolCalls = new ArrayList<>();
		ToolCall lastPreviousTooCall = null;
		if (previous.toolCalls() != null) {
			lastPreviousTooCall = previous.toolCalls().get(previous.toolCalls().size() - 1);
			if (previous.toolCalls().size() > 1) {
				toolCalls.addAll(previous.toolCalls().subList(0, previous.toolCalls().size() - 1));
			}
		}
		if (current.toolCalls() != null) {
			if (current.toolCalls().size() > 1) {
				throw new IllegalStateException("Currently only one tool call is supported per message!");
			}
			var currentToolCall = current.toolCalls().iterator().next();
			if (currentToolCall.id() != null) {
				if (lastPreviousTooCall != null) {
					toolCalls.add(lastPreviousTooCall);
				}
				toolCalls.add(currentToolCall);
			}
			else {
				toolCalls.add(merge(lastPreviousTooCall, currentToolCall));
			}
		}
		else {
			if (lastPreviousTooCall != null) {
				toolCalls.add(lastPreviousTooCall);
			}
		}
		return new ChatCompletionMessage(content, role, name, toolCalls);
	}

	private ToolCall merge(@Nullable ToolCall previous, ToolCall current) {
		if (previous == null) {
			return current;
		}
		String id = (current.id() != null ? current.id() : previous.id());
		String type = (current.type() != null ? current.type() : previous.type());
		ChatCompletionFunction function = merge(previous.function(), current.function());
		Integer index = (current.index() != null ? current.index() : previous.index());
		return new ToolCall(id, type, function, index);
	}

	private ChatCompletionFunction merge(ChatCompletionFunction previous, ChatCompletionFunction current) {
		if (previous == null) {
			return current;
		}
		String name = (current.name() != null ? current.name() : previous.name());
		StringBuilder arguments = new StringBuilder();
		if (previous.arguments() != null) {
			arguments.append(previous.arguments());
		}
		if (current.arguments() != null) {
			arguments.append(current.arguments());
		}
		return new ChatCompletionFunction(name, arguments.toString());
	}

	/**
	 * @param chatCompletion the ChatCompletionChunk to check
	 * @return true if the ChatCompletionChunk is a streaming tool function call.
	 */
	public boolean isStreamingToolFunctionCall(ChatCompletionChunk chatCompletion) {

		var choices = chatCompletion.choices();
		if (CollectionUtils.isEmpty(choices)) {
			return false;
		}

		var choice = choices.get(0);
		return !CollectionUtils.isEmpty(choice.delta().toolCalls());
	}

	/**
	 * @param chatCompletion the ChatCompletionChunk to check
	 * @return true if the ChatCompletionChunk is a streaming tool function call and it is
	 * the last one.
	 */
	public boolean isStreamingToolFunctionCallFinish(ChatCompletionChunk chatCompletion) {

		var choices = chatCompletion.choices();
		if (CollectionUtils.isEmpty(choices)) {
			return false;
		}

		var choice = choices.get(0);
		return choice.finishReason() == ChatCompletionFinishReason.TOOL_CALLS;
	}

}
// ---
