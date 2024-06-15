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
package org.springframework.ai.minimax.api;

import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to support Streaming function calling. It can merge the streamed
 * ChatCompletionChunk in case of function calling message.
 *
 * @author Geng Rong
 * @since 1.0.0 M1
 */
public class MiniMaxStreamFunctionCallingHelper {

	/**
	 * Merge the previous and current ChatCompletionChunk into a single one.
	 * @param previous the previous ChatCompletionChunk
	 * @param current the current ChatCompletionChunk
	 * @return the merged ChatCompletionChunk
	 */
	public MiniMaxApi.ChatCompletionChunk merge(MiniMaxApi.ChatCompletionChunk previous,
			MiniMaxApi.ChatCompletionChunk current) {

		if (previous == null) {
			return current;
		}

		String id = (current.id() != null ? current.id() : previous.id());
		Long created = (current.created() != null ? current.created() : previous.created());
		String model = (current.model() != null ? current.model() : previous.model());
		String systemFingerprint = (current.systemFingerprint() != null ? current.systemFingerprint()
				: previous.systemFingerprint());
		String object = (current.object() != null ? current.object() : previous.object());

		MiniMaxApi.ChatCompletionChunk.ChunkChoice previousChoice0 = (CollectionUtils.isEmpty(previous.choices()) ? null
				: previous.choices().get(0));
		MiniMaxApi.ChatCompletionChunk.ChunkChoice currentChoice0 = (CollectionUtils.isEmpty(current.choices()) ? null
				: current.choices().get(0));

		MiniMaxApi.ChatCompletionChunk.ChunkChoice choice = merge(previousChoice0, currentChoice0);
		List<MiniMaxApi.ChatCompletionChunk.ChunkChoice> chunkChoices = choice == null ? List.of() : List.of(choice);
		return new MiniMaxApi.ChatCompletionChunk(id, chunkChoices, created, model, systemFingerprint, object);
	}

	private MiniMaxApi.ChatCompletionChunk.ChunkChoice merge(MiniMaxApi.ChatCompletionChunk.ChunkChoice previous,
			MiniMaxApi.ChatCompletionChunk.ChunkChoice current) {
		if (previous == null) {
			return current;
		}

		MiniMaxApi.ChatCompletionFinishReason finishReason = (current.finishReason() != null ? current.finishReason()
				: previous.finishReason());
		Integer index = (current.index() != null ? current.index() : previous.index());

		MiniMaxApi.ChatCompletionMessage message = merge(previous.delta(), current.delta());

		MiniMaxApi.LogProbs logprobs = (current.logprobs() != null ? current.logprobs() : previous.logprobs());
		return new MiniMaxApi.ChatCompletionChunk.ChunkChoice(finishReason, index, message, logprobs);
	}

	private MiniMaxApi.ChatCompletionMessage merge(MiniMaxApi.ChatCompletionMessage previous,
			MiniMaxApi.ChatCompletionMessage current) {
		String content = (current.content() != null ? current.content()
				: (previous.content() != null) ? previous.content() : "");
		MiniMaxApi.ChatCompletionMessage.Role role = (current.role() != null ? current.role() : previous.role());
		role = (role != null ? role : MiniMaxApi.ChatCompletionMessage.Role.ASSISTANT); // default
																						// to
																						// ASSISTANT
																						// (if
																						// null
		String name = (current.name() != null ? current.name() : previous.name());
		String toolCallId = (current.toolCallId() != null ? current.toolCallId() : previous.toolCallId());

		List<MiniMaxApi.ChatCompletionMessage.ToolCall> toolCalls = new ArrayList<>();
		MiniMaxApi.ChatCompletionMessage.ToolCall lastPreviousTooCall = null;
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
		return new MiniMaxApi.ChatCompletionMessage(content, role, name, toolCallId, toolCalls);
	}

	private MiniMaxApi.ChatCompletionMessage.ToolCall merge(MiniMaxApi.ChatCompletionMessage.ToolCall previous,
			MiniMaxApi.ChatCompletionMessage.ToolCall current) {
		if (previous == null) {
			return current;
		}
		String id = (current.id() != null ? current.id() : previous.id());
		String type = (current.type() != null ? current.type() : previous.type());
		MiniMaxApi.ChatCompletionMessage.ChatCompletionFunction function = merge(previous.function(),
				current.function());
		return new MiniMaxApi.ChatCompletionMessage.ToolCall(id, type, function);
	}

	private MiniMaxApi.ChatCompletionMessage.ChatCompletionFunction merge(
			MiniMaxApi.ChatCompletionMessage.ChatCompletionFunction previous,
			MiniMaxApi.ChatCompletionMessage.ChatCompletionFunction current) {
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
		return new MiniMaxApi.ChatCompletionMessage.ChatCompletionFunction(name, arguments.toString());
	}

	/**
	 * @param chatCompletion the ChatCompletionChunk to check
	 * @return true if the ChatCompletionChunk is a streaming tool function call.
	 */
	public boolean isStreamingToolFunctionCall(MiniMaxApi.ChatCompletionChunk chatCompletion) {

		if (chatCompletion == null || CollectionUtils.isEmpty(chatCompletion.choices())) {
			return false;
		}

		var choice = chatCompletion.choices().get(0);
		if (choice == null || choice.delta() == null) {
			return false;
		}
		return !CollectionUtils.isEmpty(choice.delta().toolCalls());
	}

	/**
	 * @param chatCompletion the ChatCompletionChunk to check
	 * @return true if the ChatCompletionChunk is a streaming tool function call and it is
	 * the last one.
	 */
	public boolean isStreamingToolFunctionCallFinish(MiniMaxApi.ChatCompletionChunk chatCompletion) {

		if (chatCompletion == null || CollectionUtils.isEmpty(chatCompletion.choices())) {
			return false;
		}

		var choice = chatCompletion.choices().get(0);
		if (choice == null || choice.delta() == null) {
			return false;
		}
		return choice.finishReason() == MiniMaxApi.ChatCompletionFinishReason.TOOL_CALLS;
	}

	/**
	 * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	public MiniMaxApi.ChatCompletion chunkToChatCompletion(MiniMaxApi.ChatCompletionChunk chunk) {
		List<MiniMaxApi.ChatCompletion.Choice> choices = chunk.choices()
			.stream()
			.map(chunkChoice -> new MiniMaxApi.ChatCompletion.Choice(chunkChoice.finishReason(), chunkChoice.index(),
					chunkChoice.delta(), chunkChoice.logprobs()))
			.toList();

		return new MiniMaxApi.ChatCompletion(chunk.id(), choices, chunk.created(), chunk.model(),
				chunk.systemFingerprint(), "chat.completion", null, null);
	}

}
