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

package org.springframework.ai.deepseek.api;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionFinishReason;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.Role;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage.ToolCall;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Helper class to support Streaming function calling. It can merge the streamed
 * ChatCompletionChunk in case of function calling message.
 *
 * @author Geng Rong
 * @author Sun Yuhan
 */
public class DeepSeekStreamFunctionCallingHelper {

	public ChatCompletionChunk merge(@Nullable ChatCompletionChunk previous, ChatCompletionChunk current) {

		if (previous == null) {
			return current;
		}

		String id = (current.id() != null ? current.id() : previous.id());
		Long created = (current.created() != null ? current.created() : previous.created());
		String model = (current.model() != null ? current.model() : previous.model());
		String serviceTier = (current.serviceTier() != null ? current.serviceTier() : previous.serviceTier());
		String systemFingerprint = (current.systemFingerprint() != null ? current.systemFingerprint()
				: previous.systemFingerprint());
		String object = (current.object() != null ? current.object() : previous.object());
		DeepSeekApi.Usage usage = (current.usage() != null ? current.usage() : previous.usage());

		ChunkChoice previousChoice0 = (CollectionUtils.isEmpty(previous.choices()) ? null : previous.choices().get(0));
		ChunkChoice currentChoice0 = (CollectionUtils.isEmpty(current.choices()) ? null : current.choices().get(0));

		ChunkChoice choice = currentChoice0 != null ? merge(previousChoice0, currentChoice0) : null;
		List<ChunkChoice> chunkChoices = choice == null ? List.of() : List.of(choice);
		return new ChatCompletionChunk(id, chunkChoices, created, model, serviceTier, systemFingerprint, object, usage);
	}

	private ChunkChoice merge(@Nullable ChunkChoice previous, ChunkChoice current) {
		if (previous == null) {
			return current;
		}

		ChatCompletionFinishReason finishReason = (current.finishReason() != null ? current.finishReason()
				: previous.finishReason());
		Integer index = current.index();

		ChatCompletionMessage message = merge(previous.delta(), current.delta());

		DeepSeekApi.LogProbs logprobs = (current.logprobs() != null ? current.logprobs() : previous.logprobs());
		return new ChunkChoice(finishReason, index, message, logprobs);
	}

	private ChatCompletionMessage merge(@Nullable ChatCompletionMessage previous, ChatCompletionMessage current) {
		String content = (previous != null && previous.content() != null)
				? previous.content() + (current.content() != null ? current.content() : "") : current.content();
		String reasoningContent = (previous != null && previous.reasoningContent() != null)
				? previous.reasoningContent() + (current.reasoningContent() != null ? current.reasoningContent() : "")
				: current.reasoningContent();
		Role role = current.role();
		String name = (current.name() != null ? current.name() : (previous != null ? previous.name() : null));
		String toolCallId = (current.toolCallId() != null ? current.toolCallId()
				: (previous != null ? previous.toolCallId() : null));
		Boolean prefix = (current.prefix() != null ? current.prefix() : (previous != null ? previous.prefix() : null));

		List<ToolCall> toolCalls = new ArrayList<>();
		ToolCall lastPreviousTooCall = null;
		if (previous != null && !CollectionUtils.isEmpty(previous.toolCalls())) {
			lastPreviousTooCall = previous.toolCalls().get(previous.toolCalls().size() - 1);
			if (previous.toolCalls().size() > 1) {
				toolCalls.addAll(previous.toolCalls().subList(0, previous.toolCalls().size() - 1));
			}
		}
		if (!CollectionUtils.isEmpty(current.toolCalls())) {
			if (current.toolCalls().size() > 1) {
				throw new IllegalStateException("Currently only one tool call is supported per message!");
			}
			var currentToolCall = current.toolCalls().iterator().next();
			if (StringUtils.hasText(currentToolCall.id())) {
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
		return new ChatCompletionMessage(content, role, name, toolCallId, toolCalls, prefix, reasoningContent);
	}

	private ToolCall merge(@Nullable ToolCall previous, ToolCall current) {
		if (previous == null) {
			return current;
		}
		String id = (StringUtils.hasText(current.id()) ? current.id() : previous.id());
		String type = (current.type() != null ? current.type() : previous.type());
		ChatCompletionFunction function = merge(previous.function(), current.function());
		return new ToolCall(id, type, function);
	}

	private ChatCompletionFunction merge(@Nullable ChatCompletionFunction previous, ChatCompletionFunction current) {
		if (previous == null) {
			return current;
		}
		String name = (StringUtils.hasText(current.name()) ? current.name() : previous.name());
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
	public boolean isStreamingToolFunctionCall(@Nullable ChatCompletionChunk chatCompletion) {

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
	public boolean isStreamingToolFunctionCallFinish(@Nullable ChatCompletionChunk chatCompletion) {

		if (chatCompletion == null || CollectionUtils.isEmpty(chatCompletion.choices())) {
			return false;
		}

		var choice = chatCompletion.choices().get(0);
		if (choice == null || choice.delta() == null) {
			return false;
		}
		return choice.finishReason() == ChatCompletionFinishReason.TOOL_CALLS;
	}

	/**
	 * Expand a tool-call SSE window into per-chunk delta frames followed by a single
	 * authoritative merged frame.
	 *
	 * <p>
	 * For each input chunk that carries a tool-call delta, this emits a frame that
	 * preserves the chunk's own {@code arguments} fragment (i.e. only the new bytes the
	 * model produced in that SSE event) but stamps the cumulative tool-call identity (id,
	 * type, function name) carried over from earlier chunks. Empty SSE marker chunks
	 * (e.g. the trailing {@code delta: {}, finish_reason: tool_calls} frame that closes
	 * the window) are dropped — the merged frame at the end carries the
	 * {@code finish_reason}. Pre-merge frames keep {@code finish_reason} unset so
	 * downstream can distinguish them from the merged complete frame.
	 *
	 * <p>
	 * The final element of the returned list is the fully merged chunk: full id, name,
	 * concatenated arguments, and {@code finish_reason = TOOL_CALLS}.
	 * @param windowChunks chunks belonging to the same tool-call window, in arrival order
	 * @return delta frames + one terminal merged frame; empty when input is empty
	 */
	public List<ChatCompletionChunk> expandToolCallWindow(List<ChatCompletionChunk> windowChunks) {
		if (CollectionUtils.isEmpty(windowChunks)) {
			return List.of();
		}
		if (windowChunks.size() == 1) {
			return List.of(windowChunks.get(0));
		}

		List<ChatCompletionChunk> out = new ArrayList<>(windowChunks.size() + 1);
		ChatCompletionChunk cumulative = null;
		for (ChatCompletionChunk chunk : windowChunks) {
			cumulative = (cumulative == null) ? chunk : merge(cumulative, chunk);
			if (carriesToolCallDelta(chunk)) {
				out.add(stampToolCallIdentity(chunk, cumulative));
			}
		}
		// The merged chunk carries the full id/name, the concatenated arguments, and
		// the trailing finish_reason — downstream uses it as the authoritative frame
		// to drive tool execution.
		if (cumulative != null) {
			out.add(cumulative);
		}
		return out;
	}

	private static boolean carriesToolCallDelta(ChatCompletionChunk chunk) {
		if (CollectionUtils.isEmpty(chunk.choices())) {
			return false;
		}
		ChunkChoice choice = chunk.choices().get(0);
		return choice != null && choice.delta() != null && !CollectionUtils.isEmpty(choice.delta().toolCalls());
	}

	/**
	 * Build a chunk that preserves {@code source}'s own {@code arguments} fragment but
	 * adopts the {@code identitySource}'s id/type/name and clears any finish_reason.
	 */
	private static ChatCompletionChunk stampToolCallIdentity(ChatCompletionChunk source,
			ChatCompletionChunk identitySource) {
		if (CollectionUtils.isEmpty(source.choices()) || CollectionUtils.isEmpty(identitySource.choices())) {
			return source;
		}
		ChunkChoice srcChoice = source.choices().get(0);
		ChunkChoice idChoice = identitySource.choices().get(0);
		if (srcChoice == null || srcChoice.delta() == null || idChoice == null || idChoice.delta() == null) {
			return source;
		}

		List<ToolCall> srcToolCalls = srcChoice.delta().toolCalls();
		List<ToolCall> idToolCalls = idChoice.delta().toolCalls();
		if (CollectionUtils.isEmpty(srcToolCalls) || CollectionUtils.isEmpty(idToolCalls)) {
			return source;
		}

		List<ToolCall> stamped = new ArrayList<>(srcToolCalls.size());
		for (int i = 0; i < srcToolCalls.size(); i++) {
			ToolCall src = srcToolCalls.get(i);
			ToolCall identity = i < idToolCalls.size() ? idToolCalls.get(i) : src;
			ChatCompletionFunction srcFn = src.function();
			ChatCompletionFunction idFn = identity.function();
			String stampedName = (srcFn != null && StringUtils.hasText(srcFn.name())) ? srcFn.name()
					: (idFn != null && idFn.name() != null) ? idFn.name() : "";
			String args = (srcFn != null && srcFn.arguments() != null) ? srcFn.arguments() : "";
			ChatCompletionFunction fn = new ChatCompletionFunction(stampedName, args);
			String stampedId = StringUtils.hasText(src.id()) ? src.id() : identity.id();
			String stampedType = (src.type() != null) ? src.type() : identity.type();
			stamped.add(new ToolCall(src.index(), stampedId, stampedType, fn));
		}

		ChatCompletionMessage delta = srcChoice.delta();
		ChatCompletionMessage stampedDelta = new ChatCompletionMessage(delta.content(), delta.role(), delta.name(),
				delta.toolCallId(), stamped, delta.prefix(), delta.reasoningContent());
		// finish_reason cleared so downstream can distinguish partial deltas from the
		// terminal merged frame, which carries finish_reason = TOOL_CALLS.
		ChunkChoice stampedChoice = new ChunkChoice(null, srcChoice.index(), stampedDelta, srcChoice.logprobs());
		return new ChatCompletionChunk(source.id(), List.of(stampedChoice), source.created(), source.model(),
				source.serviceTier(), source.systemFingerprint(), source.object(), source.usage());
	}

}
