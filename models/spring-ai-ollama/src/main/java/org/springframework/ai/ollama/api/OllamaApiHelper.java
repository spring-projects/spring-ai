/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.ollama.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.ollama.api.OllamaApi.ChatResponse;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 * @author Sun Yuhan
 * @since 1.0.0
 */
public final class OllamaApiHelper {

	private OllamaApiHelper() {
		throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
	}

	/**
	 * @param ollamaChatResponse the Ollama chat response chunk to check
	 * @return true if the chunk is a streaming tool call.
	 */
	public static boolean isStreamingToolCall(OllamaApi.ChatResponse ollamaChatResponse) {

		if (ollamaChatResponse == null || ollamaChatResponse.message() == null
				|| ollamaChatResponse.message().toolCalls() == null) {
			return false;
		}

		return !CollectionUtils.isEmpty(ollamaChatResponse.message().toolCalls());
	}

	/**
	 * @param ollamaChatResponse the Ollama chat response chunk to check
	 * @return true if the chunk is final
	 */
	public static boolean isStreamingDone(OllamaApi.ChatResponse ollamaChatResponse) {

		if (ollamaChatResponse == null) {
			return false;
		}

		return ollamaChatResponse.done() && ollamaChatResponse.doneReason().equals("stop");
	}

	public static ChatResponse merge(ChatResponse previous, ChatResponse current) {

		String model = (current.model() != null ? current.model() : previous.model());
		Instant createdAt = merge(previous.createdAt(), current.createdAt());
		OllamaApi.Message message = merge(previous.message(), current.message());
		String doneReason = (current.doneReason() != null ? current.doneReason() : previous.doneReason());
		Boolean done = (current.done() != null ? current.done() : previous.done());
		Long totalDuration = merge(previous.totalDuration(), current.totalDuration());
		Long loadDuration = merge(previous.loadDuration(), current.loadDuration());
		Integer promptEvalCount = merge(previous.promptEvalCount(), current.promptEvalCount());
		Long promptEvalDuration = merge(previous.promptEvalDuration(), current.promptEvalDuration());
		Integer evalCount = merge(previous.evalCount(), current.evalCount());
		Long evalDuration = merge(previous.evalDuration(), current.evalDuration());

		return new ChatResponse(model, createdAt, message, doneReason, done, totalDuration, loadDuration,
				promptEvalCount, promptEvalDuration, evalCount, evalDuration);
	}

	private static OllamaApi.Message merge(OllamaApi.Message previous, OllamaApi.Message current) {

		String content = mergeContent(previous, current);
		String thinking = mergeThinking(previous, current);
		OllamaApi.Message.Role role = (current.role() != null ? current.role() : previous.role());
		role = (role != null ? role : OllamaApi.Message.Role.ASSISTANT);
		List<String> images = mergeImages(previous, current);
		List<OllamaApi.Message.ToolCall> toolCalls = mergeToolCall(previous, current);
		String toolName = mergeToolName(previous, current);

		return OllamaApi.Message.builder(role)
			.content(content)
			.thinking(thinking)
			.images(images)
			.toolCalls(toolCalls)
			.toolName(toolName)
			.build();
	}

	private static Instant merge(Instant previous, Instant current) {
		return (current != null ? current : previous);
	}

	private static Integer merge(Integer previous, Integer current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}
		return previous + current;
	}

	private static Long merge(Long previous, Long current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}
		return previous + current;
	}

	private static String mergeContent(OllamaApi.Message previous, OllamaApi.Message current) {
		if (previous == null || previous.content() == null) {
			return (current != null ? current.content() : null);
		}
		if (current == null || current.content() == null) {
			return (previous != null ? previous.content() : null);
		}

		return previous.content() + current.content();
	}

	private static List<OllamaApi.Message.ToolCall> mergeToolCall(OllamaApi.Message previous,
			OllamaApi.Message current) {
		if (previous == null) {
			return (current != null ? current.toolCalls() : null);
		}
		if (current == null) {
			return previous.toolCalls();
		}
		return merge(previous.toolCalls(), current.toolCalls());
	}

	private static String mergeThinking(OllamaApi.Message previous, OllamaApi.Message current) {
		if (previous == null || previous.thinking() == null) {
			return (current != null ? current.thinking() : null);
		}
		if (current == null || current.thinking() == null) {
			return (previous.thinking());
		}

		return previous.thinking() + current.thinking();
	}

	private static String mergeToolName(OllamaApi.Message previous, OllamaApi.Message current) {
		if (previous == null || previous.toolName() == null) {
			return (current != null ? current.toolName() : null);
		}
		if (current == null || current.toolName() == null) {
			return (previous.toolName());
		}

		return previous.toolName() + current.toolName();
	}

	private static List<String> mergeImages(OllamaApi.Message previous, OllamaApi.Message current) {
		if (previous == null) {
			return (current != null ? current.images() : null);
		}
		if (current == null) {
			return previous.images();
		}
		return merge(previous.images(), current.images());
	}

	private static <T> List<T> merge(List<T> previous, List<T> current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}
		List<T> merged = new ArrayList<>(previous);
		merged.addAll(current);
		return merged;
	}

}
