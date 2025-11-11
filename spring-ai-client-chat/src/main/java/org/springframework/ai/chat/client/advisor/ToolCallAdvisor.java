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

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
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
public final class ToolCallAdvisor implements CallAdvisor, StreamAdvisor {

	private final ToolCallingManager toolCallingManager;

	/**
	 * Set the order close to {@link Ordered#LOWEST_PRECEDENCE} to ensure an advisor is
	 * executed first in the chain (first for request processing, last for response
	 * processing).
	 * <p>
	 * https://docs.spring.io/spring-ai/reference/api/advisors.html#_advisor_order
	 */
	private final int advisorOrder;

	private ToolCallAdvisor(ToolCallingManager toolCallingManager, int advisorOrder) {
		Assert.notNull(toolCallingManager, "toolCallingManager must not be null");
		Assert.isTrue(advisorOrder > BaseAdvisor.HIGHEST_PRECEDENCE && advisorOrder < BaseAdvisor.LOWEST_PRECEDENCE,
				"advisorOrder must be between HIGHEST_PRECEDENCE and LOWEST_PRECEDENCE");

		this.toolCallingManager = toolCallingManager;
		this.advisorOrder = advisorOrder;
	}

	@Override
	public String getName() {
		return "Tool Calling Advisor";
	}

	@Override
	public int getOrder() {
		return this.advisorOrder;
	}

	@SuppressWarnings("null")
	@Override
	public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		Assert.notNull(callAdvisorChain, "callAdvisorChain must not be null");
		Assert.notNull(chatClientRequest, "chatClientRequest must not be null");

		if (chatClientRequest.prompt().getOptions() == null
				|| !(chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions)) {
			throw new IllegalArgumentException(
					"ToolCall Advisor requires ToolCallingChatOptions to be set in the ChatClientRequest options.");
		}

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
			chatClientResponse = callAdvisorChain.copy(this).nextCall(processedChatClientRequest);

			// After Call

			// TODO: check that this is tool call is sufficiant for all chat models
			// that support tool calls. (e.g. Anthropic and Bedrock are checking for
			// finish status as well)
			isToolCall = chatClientResponse.chatResponse() != null && chatClientResponse.chatResponse().hasToolCalls();

			if (isToolCall) {

				ToolExecutionResult toolExecutionResult = this.toolCallingManager
					.executeToolCalls(processedChatClientRequest.prompt(), chatClientResponse.chatResponse());

				if (toolExecutionResult.returnDirect()) {

					// Return tool execution result directly to the application client.
					chatClientResponse = chatClientResponse.mutate()
						.chatResponse(ChatResponse.builder()
							.from(chatClientResponse.chatResponse())
							.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
							.build())
						.build();

					// Interupt the tool calling loop and return the tool execution result
					// directly to the client application instead of returning it to the
					// LLM.
					break;
				}

				instructions = toolExecutionResult.conversationHistory();
			}

		}
		while (isToolCall); // loop until no tool calls are present

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
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for creating instances of ToolCallAdvisor.
	 */
	public final static class Builder {

		private ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

		private int advisorOrder = BaseAdvisor.HIGHEST_PRECEDENCE + 300;

		private Builder() {
		}

		/**
		 * Sets the ToolCallingManager to be used by the advisor.
		 * @param toolCallingManager the ToolCallingManager instance
		 * @return this Builder instance for method chaining
		 */
		public Builder toolCallingManager(ToolCallingManager toolCallingManager) {
			this.toolCallingManager = toolCallingManager;
			return this;
		}

		/**
		 * Sets the order of the advisor in the advisor chain.
		 * @param advisorOrder the order value, must be between HIGHEST_PRECEDENCE and
		 * LOWEST_PRECEDENCE
		 * @return this Builder instance for method chaining
		 */
		public Builder advisorOrder(int advisorOrder) {
			this.advisorOrder = advisorOrder;
			return this;
		}

		/**
		 * Builds and returns a new ToolCallAdvisor instance with the configured
		 * properties.
		 * @return a new ToolCallAdvisor instance
		 * @throws IllegalArgumentException if toolCallingManager is null or advisorOrder
		 * is out of valid range
		 */
		public ToolCallAdvisor build() {
			return new ToolCallAdvisor(this.toolCallingManager, this.advisorOrder);
		}

	}

}
