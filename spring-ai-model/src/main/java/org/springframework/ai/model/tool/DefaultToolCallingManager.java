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

package org.springframework.ai.model.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.internal.ToolCallReactiveContextHolder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.observation.DefaultToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of {@link ToolCallingManager}.
 *
 * @author Thomas Vitale
 * @author chabinhwang
 * @author Christian Tzolov
 * @author Dimitar Proynov
 * @since 1.0.0
 */
public final class DefaultToolCallingManager implements ToolCallingManager {

	private static final Log logger = LogFactory.getLog(DefaultToolCallingManager.class);

	// @formatter:off

	private static final ObservationRegistry DEFAULT_OBSERVATION_REGISTRY
			= ObservationRegistry.NOOP;

	private static final ToolCallingObservationConvention DEFAULT_OBSERVATION_CONVENTION
			= new DefaultToolCallingObservationConvention();

	private static final ToolCallbackResolver DEFAULT_TOOL_CALLBACK_RESOLVER
			= new DelegatingToolCallbackResolver(List.of());

	private static final ToolExecutionExceptionProcessor DEFAULT_TOOL_EXECUTION_EXCEPTION_PROCESSOR
			= DefaultToolExecutionExceptionProcessor.builder().build();

	private static final boolean DEFAULT_RESOLUTION_FALLBACK_ENABLED = false;

	private static final String POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING_START = "LLM may have adapted the tool name '";
	private static final String POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING_END
			= "', especially if the name was truncated due to length limits. If this is the case, you can customize the prefixing and processing logic using McpToolNamePrefixGenerator";

	// @formatter:on

	/**
	 * Default cap applied to every tool name unless overridden via
	 * {@link Builder#maxCallsPerTool(String, int)}, exempted via
	 * {@link Builder#excludeToolFromLimit(String)}, or disabled entirely via
	 * {@link Builder#unlimitedCallsPerTool()}. Generous enough for legitimate iterative
	 * tool use (pagination, retries) while still catching a runaway loop long before it
	 * becomes expensive.
	 */
	public static final int DEFAULT_MAX_CALLS_PER_TOOL = 40;

	/**
	 * Default cap on the total number of tool calls, across all tools combined, allowed
	 * within a turn, unless disabled entirely via
	 * {@link Builder#unlimitedTotalToolCalls()}.
	 */
	public static final int DEFAULT_MAX_TOTAL_TOOL_CALLS = 150;

	private final ObservationRegistry observationRegistry;

	private final ToolCallbackResolver toolCallbackResolver;

	private final ToolExecutionExceptionProcessor toolExecutionExceptionProcessor;

	private final ToolCallLimits toolCallLimits;

	/**
	 * Whether a call to a tool that is not part of the request's tool callbacks may still
	 * be executed by resolving it through the {@link ToolCallbackResolver}. When
	 * {@code false} (the default), the tools attached to the request are the only tools
	 * that can be executed.
	 */
	private final boolean resolutionFallbackEnabled;

	/** Which tool calls need a person's confirmation before they run. */
	private final ToolCallConfirmationPolicy toolCallConfirmationPolicy;

	private ToolCallingObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public DefaultToolCallingManager(ObservationRegistry observationRegistry, ToolCallbackResolver toolCallbackResolver,
			ToolExecutionExceptionProcessor toolExecutionExceptionProcessor) {
		this(observationRegistry, toolCallbackResolver, toolExecutionExceptionProcessor,
				new ToolCallLimits(DEFAULT_MAX_CALLS_PER_TOOL, Map.of(), Set.of(), DEFAULT_MAX_TOTAL_TOOL_CALLS,
						ToolCallLimitBehavior.THROW),
				DEFAULT_RESOLUTION_FALLBACK_ENABLED);
	}

	DefaultToolCallingManager(ObservationRegistry observationRegistry, ToolCallbackResolver toolCallbackResolver,
			ToolExecutionExceptionProcessor toolExecutionExceptionProcessor, ToolCallLimits toolCallLimits) {
		this(observationRegistry, toolCallbackResolver, toolExecutionExceptionProcessor, toolCallLimits,
				DEFAULT_RESOLUTION_FALLBACK_ENABLED);
	}

	/**
	 * Create a {@link DefaultToolCallingManager}.
	 * @param observationRegistry the observation registry
	 * @param toolCallbackResolver the resolver consulted for name-based tool resolution
	 * @param toolExecutionExceptionProcessor the processor for tool execution exceptions
	 * @param toolCallLimits the limits applied to tool calls within a turn
	 * @param resolutionFallbackEnabled whether tools that are not part of the request's
	 * tool callbacks may be resolved and executed via the {@code toolCallbackResolver};
	 * defaults to {@code false} so that the request's tool callbacks bound execution
	 * @since 2.0.1
	 */
	DefaultToolCallingManager(ObservationRegistry observationRegistry, ToolCallbackResolver toolCallbackResolver,
			ToolExecutionExceptionProcessor toolExecutionExceptionProcessor, ToolCallLimits toolCallLimits,
			boolean resolutionFallbackEnabled) {
		this(observationRegistry, toolCallbackResolver, toolExecutionExceptionProcessor, toolCallLimits,
				resolutionFallbackEnabled, ToolCallConfirmationPolicy.fromToolMetadata());
	}

	/**
	 * Create a {@link DefaultToolCallingManager}.
	 * @param observationRegistry the observation registry
	 * @param toolCallbackResolver the resolver consulted for name-based tool resolution
	 * @param toolExecutionExceptionProcessor the processor for tool execution exceptions
	 * @param toolCallLimits the limits applied to tool calls within a turn
	 * @param resolutionFallbackEnabled whether tools that are not part of the request's
	 * tool callbacks may be resolved and executed via the {@code toolCallbackResolver}
	 * @param toolCallConfirmationPolicy which tool calls need a person's confirmation
	 * before they run; see {@link ToolCallDecisions}
	 * @since 2.1.0
	 */
	DefaultToolCallingManager(ObservationRegistry observationRegistry, ToolCallbackResolver toolCallbackResolver,
			ToolExecutionExceptionProcessor toolExecutionExceptionProcessor, ToolCallLimits toolCallLimits,
			boolean resolutionFallbackEnabled, ToolCallConfirmationPolicy toolCallConfirmationPolicy) {
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		Assert.notNull(toolCallbackResolver, "toolCallbackResolver cannot be null");
		Assert.notNull(toolExecutionExceptionProcessor, "toolCallExceptionConverter cannot be null");
		Assert.notNull(toolCallLimits, "toolCallLimits cannot be null");
		Assert.notNull(toolCallConfirmationPolicy, "toolCallConfirmationPolicy cannot be null");

		this.observationRegistry = observationRegistry;
		this.toolCallbackResolver = toolCallbackResolver;
		this.toolExecutionExceptionProcessor = toolExecutionExceptionProcessor;
		this.toolCallLimits = toolCallLimits;
		this.resolutionFallbackEnabled = resolutionFallbackEnabled;
		this.toolCallConfirmationPolicy = toolCallConfirmationPolicy;
	}

	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
		Assert.notNull(chatOptions, "chatOptions cannot be null");

		List<ToolCallback> toolCallbacks = new ArrayList<>(
				!CollectionUtils.isEmpty(chatOptions.getToolCallbacks()) ? chatOptions.getToolCallbacks() : List.of());

		return toolCallbacks.stream().map(ToolCallback::getToolDefinition).toList();
	}

	@Override
	public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
		return this.doExecuteToolCalls(prompt, chatResponse, null);
	}

	@Override
	public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse, ToolCallDecisions decisions) {
		Assert.notNull(decisions, "decisions cannot be null");
		return this.doExecuteToolCalls(prompt, chatResponse, decisions);
	}

	private ToolExecutionResult doExecuteToolCalls(Prompt prompt, ChatResponse chatResponse,
			@Nullable ToolCallDecisions decisions) {
		Assert.notNull(prompt, "prompt cannot be null");
		Assert.notNull(chatResponse, "chatResponse cannot be null");

		Optional<Generation> toolCallGeneration = chatResponse.getResults()
			.stream()
			.filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
			.findFirst();

		if (toolCallGeneration.isEmpty()) {
			throw new IllegalStateException("No tool call requested by the chat model");
		}

		AssistantMessage assistantMessage = toolCallGeneration.get().getOutput();

		ToolContext toolContext = buildToolContext(prompt, assistantMessage);

		InternalToolExecutionResult internalToolExecutionResult = executeToolCall(prompt, assistantMessage, toolContext,
				decisions);

		// A resumed batch may come with the conversation up to and including the reply;
		// the history then holds the reply once
		List<Message> previousMessages = prompt.getInstructions();
		if (endsWith(previousMessages, assistantMessage)) {
			previousMessages = previousMessages.subList(0, previousMessages.size() - 1);
		}
		List<Message> conversationHistory = buildConversationHistoryAfterToolExecution(previousMessages,
				assistantMessage, internalToolExecutionResult.toolResponseMessage());

		return ToolExecutionResult.builder()
			.conversationHistory(conversationHistory)
			.returnDirect(internalToolExecutionResult.returnDirect())
			.build();
	}

	private static ToolContext buildToolContext(Prompt prompt, AssistantMessage assistantMessage) {
		Map<String, Object> toolContextMap = Map.of();

		if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions
				&& !CollectionUtils.isEmpty(toolCallingChatOptions.getToolContext())) {
			toolContextMap = new HashMap<>(toolCallingChatOptions.getToolContext());
		}

		return new ToolContext(toolContextMap);
	}

	/**
	 * Whether the messages end with a reply holding the same tool calls as the message.
	 */
	private static boolean endsWith(List<Message> messages, AssistantMessage assistantMessage) {
		return !messages.isEmpty() && messages.get(messages.size() - 1) instanceof AssistantMessage last
				&& last.getToolCalls().equals(assistantMessage.getToolCalls());
	}

	/**
	 * Execute the tool call and return the response message.
	 */
	private InternalToolExecutionResult executeToolCall(Prompt prompt, AssistantMessage assistantMessage,
			ToolContext toolContext, @Nullable ToolCallDecisions decisions) {
		List<ToolCallback> toolCallbacks = List.of();
		if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
			if (!CollectionUtils.isEmpty(toolCallingChatOptions.getToolCallbacks())) {
				toolCallbacks = toolCallingChatOptions.getToolCallbacks();
			}
		}

		// Before any call runs: a call that needs a person's confirmation pauses the
		// whole batch, unless the decisions cover it
		if (decisions == null) {
			// The decisions name the calls by id, so a reply that pauses gets one per
			// call
			AssistantMessage withIds = withToolCallIds(assistantMessage);
			List<String> awaiting = this.awaitingConfirmation(withIds, toolCallbacks);
			if (!awaiting.isEmpty()) {
				throw new ToolCallConfirmationRequiredException(awaiting, withIds);
			}
		}
		else {
			checkDecisions(decisions, assistantMessage, toolCallbacks);
		}

		List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

		Boolean returnDirect = null;
		boolean denied = false;

		Map<String, Integer> toolCallCounts = ToolCallLimits.countPriorToolCalls(prompt.getInstructions());
		int totalToolCallCount = toolCallCounts.values().stream().mapToInt(Integer::intValue).sum();

		for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

			String toolName = toolCall.name();

			ToolCallDecision decision = (decisions != null) ? decisions.decisionOn(toolCall.id()) : null;
			if (decision != null && !decision.allowed()) {
				// A denied call does not run; the model receives the decision's message
				toolResponses
					.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, decision.denyMessage()));
				denied = true;
				continue;
			}

			if (logger.isDebugEnabled()) {
				logger.debug("Executing tool call: " + toolCall.name());
			}

			totalToolCallCount++;
			int toolCallCount = toolCallCounts.merge(toolName, 1, Integer::sum);

			ToolCallLimits.Breach limitBreach = this.toolCallLimits.check(toolName, toolCallCount, totalToolCallCount);
			if (limitBreach != null) {
				toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, limitBreach.message()));

				if (this.toolCallLimits.onLimitExceeded() == ToolCallLimitBehavior.THROW) {
					ToolResponseMessage partialToolResponseMessage = ToolResponseMessage.builder()
						.responses(toolResponses)
						.build();
					List<Message> partialConversationHistory = buildConversationHistoryAfterToolExecution(
							prompt.getInstructions(), assistantMessage, partialToolResponseMessage);
					ToolExecutionResult partialToolExecutionResult = ToolExecutionResult.builder()
						.conversationHistory(partialConversationHistory)
						.returnDirect(Objects.requireNonNullElse(returnDirect, false))
						.build();
					throw new ToolCallLimitExceededException(limitBreach.toolName(), limitBreach.limit(),
							partialToolExecutionResult);
				}

				// ToolCallLimitBehavior.RETURN_ERROR_RESPONSE: skip invoking this tool
				// call but keep processing the rest of the batch.
				continue;
			}

			String toolInputArguments = toolCall.arguments();

			// Handle the possible null parameter situation in streaming mode.
			final String finalToolInputArguments;
			if (!StringUtils.hasText(toolInputArguments)) {
				if (logger.isWarnEnabled()) {
					logger.warn("Tool call arguments are null or empty for tool: " + toolName
							+ ". Using empty JSON object as default.");
				}
				finalToolInputArguments = "{}";
			}
			else {
				finalToolInputArguments = toolInputArguments;
			}

			ToolCallback toolCallback = this.resolveToolCallback(toolCallbacks, toolName);

			if (toolCallback == null) {
				if (logger.isWarnEnabled()) {
					logger.warn(POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING_START + toolName
							+ POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING_END);
				}
				throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
			}

			if (returnDirect == null) {
				returnDirect = toolCallback.getToolMetadata().returnDirect();
			}
			else {
				returnDirect = returnDirect && toolCallback.getToolMetadata().returnDirect();
			}

			// In streaming/reactive mode the parent observation is propagated through the
			// Reactor context captured in ToolCallReactiveContextHolder. In blocking mode
			// that holder is never populated, so fall back to the observation currently
			// in scope on the calling thread to keep the observation hierarchy intact.
			Observation parent = ToolCallReactiveContextHolder.getContext()
				.getOrDefault(ObservationThreadLocalAccessor.KEY, this.observationRegistry.getCurrentObservation());

			ToolCallingObservationContext observationContext = ToolCallingObservationContext.builder()
				.toolDefinition(toolCallback.getToolDefinition())
				.toolMetadata(toolCallback.getToolMetadata())
				.toolCallId(toolCall.id())
				.toolType(toolCall.type())
				.toolCallArguments(finalToolInputArguments)
				.build();

			String toolCallResult = ToolCallingObservationDocumentation.TOOL_CALL
				.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
						this.observationRegistry)
				.parentObservation(parent)
				.observe(() -> {
					String toolResult;
					try {
						toolResult = toolCallback.call(finalToolInputArguments, toolContext);
					}
					catch (ToolExecutionException ex) {
						toolResult = this.toolExecutionExceptionProcessor.process(ex);
					}
					observationContext.setToolCallResult(toolResult);
					return toolResult;
				});

			toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolName,
					toolCallResult != null ? toolCallResult : ""));
		}

		// Straight back to the caller only when every call ran and asked for it
		return new InternalToolExecutionResult(ToolResponseMessage.builder().responses(toolResponses).build(),
				Objects.requireNonNullElse(returnDirect, false) && !denied);
	}

	/**
	 * The message with an id of its own on every tool call: the model's, or a made-up one
	 * for a call the model gave none or one it gave another call too, since decisions
	 * name the calls by id.
	 */
	private static AssistantMessage withToolCallIds(AssistantMessage assistantMessage) {
		if (hasDistinctToolCallIds(assistantMessage)) {
			return assistantMessage;
		}
		Set<String> seen = new HashSet<>();
		List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls()
			.stream()
			.map(toolCall -> (StringUtils.hasText(toolCall.id()) && seen.add(toolCall.id())) ? toolCall
					: new AssistantMessage.ToolCall("call_" + UUID.randomUUID(), toolCall.type(), toolCall.name(),
							toolCall.arguments()))
			.toList();
		return assistantMessage.mutate().toolCalls(toolCalls).build();
	}

	/** Whether every tool call has an id, and no two calls share one. */
	private static boolean hasDistinctToolCallIds(AssistantMessage assistantMessage) {
		List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
		return toolCalls.stream().allMatch(toolCall -> StringUtils.hasText(toolCall.id()))
				&& toolCalls.stream().map(AssistantMessage.ToolCall::id).distinct().count() == toolCalls.size();
	}

	/**
	 * The ids of the calls that need a person's confirmation before they run, in the
	 * order the model asked for them. The policy sees the metadata of the tool the call
	 * would run with, or {@code null} when the manager has none for it.
	 */
	private List<String> awaitingConfirmation(AssistantMessage assistantMessage, List<ToolCallback> toolCallbacks) {
		List<String> awaiting = new ArrayList<>();
		for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
			ToolCallback toolCallback = this.resolveToolCallback(toolCallbacks, toolCall.name());
			ToolMetadata toolMetadata = (toolCallback != null) ? toolCallback.getToolMetadata() : null;
			if (this.toolCallConfirmationPolicy.requiresConfirmation(toolCall, toolMetadata)) {
				awaiting.add(toolCall.id());
			}
		}
		return awaiting;
	}

	/**
	 * Every call of the reply needs a decision, so the policy is never re-evaluated when
	 * a paused batch resumes, and an allowed call runs only with a tool the prompt
	 * carries, never with one resolved by name.
	 */
	private static void checkDecisions(ToolCallDecisions decisions, AssistantMessage assistantMessage,
			List<ToolCallback> toolCallbacks) {
		Assert.isTrue(hasDistinctToolCallIds(assistantMessage),
				"Every tool call of the reply needs an id of its own to be decided on; resume with the reply the loop returned");
		Set<String> ids = new LinkedHashSet<>();
		assistantMessage.getToolCalls().forEach(toolCall -> ids.add(toolCall.id()));
		for (ToolCallDecision decision : decisions.decisions()) {
			Assert.isTrue(ids.contains(decision.toolCallId()),
					() -> "No tool call has the id '" + decision.toolCallId() + "'; the reply's calls: " + ids);
		}
		Set<String> missing = new LinkedHashSet<>(ids);
		decisions.decisions().forEach(decision -> missing.remove(decision.toolCallId()));
		Assert.isTrue(missing.isEmpty(), () -> "Every tool call of the reply needs a decision; missing: " + missing);
		for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
			ToolCallDecision decision = decisions.decisionOn(toolCall.id());
			if (decision != null && decision.allowed() && findToolCallback(toolCallbacks, toolCall.name()) == null) {
				throw new IllegalArgumentException(
						"The prompt does not carry the tool '" + toolCall.name() + "' of the allowed call '"
								+ toolCall.id() + "'; an allowed call runs only with a tool of the prompt");
			}
		}
	}

	/**
	 * The prompt's callback for the tool or, when the fallback is enabled, the
	 * resolver's.
	 */
	private @Nullable ToolCallback resolveToolCallback(List<ToolCallback> toolCallbacks, String toolName) {
		ToolCallback toolCallback = findToolCallback(toolCallbacks, toolName);
		if (toolCallback == null && this.resolutionFallbackEnabled) {
			return this.toolCallbackResolver.resolve(toolName);
		}
		return toolCallback;
	}

	private static @Nullable ToolCallback findToolCallback(List<ToolCallback> toolCallbacks, String toolName) {
		return toolCallbacks.stream()
			.filter(tool -> toolName.equals(tool.getToolDefinition().name()))
			.findFirst()
			.orElse(null);
	}

	private List<Message> buildConversationHistoryAfterToolExecution(List<Message> previousMessages,
			AssistantMessage assistantMessage, ToolResponseMessage toolResponseMessage) {
		List<Message> messages = new ArrayList<>(previousMessages);
		messages.add(assistantMessage);
		messages.add(toolResponseMessage);
		return messages;
	}

	public void setObservationConvention(ToolCallingObservationConvention observationConvention) {
		this.observationConvention = observationConvention;
	}

	public static Builder builder() {
		return new Builder();
	}

	private record InternalToolExecutionResult(ToolResponseMessage toolResponseMessage, boolean returnDirect) {
	}

	public final static class Builder {

		private ObservationRegistry observationRegistry = DEFAULT_OBSERVATION_REGISTRY;

		private ToolCallbackResolver toolCallbackResolver = DEFAULT_TOOL_CALLBACK_RESOLVER;

		private ToolExecutionExceptionProcessor toolExecutionExceptionProcessor = DEFAULT_TOOL_EXECUTION_EXCEPTION_PROCESSOR;

		private int defaultMaxCallsPerTool = DEFAULT_MAX_CALLS_PER_TOOL;

		private final Map<String, Integer> maxCallsPerTool = new HashMap<>();

		private final Set<String> toolsExcludedFromLimit = new HashSet<>();

		private int maxTotalToolCalls = DEFAULT_MAX_TOTAL_TOOL_CALLS;

		private ToolCallLimitBehavior onLimitExceeded = ToolCallLimitBehavior.THROW;

		private boolean resolutionFallbackEnabled = DEFAULT_RESOLUTION_FALLBACK_ENABLED;

		private ToolCallConfirmationPolicy toolCallConfirmationPolicy = ToolCallConfirmationPolicy.fromToolMetadata();

		private Builder() {
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public Builder toolCallbackResolver(ToolCallbackResolver toolCallbackResolver) {
			this.toolCallbackResolver = toolCallbackResolver;
			return this;
		}

		public Builder toolExecutionExceptionProcessor(
				ToolExecutionExceptionProcessor toolExecutionExceptionProcessor) {
			this.toolExecutionExceptionProcessor = toolExecutionExceptionProcessor;
			return this;
		}

		/**
		 * Default maximum number of times any single tool can be called within a turn,
		 * unless overridden for a specific tool name via
		 * {@link #maxCallsPerTool(String, int)} or exempted via
		 * {@link #excludeToolFromLimit(String)}. Defaults to
		 * {@link DefaultToolCallingManager#DEFAULT_MAX_CALLS_PER_TOOL}; call
		 * {@link #unlimitedCallsPerTool()} to disable this limit entirely.
		 */
		public Builder maxCallsPerTool(int maxCallsPerTool) {
			Assert.isTrue(maxCallsPerTool > 0, "maxCallsPerTool must be greater than 0");
			this.defaultMaxCallsPerTool = maxCallsPerTool;
			return this;
		}

		/**
		 * Disable the per-tool call limit entirely, undoing {@link #maxCallsPerTool(int)}
		 * (or the {@link DefaultToolCallingManager#DEFAULT_MAX_CALLS_PER_TOOL} default),
		 * so that, absent a specific {@link #maxCallsPerTool(String, int)} override, any
		 * tool can be called an unlimited number of times.
		 */
		public Builder unlimitedCallsPerTool() {
			this.defaultMaxCallsPerTool = ToolCallLimits.NO_LIMIT;
			return this;
		}

		/**
		 * Maximum number of times the named tool can be called within a turn, overriding
		 * the default set via {@link #maxCallsPerTool(int)} for that tool only.
		 */
		public Builder maxCallsPerTool(String toolName, int maxCallsPerTool) {
			Assert.hasText(toolName, "toolName cannot be null or empty");
			Assert.isTrue(maxCallsPerTool > 0, "maxCallsPerTool must be greater than 0");
			this.maxCallsPerTool.put(toolName, maxCallsPerTool);
			this.toolsExcludedFromLimit.remove(toolName);
			return this;
		}

		/**
		 * Exempt the named tool from the per-tool call limit, so it can be called an
		 * unlimited number of times regardless of {@link #maxCallsPerTool(int)} or a
		 * prior {@link #maxCallsPerTool(String, int)} override for that tool. Calls to
		 * this tool still count toward {@link #maxTotalToolCalls(int)}.
		 */
		public Builder excludeToolFromLimit(String toolName) {
			Assert.hasText(toolName, "toolName cannot be null or empty");
			this.toolsExcludedFromLimit.add(toolName);
			this.maxCallsPerTool.remove(toolName);
			return this;
		}

		/**
		 * Maximum number of tool calls, across all tools combined, allowed within a turn.
		 * Defaults to {@link DefaultToolCallingManager#DEFAULT_MAX_TOTAL_TOOL_CALLS};
		 * call {@link #unlimitedTotalToolCalls()} to disable this limit entirely.
		 */
		public Builder maxTotalToolCalls(int maxTotalToolCalls) {
			Assert.isTrue(maxTotalToolCalls > 0, "maxTotalToolCalls must be greater than 0");
			this.maxTotalToolCalls = maxTotalToolCalls;
			return this;
		}

		/**
		 * Disable the total tool call limit entirely, undoing
		 * {@link #maxTotalToolCalls(int)} (or the
		 * {@link DefaultToolCallingManager#DEFAULT_MAX_TOTAL_TOOL_CALLS} default).
		 */
		public Builder unlimitedTotalToolCalls() {
			this.maxTotalToolCalls = ToolCallLimits.NO_LIMIT;
			return this;
		}

		/**
		 * What to do when a configured limit is exceeded. Defaults to
		 * {@link ToolCallLimitBehavior#THROW}.
		 */
		public Builder onLimitExceeded(ToolCallLimitBehavior onLimitExceeded) {
			Assert.notNull(onLimitExceeded, "onLimitExceeded cannot be null");
			this.onLimitExceeded = onLimitExceeded;
			return this;
		}

		/**
		 * Whether a call to a tool that is not part of the request's tool callbacks may
		 * still be executed by resolving it through the {@link ToolCallbackResolver}.
		 * Defaults to {@code false}, so that the tools attached to a request bound what
		 * can be executed for that request.
		 * @param resolutionFallbackEnabled {@code true} to allow name-based resolution of
		 * tools absent from the request via the resolver
		 * @since 2.0.1
		 */
		public Builder resolutionFallbackEnabled(boolean resolutionFallbackEnabled) {
			this.resolutionFallbackEnabled = resolutionFallbackEnabled;
			return this;
		}

		/**
		 * Which tool calls need a person's confirmation before they run. The manager
		 * refuses to run such a call without a decision for it, by throwing
		 * {@link ToolCallConfirmationRequiredException} before any call of the batch
		 * runs; see {@link ToolCallDecisions}. Defaults to
		 * {@link ToolCallConfirmationPolicy#fromToolMetadata()}: the calls of tools
		 * marked {@code requiresConfirmation}.
		 * @param toolCallConfirmationPolicy the policy
		 * @since 2.1.0
		 */
		public Builder toolCallConfirmationPolicy(ToolCallConfirmationPolicy toolCallConfirmationPolicy) {
			Assert.notNull(toolCallConfirmationPolicy, "toolCallConfirmationPolicy cannot be null");
			this.toolCallConfirmationPolicy = toolCallConfirmationPolicy;
			return this;
		}

		public DefaultToolCallingManager build() {
			ToolCallLimits toolCallLimits = new ToolCallLimits(this.defaultMaxCallsPerTool, this.maxCallsPerTool,
					this.toolsExcludedFromLimit, this.maxTotalToolCalls, this.onLimitExceeded);
			return new DefaultToolCallingManager(this.observationRegistry, this.toolCallbackResolver,
					this.toolExecutionExceptionProcessor, toolCallLimits, this.resolutionFallbackEnabled,
					this.toolCallConfirmationPolicy);
		}

	}

}
