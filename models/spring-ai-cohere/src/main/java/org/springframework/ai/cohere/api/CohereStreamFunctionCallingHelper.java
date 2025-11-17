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

package org.springframework.ai.cohere.api;

import org.springframework.ai.cohere.api.CohereApi.ChatCompletionChunk;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.Role;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Ricken Bazolo
 */
public class CohereStreamFunctionCallingHelper {

	/**
	 * Merge the previous and current ChatCompletionChunk into a single one.
	 * @param previous the previous ChatCompletionChunk
	 * @param current the current ChatCompletionChunk
	 * @return the merged ChatCompletionChunk
	 */
	public ChatCompletionChunk merge(ChatCompletionChunk previous, ChatCompletionChunk current) {

		if (previous == null) {
			return current;
		}

		if (current == null) {
			return previous;
		}

		var previousDelta = previous.delta();
		var currentDelta = current.delta();

		ChatCompletionMessage previousMessage = previousDelta != null ? previousDelta.message() : null;
		ChatCompletionMessage currentMessage = currentDelta != null ? currentDelta.message() : null;

		Role role = previousMessage != null && previousMessage.role() != null
				? previousMessage.role()
				: (currentMessage != null ? currentMessage.role() : null);

		String previousText = previousMessage != null ? extractTextFromRawContent(previousMessage.rawContent()) : "";

		String currentText = currentMessage != null ? extractTextFromRawContent(currentMessage.rawContent()) : "";

		String mergedText = previousText + currentText;

		String toolPlan = previousMessage != null && previousMessage.toolPlan() != null
				? previousMessage.toolPlan()
				: (currentMessage != null ? currentMessage.toolPlan() : null);

		List<ToolCall> toolCalls = previousMessage != null && previousMessage.toolCalls() != null && !previousMessage.toolCalls().isEmpty()
				? previousMessage.toolCalls()
				: (currentMessage != null ? currentMessage.toolCalls() : null);

		List<ChatCompletionMessage.ChatCompletionCitation> citations = previousMessage != null && previousMessage.citations() != null && !previousMessage.citations().isEmpty()
				? previousMessage.citations()
				: (currentMessage != null ? currentMessage.citations() : null);

		ChatCompletionMessage mergedMessage = new ChatCompletionMessage(
				mergedText,
				role,
				toolPlan,
				toolCalls,
				citations
		);

		var finishReason = (currentDelta != null && currentDelta.finishReason() != null)
				? currentDelta.finishReason()
				: (previousDelta != null ? previousDelta.finishReason() : null);

		var usage = (currentDelta != null && currentDelta.usage() != null)
				? currentDelta.usage()
				: (previousDelta != null ? previousDelta.usage() : null);

		var mergedDelta = new ChatCompletionChunk.ChunkDelta(
				mergedMessage,
				finishReason,
				usage
		);

		String id = current.id() != null ? current.id() : previous.id();
		String type = current.type() != null ? current.type() : previous.type();
		Integer index = current.index() != null ? current.index() : previous.index();

		return new ChatCompletionChunk(id, type, index, mergedDelta);
	}

	private String extractTextFromRawContent(Object rawContent) {
		if (rawContent == null) {
			return "";
		}
		if (rawContent instanceof Map<?, ?> map) {
			Object text = map.get("text");
			if (text != null) return text.toString();
		}
		if (rawContent instanceof List<?> list) {
			StringBuilder sb = new StringBuilder();
			for (Object item : list) {
				if (item instanceof Map<?, ?> m) {
					Object text = m.get("text");
					if (text != null) sb.append(text);
				} else if (item instanceof String s) {
					sb.append(s);
				}
			}
			return sb.toString();
		}
		if (rawContent instanceof String s) return s;
		return rawContent.toString();
	}


	private ChatCompletionMessage merge(ChatCompletionMessage previous, ChatCompletionMessage current) {
		String content = (current.content() != null ? current.content()
				: (previous.content() != null) ? previous.content() : "");
		Role role = (current.role() != null ? current.role() : previous.role());
		role = (role != null ? role : Role.ASSISTANT);
		//String name = (current.name() != null ? current.name() : previous.name());

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
		return new ChatCompletionMessage(content, role, toolCalls);
	}

	private ToolCall merge(ToolCall previous, ToolCall current) {
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

		return false;
	}

	/**
	 * @param chatCompletion the ChatCompletionChunk to check
	 * @return true if the ChatCompletionChunk is a streaming tool function call and it is
	 * the last one.
	 */
	public boolean isStreamingToolFunctionCallFinish(ChatCompletionChunk chatCompletion) {

		return false;
	}

}
// ---
