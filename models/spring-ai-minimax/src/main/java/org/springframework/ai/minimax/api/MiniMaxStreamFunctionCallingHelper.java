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

package org.springframework.ai.minimax.api;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionChunk;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionFinishReason;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.Role;
import org.springframework.ai.minimax.api.MiniMaxApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.minimax.api.MiniMaxApi.LogProbs;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Helper class to support Streaming function calling. It can merge the streamed
 * ChatCompletionChunk in case of function calling message.
 *
 * @author Geng Rong
 * @since 1.0.0 M1
 */
public class MiniMaxStreamFunctionCallingHelper {

	public ChatCompletionChunk merge(@Nullable ChatCompletionChunk previous, ChatCompletionChunk current) {

		if (previous == null) {
			return current;
		}

		String id = (current.id() != null ? current.id() : previous.id());
		Long created = (current.created() != null ? current.created() : previous.created());
		String model = (current.model() != null ? current.model() : previous.model());
		String systemFingerprint = (current.systemFingerprint() != null ? current.systemFingerprint()
				: previous.systemFingerprint());
		String object = (current.object() != null ? current.object() : previous.object());

		ChunkChoice previousChoice0 = (CollectionUtils.isEmpty(previous.choices()) ? null : previous.choices().get(0));
		ChunkChoice currentChoice0 = (CollectionUtils.isEmpty(current.choices()) ? null : current.choices().get(0));

		ChunkChoice choice = merge(previousChoice0, currentChoice0);
		List<ChunkChoice> chunkChoices = choice == null ? List.of() : List.of(choice);
		return new ChatCompletionChunk(id, chunkChoices, created, model, systemFingerprint, object);
	}

	private @Nullable ChunkChoice merge(@Nullable ChunkChoice previous, @Nullable ChunkChoice current) {
		if (previous == null) {
			return current;
		}
		if (current == null) {
			return previous;
		}

		ChatCompletionFinishReason finishReason = (current.finishReason() != null ? current.finishReason()
				: previous.finishReason());
		Integer index = (current.index() != null ? current.index() : previous.index());
		LogProbs logprobs = (current.logprobs() != null ? current.logprobs() : previous.logprobs());

		ChatCompletionMessage previousDelta = previous.delta();
		ChatCompletionMessage currentDelta = current.delta();
		ChatCompletionMessage message;
		if (previousDelta == null) {
			message = currentDelta;
		}
		else if (currentDelta == null) {
			message = previousDelta;
		}
		else {
			message = merge(previousDelta, currentDelta);
		}
		return new ChunkChoice(finishReason, index, message, logprobs);
	}

	private ChatCompletionMessage merge(ChatCompletionMessage previous, ChatCompletionMessage current) {
		String content = (current.content() != null ? current.content()
				: "" + ((previous.content() != null) ? previous.content() : ""));
		Role role = (current.role() != null ? current.role() : previous.role());
		role = (role != null ? role : Role.ASSISTANT); // default to ASSISTANT (if null
		String name = (current.name() != null ? current.name() : previous.name());
		String toolCallId = (current.toolCallId() != null ? current.toolCallId() : previous.toolCallId());

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
			if (currentToolCall.id() == null
					|| (lastPreviousTooCall != null && currentToolCall.id().equals(lastPreviousTooCall.id()))) {
				toolCalls.add(merge(lastPreviousTooCall, currentToolCall));
			}
			else {
				if (lastPreviousTooCall != null) {
					toolCalls.add(lastPreviousTooCall);
				}
				toolCalls.add(currentToolCall);
			}
		}
		else {
			if (lastPreviousTooCall != null) {
				toolCalls.add(lastPreviousTooCall);
			}
		}
		return new ChatCompletionMessage(content, role, name, toolCallId, toolCalls);
	}

	private ToolCall merge(@Nullable ToolCall previous, ToolCall current) {
		if (previous == null) {
			return current;
		}
		String id = (current.id() != null ? current.id() : previous.id());
		String type = (current.type() != null ? current.type() : previous.type());
		ChatCompletionFunction function = merge(previous.function(), current.function());
		return new ToolCall(id, type, function);
	}

	private ChatCompletionFunction merge(@Nullable ChatCompletionFunction previous, ChatCompletionFunction current) {
		if (previous == null) {
			return current;
		}
		String name = (StringUtils.hasLength(current.name()) ? current.name() : previous.name());
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
		return choice.finishReason() == MiniMaxApi.ChatCompletionFinishReason.TOOL_CALLS;
	}

	/**
	 * Expand a tool-call SSE window into per-chunk delta frames followed by a single
	 * authoritative merged frame. Mirrors the same contract used by DeepSeek and Mistral
	 * helpers so {@code MessageAggregator} can stitch partials by id.
	 *
	 * <p>
	 * Pre-merge frames preserve their own argument fragments and adopt the cumulative
	 * tool-call identity (id, type, function name) stamped from earlier chunks;
	 * finish_reason stays unset. Empty-delta marker chunks are dropped. The final element
	 * is the fully merged chunk used by tool execution.
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
			String stampedId = (StringUtils.hasText(src.id())) ? src.id() : identity.id();
			String stampedType = (src.type() != null) ? src.type() : identity.type();
			stamped.add(new ToolCall(stampedId, stampedType, fn));
		}

		ChatCompletionMessage delta = srcChoice.delta();
		ChatCompletionMessage stampedDelta = new ChatCompletionMessage(delta.rawContent(), delta.role(), delta.name(),
				delta.toolCallId(), stamped);
		// Pre-merge frames carry no finish_reason from the source SSE; preserve that.
		// The terminal merged frame keeps its own finish_reason because
		// expandToolCallWindow appends `cumulative` directly without stamping.
		ChunkChoice stampedChoice = new ChunkChoice(srcChoice.finishReason(), srcChoice.index(), stampedDelta,
				srcChoice.logprobs());
		return new ChatCompletionChunk(source.id(), List.of(stampedChoice), source.created(), source.model(),
				source.systemFingerprint(), source.object());
	}

	/**
	 * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	public MiniMaxApi.ChatCompletion chunkToChatCompletion(MiniMaxApi.ChatCompletionChunk chunk) {
		List<ChunkChoice> chunkChoices = chunk.choices();
		List<MiniMaxApi.ChatCompletion.Choice> choices = chunkChoices == null ? List.of()
				: chunkChoices.stream()
					.map(chunkChoice -> new MiniMaxApi.ChatCompletion.Choice(chunkChoice.finishReason(),
							chunkChoice.index(), chunkChoice.delta(), null, chunkChoice.logprobs()))
					.toList();

		return new MiniMaxApi.ChatCompletion(chunk.id(), choices, chunk.created(), chunk.model(),
				chunk.systemFingerprint(), "chat.completion", null, null);
	}

}
