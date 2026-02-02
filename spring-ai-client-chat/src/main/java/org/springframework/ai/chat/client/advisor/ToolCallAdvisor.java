/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.chat.client.advisor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * Recursive Advisor that disables the internal tool execution flow and instead implements
 * the tool calling loop as part of the advisor chain.
 * <p>
 * It uses the CallAdvisorChainUtil to implement looping advisor chain calls.
 * <p>
 * This enables intercepting the tool calling loop by the rest of the advisors next in the
 * chain.
 *
 * @author Christian Tzolov
 */
public class ToolCallAdvisor implements CallAdvisor, StreamAdvisor {

	protected final ToolCallingManager toolCallingManager;

	/**
	 * Set the order close to {@link Ordered#LOWEST_PRECEDENCE} to ensure an advisor is
	 * executed first in the chain (first for request processing, last for response
	 * processing).
	 * <p>
	 * https://docs.spring.io/spring-ai/reference/api/advisors.html#_advisor_order
	 */
	private final int advisorOrder;

	private final boolean conversationHistoryEnabled;

	private final boolean streamToolCallResponses;

	protected ToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder) {
		this(toolCallingManager, advisorOrder, true, true);
	}

	protected ToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder,
			boolean conversationHistoryEnabled) {
		this(toolCallingManager, advisorOrder, conversationHistoryEnabled, true);
	}

	protected ToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder,
			boolean conversationHistoryEnabled, boolean streamToolCallResponses) {
		Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
		Assert.isTrue(advisorOrder > BaseAdvisor.HIGHEST_PRECEDENCE && advisorOrder < BaseAdvisor.LOWEST_PRECEDENCE,
				"advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");

		this.toolCallingManager = toolCallingManager;
		this.advisorOrder = advisorOrder;
		this.conversationHistoryEnabled = conversationHistoryEnabled;
		this.streamToolCallResponses = streamToolCallResponses;
	}

	@Override
	public String getName() {
		return "Tool Calling Advisor";
	}

	@Override
	public int getOrder() {
		return this.advisorOrder;
	}

	// -------------------------------------------------------------------------
	// Call (non-streaming) implementation
	// -------------------------------------------------------------------------
	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		Assert.notNull(callAdvisorChain, "callAdvisorChain must not be null");
		Assert.notNull(chatClientRequest, "chatClientRequest must not be null");

		if (chatClientRequest.prompt().getOptions() == null
				|| !(chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions)) {
			throw new IllegalArgumentException(
					"ToolCall Advisor requires ToolCallingChatOptions to be set in the ChatClientRequest options.");
		}

		chatClientRequest = this.doInitializeLoop(chatClientRequest, callAdvisorChain);

		// Overwrite the ToolCallingChatOptions to disable internal tool execution.
		var optionsCopy = (ToolCallingChatOptions) chatClientRequest.prompt().getOptions().copy();

		// Disable internal tool execution to allow ToolCallAdvisor to handle tool calls
		optionsCopy.setInternalToolExecutionEnabled(false);

		var instructions = chatClientRequest.prompt().getInstructions();

		ChatClientResponse chatClientResponse = null;

		boolean isToolCall = false;

		do {

			// Before Call
			var processedChatClientRequest = ChatClientRequest.builder()
				.prompt(new Prompt(instructions, optionsCopy))
				.context(chatClientRequest.context())
				.build();

			// Next Call
			processedChatClientRequest = this.doBeforeCall(processedChatClientRequest, callAdvisorChain);

			chatClientResponse = callAdvisorChain.copy(this).nextCall(processedChatClientRequest);

			chatClientResponse = this.doAfterCall(chatClientResponse, callAdvisorChain);

			// After Call

			// TODO: check that this tool call detection is sufficient for all chat models
			// that support tool calls. (e.g. Anthropic and Bedrock are checking for
			// finish status as well)
			ChatResponse chatResponse = chatClientResponse.chatResponse();
			isToolCall = chatResponse != null && chatResponse.hasToolCalls();

			if (isToolCall) {
				Assert.notNull(chatResponse, "redundant check that should never fail, but here to help NullAway");
				ToolExecutionResult toolExecutionResult = this.toolCallingManager
					.executeToolCalls(processedChatClientRequest.prompt(), chatResponse);

				if (toolExecutionResult.returnDirect()) {

					// Return tool execution result directly to the application client.
					chatClientResponse = chatClientResponse.mutate()
						.chatResponse(ChatResponse.builder()
							.from(chatResponse)
							.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
							.build())
						.build();

					// Interrupt the tool calling loop and return the tool execution
					// result directly to the client application instead of returning
					// it to the LLM.
					break;
				}

				instructions = this.doGetNextInstructionsForToolCall(processedChatClientRequest, chatClientResponse,
						toolExecutionResult);
			}

		}
		while (isToolCall); // loop until no tool calls are present

		return this.doFinalizeLoop(chatClientResponse, callAdvisorChain);
	}

	protected List<Message> doGetNextInstructionsForToolCall(ChatClientRequest chatClientRequest,
			ChatClientResponse chatClientResponse, ToolExecutionResult toolExecutionResult) {

		if (!this.conversationHistoryEnabled) {
			return List.of(chatClientRequest.prompt().getSystemMessage(), toolExecutionResult.conversationHistory()
				.get(toolExecutionResult.conversationHistory().size() - 1));
		}

		return toolExecutionResult.conversationHistory();
	}

	protected ChatClientResponse doFinalizeLoop(ChatClientResponse chatClientResponse,
			CallAdvisorChain callAdvisorChain) {
		return chatClientResponse;
	}

	protected ChatClientRequest doInitializeLoop(ChatClientRequest chatClientRequest,
			CallAdvisorChain callAdvisorChain) {
		return chatClientRequest;
	}

	protected ChatClientRequest doBeforeCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		return chatClientRequest;
	}

	protected ChatClientResponse doAfterCall(ChatClientResponse chatClientResponse, CallAdvisorChain callAdvisorChain) {
		return chatClientResponse;
	}

	// -------------------------------------------------------------------------
	// Streaming implementation
	// -------------------------------------------------------------------------
	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		Assert.notNull(streamAdvisorChain, "streamAdvisorChain must not be null");
		Assert.notNull(chatClientRequest, "chatClientRequest must not be null");

		if (chatClientRequest.prompt().getOptions() == null
				|| !(chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions)) {
			throw new IllegalArgumentException(
					"ToolCall Advisor requires ToolCallingChatOptions to be set in the ChatClientRequest options.");
		}

		ChatClientRequest initializedRequest = this.doInitializeLoopStream(chatClientRequest, streamAdvisorChain);

		// Overwrite the ToolCallingChatOptions to disable internal tool execution.
		// Use the validated options from the original request to satisfy NullAway,
		// as doInitializeLoopStream should preserve the options contract.
		var optionsCopy = (ToolCallingChatOptions) chatClientRequest.prompt().getOptions().copy();
		optionsCopy.setInternalToolExecutionEnabled(false);

		return this.internalStream(streamAdvisorChain, initializedRequest, optionsCopy,
				initializedRequest.prompt().getInstructions());
	}

	private Flux<ChatClientResponse> internalStream(StreamAdvisorChain streamAdvisorChain,
			ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy, List<Message> instructions) {

		return Flux.deferContextual(contextView -> {
			// Build request with current instructions
			var processedRequest = ChatClientRequest.builder()
				.prompt(new Prompt(instructions, optionsCopy))
				.context(originalRequest.context())
				.build();

			processedRequest = this.doBeforeStream(processedRequest, streamAdvisorChain);

			// Get a copy of the chain excluding this advisor
			StreamAdvisorChain chainCopy = streamAdvisorChain.copy(this);

			final ChatClientRequest finalRequest = processedRequest;

			// Get the streaming response
			Flux<ChatClientResponse> responseFlux = chainCopy.nextStream(processedRequest);

			// Holder for aggregated response (set when aggregation completes)
			AtomicReference<ChatClientResponse> aggregatedResponseRef = new AtomicReference<>();

			if (this.streamToolCallResponses) {
				// Stream all chunks immediately (including tool call responses)
				return streamWithToolCallResponses(responseFlux, aggregatedResponseRef, finalRequest,
						streamAdvisorChain, originalRequest, optionsCopy);
			}
			else {
				// Buffer chunks and only emit for final (non-tool-call) responses
				return streamWithoutToolCallResponses(responseFlux, aggregatedResponseRef, finalRequest,
						streamAdvisorChain, originalRequest, optionsCopy);
			}
		});
	}

	/**
	 * Streams all chunks immediately including intermediate tool call responses. Uses
	 * publish() to multicast the stream for parallel streaming and aggregation.
	 */
	private Flux<ChatClientResponse> streamWithToolCallResponses(Flux<ChatClientResponse> responseFlux,
			AtomicReference<ChatClientResponse> aggregatedResponseRef, ChatClientRequest finalRequest,
			StreamAdvisorChain streamAdvisorChain, ChatClientRequest originalRequest,
			ToolCallingChatOptions optionsCopy) {

		return responseFlux.publish(shared -> {
			// Branch 1: Stream chunks immediately for real-time streaming UX
			Flux<ChatClientResponse> streamingBranch = new ChatClientMessageAggregator()
				.aggregateChatClientResponse(shared, aggregatedResponseRef::set);

			// Branch 2: After streaming completes, check for tool calls and
			// potentially recurse.
			Flux<ChatClientResponse> recursionBranch = Flux
				.defer(() -> this.handleToolCallRecursion(aggregatedResponseRef.get(), finalRequest, streamAdvisorChain,
						originalRequest, optionsCopy));

			// Emit all streaming chunks first, then append any recursive results
			return streamingBranch.concatWith(recursionBranch);
		});
	}

	/**
	 * Buffers chunks and only emits them for the final (non-tool-call) response.
	 * Intermediate tool call responses are filtered out.
	 */
	private Flux<ChatClientResponse> streamWithoutToolCallResponses(Flux<ChatClientResponse> responseFlux,
			AtomicReference<ChatClientResponse> aggregatedResponseRef, ChatClientRequest finalRequest,
			StreamAdvisorChain streamAdvisorChain, ChatClientRequest originalRequest,
			ToolCallingChatOptions optionsCopy) {

		// Holder for collected chunks
		AtomicReference<List<ChatClientResponse>> chunksRef = new AtomicReference<>(new ArrayList<>());

		// Collect all chunks and aggregate, then decide whether to recurse or emit
		return new ChatClientMessageAggregator().aggregateChatClientResponse(responseFlux, aggregatedResponseRef::set)
			.doOnNext(chunk -> chunksRef.get().add(chunk))
			.ignoreElements()
			.cast(ChatClientResponse.class)
			.concatWith(Flux.defer(() -> this.handleBufferedResponse(aggregatedResponseRef.get(), chunksRef.get(),
					finalRequest, streamAdvisorChain, originalRequest, optionsCopy)));
	}

	/**
	 * Handles the buffered response after all chunks have been collected. If tool call
	 * detected, recurses without emitting chunks. If no tool call, emits all collected
	 * chunks.
	 */
	private Flux<ChatClientResponse> handleBufferedResponse(ChatClientResponse aggregatedResponse,
			List<ChatClientResponse> chunks, ChatClientRequest finalRequest, StreamAdvisorChain streamAdvisorChain,
			ChatClientRequest originalRequest, ToolCallingChatOptions optionsCopy) {

		if (aggregatedResponse == null) {
			// No response received, return collected chunks (if any)
			return Flux.fromIterable(chunks);
		}

		aggregatedResponse = this.doAfterStream(aggregatedResponse, streamAdvisorChain);

		ChatResponse chatResponse = aggregatedResponse.chatResponse();
		boolean isToolCall = chatResponse != null && chatResponse.hasToolCalls();

		if (isToolCall) {
			Assert.notNull(chatResponse, "redundant check that should never fail, but here to help NullAway");
			final ChatClientResponse finalAggregatedResponse = aggregatedResponse;

			// Execute tool calls on bounded elastic scheduler (tool execution is
			// blocking) Don't emit intermediate chunks for tool call iterations
			Flux<ChatClientResponse> toolCallFlux = Flux.deferContextual(ctx -> {
				ToolExecutionResult toolExecutionResult;
				try {
					ToolCallReactiveContextHolder.setContext(ctx);
					toolExecutionResult = this.toolCallingManager.executeToolCalls(finalRequest.prompt(), chatResponse);
				}
				finally {
					ToolCallReactiveContextHolder.clearContext();
				}

				if (toolExecutionResult.returnDirect()) {
					// Return tool execution result directly to the application client
					return Flux.just(finalAggregatedResponse.mutate()
						.chatResponse(ChatResponse.builder()
							.from(chatResponse)
							.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
							.build())
						.build());
				}
				else {
					// Recursive call with updated conversation history
					List<Message> nextInstructions = this.doGetNextInstructionsForToolCallStream(finalRequest,
							finalAggregatedResponse, toolExecutionResult);
					return this.internalStream(streamAdvisorChain, originalRequest, optionsCopy, nextInstructions);
				}
			});
			return toolCallFlux.subscribeOn(Schedulers.boundedElastic());
		}
		else {
			// Final answer - emit all collected chunks for streaming output
			return this.doFinalizeLoopStream(Flux.fromIterable(chunks), streamAdvisorChain);
		}
	}

	/**
	 * Handles tool call detection and recursion after streaming completes. Returns empty
	 * flux if no tool call, or recursive stream if tool call detected.
	 */
	private Flux<ChatClientResponse> handleToolCallRecursion(ChatClientResponse aggregatedResponse,
			ChatClientRequest finalRequest, StreamAdvisorChain streamAdvisorChain, ChatClientRequest originalRequest,
			ToolCallingChatOptions optionsCopy) {

		if (aggregatedResponse == null) {
			return Flux.empty();
		}

		aggregatedResponse = this.doAfterStream(aggregatedResponse, streamAdvisorChain);

		ChatResponse chatResponse = aggregatedResponse.chatResponse();
		boolean isToolCall = chatResponse != null && chatResponse.hasToolCalls();

		if (!isToolCall) {
			// No tool call - streaming already happened, nothing more to emit
			return this.doFinalizeLoopStream(Flux.empty(), streamAdvisorChain);
		}

		Assert.notNull(chatResponse, "redundant check that should never fail, but here to help NullAway");
		final ChatClientResponse finalAggregatedResponse = aggregatedResponse;

		// Execute tool calls on bounded elastic scheduler (tool execution is blocking)
		Flux<ChatClientResponse> toolCallFlux = Flux.deferContextual(ctx -> {
			ToolExecutionResult toolExecutionResult;
			try {
				ToolCallReactiveContextHolder.setContext(ctx);
				toolExecutionResult = this.toolCallingManager.executeToolCalls(finalRequest.prompt(), chatResponse);
			}
			finally {
				ToolCallReactiveContextHolder.clearContext();
			}

			if (toolExecutionResult.returnDirect()) {
				// Return tool execution result directly to the application client
				return Flux.just(finalAggregatedResponse.mutate()
					.chatResponse(ChatResponse.builder()
						.from(chatResponse)
						.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
						.build())
					.build());
			}
			else {
				// Recursive call with updated conversation history
				List<Message> nextInstructions = this.doGetNextInstructionsForToolCallStream(finalRequest,
						finalAggregatedResponse, toolExecutionResult);
				return this.internalStream(streamAdvisorChain, originalRequest, optionsCopy, nextInstructions);
			}
		});
		return toolCallFlux.subscribeOn(Schedulers.boundedElastic());
	}

	/**
	 * Hook method called at the start of the streaming tool call loop. Subclasses can
	 * override to customize initialization behavior.
	 * @param chatClientRequest the initial request
	 * @param streamAdvisorChain the stream advisor chain
	 * @return the potentially modified request
	 */
	protected ChatClientRequest doInitializeLoopStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		return chatClientRequest;
	}

	/**
	 * Hook method called before each streaming call in the tool call loop. Subclasses can
	 * override to customize pre-call behavior.
	 * @param chatClientRequest the request about to be processed
	 * @param streamAdvisorChain the stream advisor chain
	 * @return the potentially modified request
	 */
	protected ChatClientRequest doBeforeStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		return chatClientRequest;
	}

	/**
	 * Hook method called after each streaming call in the tool call loop. Subclasses can
	 * override to customize post-call behavior.
	 * @param chatClientResponse the response from the call
	 * @param streamAdvisorChain the stream advisor chain
	 * @return the potentially modified response
	 */
	protected ChatClientResponse doAfterStream(ChatClientResponse chatClientResponse,
			StreamAdvisorChain streamAdvisorChain) {
		return chatClientResponse;
	}

	/**
	 * Hook method called at the end of the streaming tool call loop to finalize the
	 * response. Subclasses can override to customize finalization behavior.
	 * @param chatClientResponseFlux the flux of collected response chunks to emit
	 * @param streamAdvisorChain the stream advisor chain
	 * @return the potentially modified flux of responses
	 */
	protected Flux<ChatClientResponse> doFinalizeLoopStream(Flux<ChatClientResponse> chatClientResponseFlux,
			StreamAdvisorChain streamAdvisorChain) {
		return chatClientResponseFlux;
	}

	/**
	 * Hook method to determine the next instructions for a tool call iteration in
	 * streaming mode. Subclasses can override to customize conversation history handling.
	 * @param chatClientRequest the current request
	 * @param chatClientResponse the current response
	 * @param toolExecutionResult the result of tool execution
	 * @return the list of messages to use as instructions for the next iteration
	 */
	protected List<Message> doGetNextInstructionsForToolCallStream(ChatClientRequest chatClientRequest,
			ChatClientResponse chatClientResponse, ToolExecutionResult toolExecutionResult) {

		if (!this.conversationHistoryEnabled) {
			return List.of(chatClientRequest.prompt().getSystemMessage(), toolExecutionResult.conversationHistory()
				.get(toolExecutionResult.conversationHistory().size() - 1));
		}

		return toolExecutionResult.conversationHistory();
	}

	/**
	 * Creates a new Builder instance for constructing a ToolCallAdvisor.
	 * @return a new Builder instance
	 */
	public static Builder<?> builder() {
		return new Builder<>();
	}

	/**
	 * Builder for creating instances of ToolCallAdvisor.
	 * <p>
	 * This builder uses the self-referential generic pattern to support extensibility.
	 *
	 * @param <T> the builder type, used for self-referential generics to support method
	 * chaining in subclasses
	 */
	public static class Builder<T extends Builder<T>> {

		private ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

		private int advisorOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 300;

		private boolean conversationHistoryEnabled = true;

		private boolean streamToolCallResponses = false;

		protected Builder() {
		}

		/**
		 * Returns this builder cast to the appropriate type for method chaining.
		 * Subclasses should override this method to return the correct type.
		 * @return this builder instance
		 */
		@SuppressWarnings("unchecked")
		protected T self() {
			return (T) this;
		}

		/**
		 * Sets the ToolCallingManager to be used by the advisor.
		 * @param toolCallingManager the ToolCallingManager instance
		 * @return this Builder instance for method chaining
		 */
		public T toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return self();
		}

		/**
		 * Sets the order of the advisor in the advisor chain.
		 * @param advisorOrder the order value, must be between HIGHEST_PRECEDENCE and
		 * LOWEST_PRECEDENCE
		 * @return this Builder instance for method chaining
		 */
		public T advisorOrder(int advisorOrder) {
			this.advisorOrder = advisorOrder;
			return self();
		}

		/**
		 * Sets whether internal conversation history is enabled. If false, you need a
		 * ChatMemory Advisor registered next in the chain.
		 * @param conversationHistoryEnabled true to enable, false to disable
		 * @return this Builder instance for method chaining
		 */
		public T conversationHistoryEnabled(boolean conversationHistoryEnabled) {
			this.conversationHistoryEnabled = conversationHistoryEnabled;
			return self();
		}

		/**
		 * Disables internal conversation history. You need a ChatMemory Advisor
		 * registered next in the chain.
		 * @return this Builder instance for method chaining
		 */
		public T disableMemory() {
			this.conversationHistoryEnabled = false;
			return self();
		}

		/**
		 * Sets whether intermediate tool call responses should be streamed to downstream
		 * consumers. When enabled (default), all chunks including tool call responses are
		 * streamed in real-time. When disabled, only the final answer chunks are
		 * streamed, and intermediate tool call responses are filtered out.
		 * @param streamToolCallResponses true to stream tool call responses (default),
		 * false to filter them out
		 * @return this Builder instance for method chaining
		 */
		public T streamToolCallResponses(boolean streamToolCallResponses) {
			this.streamToolCallResponses = streamToolCallResponses;
			return self();
		}

		/**
		 * Disables streaming of intermediate tool call responses. Only the final answer
		 * will be streamed to downstream consumers.
		 * @return this Builder instance for method chaining
		 */
		public T suppressToolCallStreaming() {
			this.streamToolCallResponses = false;
			return self();
		}

		/**
		 * Returns the configured ToolCallingManager.
		 * @return the ToolCallingManager instance
		 */
		protected ToolCallingManager getToolCallingManager() {
			return this.toolCallingManager;
		}

		/**
		 * Returns the configured advisor order.
		 * @return the advisor order value
		 */
		protected int getAdvisorOrder() {
			return this.advisorOrder;
		}

		/**
		 * Returns whether tool call responses should be streamed.
		 * @return true if tool call responses should be streamed
		 */
		protected boolean isStreamToolCallResponses() {
			return this.streamToolCallResponses;
		}

		/**
		 * Builds and returns a new ToolCallAdvisor instance with the configured
		 * properties.
		 * @return a new ToolCallAdvisor instance
		 * @throws IllegalArgumentException if toolCallingManager is null or advisorOrder
		 * is out of valid range
		 */
		public ToolCallAdvisor build() {
			return new ToolCallAdvisor(this.toolCallingManager, this.advisorOrder, this.conversationHistoryEnabled,
					this.streamToolCallResponses);
		}

	}

}
