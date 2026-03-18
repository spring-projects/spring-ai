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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.cohere.api.CohereApi.ChatCompletionChunk;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.Role;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.cohere.api.CohereApi.EventType;
import org.springframework.util.ObjectUtils;

/**
 * Helper class for handling streaming function calling in Cohere API.
 *
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

		Role role = previousMessage != null && previousMessage.role() != null ? previousMessage.role()
				: (currentMessage != null ? currentMessage.role() : null);

		String previousText = previousMessage != null ? extractTextFromRawContent(previousMessage.rawContent()) : "";

		String currentText = currentMessage != null ? extractTextFromRawContent(currentMessage.rawContent()) : "";

		String currentType = current.type();
		String mergedText;
		if (EventType.CONTENT_START.getValue().equals(currentType)) {
			mergedText = currentText;
		}
		else if (EventType.CONTENT_END.getValue().equals(currentType)) {
			mergedText = previousText;
		}
		else {
			mergedText = previousText + currentText;
		}

		String previousPlan = previousMessage != null ? previousMessage.toolPlan() : null;
		String currentPlan = currentMessage != null ? currentMessage.toolPlan() : null;

		String mergedToolPlan = previousPlan;

		if (EventType.TOOL_PLAN_DELTA.getValue().equals(current.type())) {
			mergedToolPlan = mergeToolPlan(previousPlan, currentPlan);
		}

		List<ToolCall> mergedToolCalls = mergeToolCalls(previous, current);

		List<ChatCompletionMessage.ChatCompletionCitation> citations = mergeCitations(previous, current);

		ChatCompletionMessage mergedMessage = new ChatCompletionMessage(mergedText, role, mergedToolPlan,
				mergedToolCalls, citations, null);

		var finishReason = (currentDelta != null && currentDelta.finishReason() != null) ? currentDelta.finishReason()
				: (previousDelta != null ? previousDelta.finishReason() : null);

		var usage = (currentDelta != null && currentDelta.usage() != null) ? currentDelta.usage()
				: (previousDelta != null ? previousDelta.usage() : null);

		var mergedDelta = new ChatCompletionChunk.ChunkDelta(mergedMessage, finishReason, usage);

		String id = current.id() != null ? current.id() : previous.id();
		String type = current.type() != null ? current.type() : previous.type();
		Integer index = current.index() != null ? current.index() : previous.index();

		return new ChatCompletionChunk(id, type, index, mergedDelta);
	}

	public ChatCompletionChunk sanitizeToolCalls(ChatCompletionChunk chunk) {
		if (chunk == null || chunk.delta() == null || chunk.delta().message() == null) {
			return chunk;
		}

		ChatCompletionMessage msg = chunk.delta().message();
		List<ToolCall> toolCalls = msg.toolCalls();

		if (toolCalls == null || toolCalls.isEmpty()) {
			return chunk;
		}

		List<ToolCall> cleaned = toolCalls.stream().filter(this::isValidToolCall).toList();

		ChatCompletionChunk.ChunkDelta oldDelta = chunk.delta();

		ChatCompletionMessage cleanedMsg = new ChatCompletionMessage(msg.rawContent(), msg.role(), msg.toolPlan(),
				cleaned.isEmpty() ? null : cleaned, msg.citations(), null);

		ChatCompletionChunk.ChunkDelta newDelta = new ChatCompletionChunk.ChunkDelta(cleanedMsg,
				oldDelta.finishReason(), oldDelta.usage());

		return new ChatCompletionChunk(chunk.id(), chunk.type(), chunk.index(), newDelta);
	}

	public boolean hasValidToolCallsOnly(ChatCompletionChunk c) {
		if (c == null || c.delta() == null || c.delta().message() == null) {
			return false;
		}

		ChatCompletionMessage message = c.delta().message();
		List<ToolCall> calls = message.toolCalls();

		boolean hasValidToolCalls = calls != null && calls.stream().anyMatch(this::isValidToolCall);

		boolean hasTextContent = message.rawContent() != null
				&& !extractTextFromRawContent(message.rawContent()).isEmpty();

		boolean hasCitations = message.citations() != null && !message.citations().isEmpty();

		return hasValidToolCalls || hasTextContent || hasCitations;
	}

	private boolean isValidToolCall(ToolCall toolCall) {
		if (toolCall == null || toolCall.function() == null) {
			return false;
		}
		ChatCompletionFunction chatCompletionFunction = toolCall.function();
		String functionName = chatCompletionFunction.name();
		String functionArguments = chatCompletionFunction.arguments();
		return !ObjectUtils.isEmpty(functionName) && !ObjectUtils.isEmpty(functionArguments);
	}

	private String extractTextFromRawContent(Object rawContent) {
		if (rawContent == null) {
			return "";
		}
		if (rawContent instanceof Map<?, ?> map) {
			Object text = map.get("text");
			if (text != null) {
				return text.toString();
			}
		}
		if (rawContent instanceof List<?> list) {
			StringBuilder sb = new StringBuilder();
			for (Object item : list) {
				if (item instanceof Map<?, ?> m) {
					Object text = m.get("text");
					if (text != null) {
						sb.append(text);
					}
				}
				else if (item instanceof String s) {
					sb.append(s);
				}
			}
			return sb.toString();
		}
		if (rawContent instanceof String s) {
			return s;
		}
		return rawContent.toString();
	}

	private List<ToolCall> mergeToolCalls(ChatCompletionChunk previous, ChatCompletionChunk current) {
		ChatCompletionMessage previousMessage = previous != null && previous.delta() != null
				? previous.delta().message() : null;
		ChatCompletionMessage currentMessage = current.delta() != null ? current.delta().message() : null;

		List<ToolCall> merged = ensureToolCallList(previousMessage != null ? previousMessage.toolCalls() : null);

		String type = current.type();
		Integer index = current.index();

		if (index == null) {
			return merged;
		}

		ToolCall existing = ensureToolCallAtIndex(merged, index);
		ChatCompletionFunction existingFunction = existing.function() != null ? existing.function()
				: new ChatCompletionFunction(null, "");

		String id = existing.id();
		String callType = existing.type();
		String functionName = existingFunction.name();
		String args = existingFunction.arguments() != null ? existingFunction.arguments() : "";

		if (EventType.TOOL_CALL_START.getValue().equals(type) && currentMessage != null
				&& currentMessage.toolCalls() != null && !currentMessage.toolCalls().isEmpty()) {

			ToolCall start = currentMessage.toolCalls().get(0);
			ChatCompletionFunction startFunction = start.function() != null ? start.function()
					: new ChatCompletionFunction(null, "");

			id = start.id() != null ? start.id() : id;
			callType = start.type() != null ? start.type() : callType;
			functionName = startFunction.name() != null ? startFunction.name() : functionName;

		}

		if (EventType.TOOL_CALL_DELTA.getValue().equals(type) && currentMessage != null
				&& currentMessage.toolCalls() != null && !currentMessage.toolCalls().isEmpty()) {

			ToolCall deltaCall = currentMessage.toolCalls().get(0);
			ChatCompletionFunction deltaFunction = deltaCall.function();
			if (deltaFunction != null && deltaFunction.arguments() != null) {
				args = (args == null ? "" : args) + deltaFunction.arguments();
			}
		}

		// tool-call-end
		ChatCompletionFunction mergedFn = new ChatCompletionFunction(functionName, args);
		ToolCall mergedCall = new ToolCall(id, callType, mergedFn, index);
		merged.set(index, mergedCall);

		return merged;
	}

	private String mergeToolPlan(final String previous, final String currentFragment) {
		if (currentFragment == null || currentFragment.isEmpty()) {
			return previous;
		}
		if (previous == null) {
			return currentFragment;
		}
		return previous + currentFragment;
	}

	private List<ChatCompletionMessage.ChatCompletionCitation> mergeCitations(final ChatCompletionChunk previous,
			final ChatCompletionChunk current) {

		ChatCompletionMessage previousMessage = previous != null && previous.delta() != null
				? previous.delta().message() : null;
		ChatCompletionMessage currentMessage = current != null && current.delta() != null ? current.delta().message()
				: null;

		List<ChatCompletionMessage.ChatCompletionCitation> merged = new ArrayList<>();

		if (previousMessage != null && previousMessage.citations() != null) {
			merged.addAll(previousMessage.citations());
		}

		if (current != null && EventType.CITATION_START.getValue().equals(current.type()) && currentMessage != null
				&& currentMessage.citations() != null) {
			merged.addAll(currentMessage.citations());
		}

		return merged.isEmpty() ? null : merged;
	}

	private List<ToolCall> ensureToolCallList(final List<ToolCall> toolCalls) {
		return (toolCalls != null) ? new ArrayList<>(toolCalls) : new ArrayList<>();
	}

	private ToolCall ensureToolCallAtIndex(final List<ToolCall> toolCalls, final int index) {
		while (toolCalls.size() <= index) {
			toolCalls.add(new ToolCall(null, null, new ChatCompletionFunction("", ""), index));
		}
		ToolCall call = toolCalls.get(index);
		if (call == null) {
			call = new ToolCall(null, null, new ChatCompletionFunction("", ""), index);
			toolCalls.set(index, call);
		}
		return call;
	}

}
