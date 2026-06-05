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

package org.springframework.ai.chat.client.advisor;

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
import org.springframework.ai.chat.client.advisor.api.ToolAdvisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;
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
 * @since 2.0.0
 */
public class ToolCallingAdvisor implements CallAdvisor, StreamAdvisor, ToolAdvisor {

	private static final ChatClientMessageAggregator CHAT_CLIENT_MESSAGE_AGGREGATOR = new ChatClientMessageAggregator();

	/**
	 * Default advisor order. Placed early in the chain so that all downstream advisors
	 * (e.g. {@link org.springframework.ai.chat.client.advisor.api.BaseChatMemoryAdvisor})
	 * participate in every tool-call iteration.
	 *
	 * @see org.springframework.ai.chat.client.advisor.api.Advisor#DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER
	 */
	public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 300;

	protected static final ToolExecutionEligibilityChecker DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER = chatResponse -> chatResponse != null
			&& chatResponse.hasToolCalls();

	protected final ToolCallingManager toolCallingManager;

	private final ToolExecutionEligibilityChecker toolExecutionEligibilityChecker;

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

	protected ToolCallingAdvisor(ToolCallingManager toolCallingManager,
			ToolExecutionEligibilityChecker toolExecutionEligibilityChecker, int advisorOrder,
			boolean conversationHistoryEnabled, boolean streamToolCallResponses) {
		Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
		Assert.notNull(toolExecutionEligibilityChecker, "toolExecutionEligibilityChecker must not be null");
		Assert.isTrue(advisorOrder > BaseAdvisor.HIGHEST_PRECEDENCE && advisorOrder < BaseAdvisor.LOWEST_PRECEDENCE,
				"advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");

		this.toolCallingManager = toolCallingManager;
		this.toolExecutionEligibilityChecker = toolExecutionEligibilityChecker;
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

		ChatOptions options = chatClientRequest.prompt().getOptions();
		if (!(options instanceof ToolCallingChatOptions)) {
			throw new IllegalArgumentException(
					"ToolCall Advisor requires ToolCallingChatOptions to be set in the ChatClientRequest options.");
		}

		chatClientRequest = this.doInitializeLoop(chatClientRequest, callAdvisorChain);

		var optionsCopy = ((ToolCallingChatOptions) options).mutate().build();

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

			ChatResponse chatResponse = chatClientResponse.chatResponse();
			isToolCall = this.toolExecutionEligibilityChecker.isToolCallResponse(chatResponse);

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
			List<Message> history = toolExecutionResult.conversationHistory();
			if (history.isEmpty()) {
				return history;
			}
			return List.of(chatClientRequest.prompt().getSystemMessage(), history.get(history.size() - 1));
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

		var optionsCopy = ((ToolCallingChatOptions.Builder<?>) chatClientRequest.prompt().getOptions().mutate())
			.build();

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

			Flux<ChatClientResponse> responseFlux = chainCopy.nextStream(processedRequest);

			return streamWithToolCallResponses(responseFlux, finalRequest, streamAdvisorChain, originalRequest,
					optionsCopy);
		});
	}

	private Flux<ChatClientResponse> streamWithToolCallResponses(Flux<ChatClientResponse> responseFlux,
			ChatClientRequest finalRequest, StreamAdvisorChain streamAdvisorChain, ChatClientRequest originalRequest,
			ToolCallingChatOptions optionsCopy) {

		AtomicReference<ChatClientResponse> aggregatedResponseRef = new AtomicReference<>();

		return CHAT_CLIENT_MESSAGE_AGGREGATOR.aggregateChatClientResponse(responseFlux, aggregatedResponseRef::set)
			.concatWith(Flux.defer(() -> this.handleToolCallRecursion(aggregatedResponseRef.get(), finalRequest,
					streamAdvisorChain, originalRequest, optionsCopy)))
			.filter(ccr -> this.streamToolCallResponses
					|| !this.toolExecutionEligibilityChecker.isToolCallResponse(ccr.chatResponse()));
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
		boolean isToolCall = this.toolExecutionEligibilityChecker.isToolCallResponse(chatResponse);

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
			List<Message> history = toolExecutionResult.conversationHistory();
			if (history.isEmpty()) {
				return history;
			}
			return List.of(chatClientRequest.prompt().getSystemMessage(), history.get(history.size() - 1));
		}

		return toolExecutionResult.conversationHistory();
	}

	/**
	 * Creates a new Builder instance for constructing a ToolCallingAdvisor.
	 * @return a new Builder instance
	 */
	public static Builder<?> builder() {
		return new Builder<>();
	}

	/**
	 * Builder for creating instances of ToolCallingAdvisor.
	 * <p>
	 * This builder uses the self-referential generic pattern to support extensibility.
	 *
	 * @param <T> the builder type, used for self-referential generics to support method
	 * chaining in subclasses
	 */
	public static class Builder<T extends Builder<T>> {

		private ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

		private ToolExecutionEligibilityChecker toolExecutionEligibilityChecker = DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER;

		private int advisorOrder = DEFAULT_ORDER;

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
		 * Sets the checker that determines whether a model response should trigger tool
		 * execution. Defaults to
		 * {@code chatResponse -> chatResponse != null && chatResponse.hasToolCalls()}.
		 * Override to apply provider-specific stop-reason logic.
		 * @param toolExecutionEligibilityChecker the checker
		 * @return this Builder instance for method chaining
		 */
		public T toolExecutionEligibilityChecker(ToolExecutionEligibilityChecker toolExecutionEligibilityChecker) {
			this.toolExecutionEligibilityChecker = toolExecutionEligibilityChecker;
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
		public T disableInternalConversationHistory() {
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
		public int getAdvisorOrder() {
			return this.advisorOrder;
		}

		/**
		 * Returns the configured ToolExecutionEligibilityChecker.
		 * @return the ToolExecutionEligibilityChecker instance
		 */
		public ToolExecutionEligibilityChecker getToolExecutionEligibilityChecker() {
			return this.toolExecutionEligibilityChecker;
		}

		/**
		 * Creates a shallow copy of this builder with all current settings. The copy can
		 * be used as a template from which per-call overrides (e.g.
		 * {@code conversationHistoryEnabled}, {@code streamToolCallResponses}) are
		 * applied without mutating the original.
		 * <p>
		 * Subclasses must override {@link #newCopy()} to return an instance of their own
		 * type, and override this method to copy their additional fields into it.
		 * @return a new {@link Builder} with the same configuration
		 */
		public Builder<?> copy() {
			Builder<?> copy = newCopy();
			copy.toolCallingManager = this.toolCallingManager;
			copy.toolExecutionEligibilityChecker = this.toolExecutionEligibilityChecker;
			copy.advisorOrder = this.advisorOrder;
			copy.conversationHistoryEnabled = this.conversationHistoryEnabled;
			copy.streamToolCallResponses = this.streamToolCallResponses;
			return copy;
		}

		/**
		 * Factory method called by {@link #copy()} to create the blank builder instance
		 * that will receive the copied fields. Subclasses must override this to return an
		 * instance of their own builder type so that {@link #copy()} preserves the full
		 * subtype.
		 * @return a new, unconfigured builder of the same concrete type
		 */
		protected Builder<?> newCopy() {
			return new Builder<>();
		}

		/**
		 * Returns whether tool call responses should be streamed.
		 * @return true if tool call responses should be streamed
		 */
		protected boolean isStreamToolCallResponses() {
			return this.streamToolCallResponses;
		}

		/**
		 * Returns whether internal conversation history is enabled.
		 * @return true if internal conversation history is enabled, false if disabled
		 */
		protected boolean isConversationHistoryEnabled() {
			return this.conversationHistoryEnabled;
		}

		/**
		 * Builds and returns a new ToolCallingAdvisor instance with the configured
		 * properties.
		 * @return a new ToolCallingAdvisor instance
		 * @throws IllegalArgumentException if toolCallingManager is null or advisorOrder
		 * is out of valid range
		 */
		public ToolCallingAdvisor build() {
			return new ToolCallingAdvisor(this.toolCallingManager, this.toolExecutionEligibilityChecker,
					this.advisorOrder, this.conversationHistoryEnabled, this.streamToolCallResponses);
		}

	}

}
