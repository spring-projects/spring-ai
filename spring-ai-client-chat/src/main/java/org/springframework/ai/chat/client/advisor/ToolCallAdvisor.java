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

import java.util.List;

import reactor.core.publisher.Flux;

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

	protected ToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder) {
		this(toolCallingManager, advisorOrder, true);
	}

	protected ToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder,
			boolean conversationHistoryEnabled) {
		Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
		Assert.isTrue(advisorOrder > BaseAdvisor.HIGHEST_PRECEDENCE && advisorOrder < BaseAdvisor.LOWEST_PRECEDENCE,
				"advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");

		this.toolCallingManager = toolCallingManager;
		this.advisorOrder = advisorOrder;
		this.conversationHistoryEnabled = conversationHistoryEnabled;
	}

	@Override
	public String getName() {
		return "Tool Calling Advisor";
	}

	@Override
	public int getOrder() {
		return this.advisorOrder;
	}

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

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		return Flux.error(new UnsupportedOperationException("Unimplemented method 'adviseStream'"));
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
		 * Builds and returns a new ToolCallAdvisor instance with the configured
		 * properties.
		 * @return a new ToolCallAdvisor instance
		 * @throws IllegalArgumentException if toolCallingManager is null or advisorOrder
		 * is out of valid range
		 */
		public ToolCallAdvisor build() {
			return new ToolCallAdvisor(this.toolCallingManager, this.advisorOrder, this.conversationHistoryEnabled);
		}

	}

}
