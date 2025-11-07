/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.model;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

/**
 * Abstract base class for ChatModel implementations that provides common functionality
 * for observation, retry, and tool calling orchestration.
 *
 * <p>
 * Subclasses need to:
 * <ul>
 * <li>Initialize the protected fields in their constructors</li>
 * <li>Implement {@link #getProviderName()} to return the provider identifier</li>
 * <li>Implement {@link #doCall(Prompt, ChatResponse)} for the actual API interaction</li>
 * <li>Implement {@link #doStream(Prompt, ChatResponse)} for streaming API
 * interaction</li>
 * <li>Implement {@link #buildRequestPrompt(Prompt prompt)} for builds the final request
 * prompt by merging options</li>
 * </ul>
 *
 * @author Fu Jian
 * @since 1.1.0
 */
public abstract class AbstractObservableChatModel implements ChatModel, StreamingChatModel {

	private static final ChatModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultChatModelObservationConvention();

	protected ObservationRegistry observationRegistry;

	protected RetryTemplate retryTemplate;

	protected ToolCallingManager toolCallingManager;

	protected ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate;

	protected ChatModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Constructor for AbstractObservableChatModel.
	 * @param observationRegistry the observation registry
	 * @param retryTemplate the retry template
	 * @param toolCallingManager the tool calling manager
	 * @param toolExecutionEligibilityPredicate the tool execution eligibility predicate
	 */
	protected AbstractObservableChatModel(ObservationRegistry observationRegistry, RetryTemplate retryTemplate,
			ToolCallingManager toolCallingManager,
			ToolExecutionEligibilityPredicate toolExecutionEligibilityPredicate) {
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(retryTemplate, "retryTemplate cannot be null");
		Assert.notNull(toolCallingManager, "toolCallingManager cannot be null");
		Assert.notNull(toolExecutionEligibilityPredicate, "toolExecutionEligibilityPredicate cannot be null");

		this.observationRegistry = observationRegistry;
		this.retryTemplate = retryTemplate;
		this.toolCallingManager = toolCallingManager;
		this.toolExecutionEligibilityPredicate = toolExecutionEligibilityPredicate;
	}

	/**
	 * Sets the observation convention for this chat model.
	 * @param observationConvention the observation convention to use
	 */
	public void setObservationConvention(ChatModelObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}

	@Override
	public final ChatResponse call(Prompt prompt) {
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return call(requestPrompt, null);
	}

	/**
	 * Internal call method that handles observation, retry, and tool calling
	 * orchestration. This method can be called recursively for tool execution.
	 * @param prompt the prompt to process
	 * @param previousChatResponse the previous chat response
	 * @return the final chat response
	 */
	protected ChatResponse call(Prompt prompt, ChatResponse previousChatResponse) {
		ChatModelObservationContext observationContext = createObservationContext(prompt);

		ChatResponse response = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				ChatResponse chatResponse = this.retryTemplate.execute(ctx -> doCall(prompt, previousChatResponse));
				if (observationContext != null) {
					observationContext.setResponse(chatResponse);
				}
				return chatResponse;
			});

		if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)
				&& shouldExecuteTools(prompt, response)) {
			var toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
			if (toolExecutionResult.returnDirect()) {
				// Return tool execution result directly to the client.
				return ChatResponse.builder()
					.from(response)
					.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
					.build();
			}
			else {
				// Send the tool execution result back to the model.
				return call(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()), response);
			}
		}

		return response;
	}

	/**
	 * Creates the observation context for the given prompt. Subclasses can override this
	 * to customize the observation context.
	 * @param prompt the prompt
	 * @return the observation context
	 */
	protected ChatModelObservationContext createObservationContext(Prompt prompt) {
		return ChatModelObservationContext.builder().prompt(prompt).provider(getProviderName()).build();
	}

	/**
	 * Returns the provider name for observation context.
	 * @return the provider name (e.g., "openai", "anthropic")
	 */
	protected abstract String getProviderName();

	/**
	 * Performs the actual chat completion API call. Subclasses should implement their
	 * provider-specific API interaction here, without worrying about observation, retry,
	 * or tool calling orchestration.
	 * @param prompt the prompt to send to the model
	 * @param previousChatResponse the previous chat response tracking, or null if this is
	 * the first call
	 * @return the chat response from the model
	 */
	protected abstract ChatResponse doCall(Prompt prompt, ChatResponse previousChatResponse);

	/**
	 * Additional condition check for tool execution. Subclasses can override this to add
	 * provider-specific conditions (e.g., checking finish reasons).
	 * @param prompt the prompt
	 * @param response the response
	 * @return true if tools should be executed
	 */
	protected boolean shouldExecuteTools(Prompt prompt, ChatResponse response) {
		return true;
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		// Before moving any further, build the final request Prompt,
		// merging runtime and default options.
		Prompt requestPrompt = buildRequestPrompt(prompt);
		return stream(requestPrompt, null);
	}

	/**
	 * Builds the final request prompt by merging runtime options with default options.
	 * Subclasses should implement this method to handle provider-specific option merging
	 * and validation logic.
	 * @param prompt the original prompt with runtime options
	 * @return the final prompt with merged options ready for API call
	 */
	protected abstract Prompt buildRequestPrompt(Prompt prompt);

	/**
	 * Internal stream method that handles observation and tool calling orchestration for
	 * streaming responses. This method can be called recursively for tool execution.
	 * @param prompt the prompt to process
	 * @param previousChatResponse the previous chat response
	 * @return a flux of chat responses
	 */
	protected Flux<ChatResponse> stream(Prompt prompt, ChatResponse previousChatResponse) {
		return Flux.deferContextual(contextView -> {
			ChatModelObservationContext observationContext = createObservationContext(prompt);

			Observation observation = ChatModelObservationDocumentation.CHAT_MODEL_OPERATION.observation(
					this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry);

			observation.parentObservation(contextView.getOrDefault(ObservationThreadLocalAccessor.KEY, null)).start();

			Flux<ChatResponse> chatResponseFlux = this.retryTemplate
				.execute(ctx -> doStream(prompt, previousChatResponse));

			Flux<ChatResponse> flux = chatResponseFlux.flatMap(response -> {
				if (this.toolExecutionEligibilityPredicate.isToolExecutionRequired(prompt.getOptions(), response)
						&& shouldExecuteTools(prompt, response)) {
					// FIXME: bounded elastic needs to be used since tool calling
					// is currently only synchronous
					return Flux.deferContextual(ctx -> {
						ToolExecutionResult toolExecutionResult;
						try {
							ToolCallReactiveContextHolder.setContext(ctx);
							toolExecutionResult = this.toolCallingManager.executeToolCalls(prompt, response);
						}
						finally {
							ToolCallReactiveContextHolder.clearContext();
						}
						if (toolExecutionResult.returnDirect()) {
							// Return tool execution result directly to the client.
							return Flux.just(ChatResponse.builder()
								.from(response)
								.generations(ToolExecutionResult.buildGenerations(toolExecutionResult))
								.build());
						}
						else {
							// Send the tool execution result back to the model.
							return stream(new Prompt(toolExecutionResult.conversationHistory(), prompt.getOptions()),
									response);
						}
					}).subscribeOn(Schedulers.boundedElastic());
				}
				else {
					return Flux.just(response);
				}
			})
				.doOnError(observation::error)
				.doFinally(s -> observation.stop())
				.contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));

			return new MessageAggregator().aggregate(flux, observationContext::setResponse);
		});
	}

	/**
	 * Performs the actual streaming chat completion API call. Subclasses should implement
	 * their provider-specific streaming API interaction here, without worrying about
	 * observation or tool calling orchestration. Note that retry is handled outside the
	 * stream for streaming calls.
	 * @param prompt the prompt to send to the model
	 * @param previousChatResponse the previous chat response tracking, or null if this is
	 * the first call
	 * @return a flux of chat responses from the model
	 */
	protected abstract Flux<ChatResponse> doStream(Prompt prompt, ChatResponse previousChatResponse);

}
