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

package org.springframework.ai.chat.client.advisor.tool.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolIndex;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolIndexEvictionStrategy;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolReference;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolSearchRequest;
import org.springframework.ai.chat.client.advisor.tool.search.api.ToolSearchResponse;
import org.springframework.ai.chat.client.advisor.tool.search.eviction.CompositeEvictionStrategy;
import org.springframework.ai.chat.client.advisor.tool.search.eviction.LruEvictionStrategy;
import org.springframework.ai.chat.client.advisor.tool.search.eviction.TtlEvictionStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * CallAdvisor that integrates a tool search mechanism into the tool calling workflow. It
 * indexes available tools at the start of a conversation, exposes a "toolSearchTool" for
 * searching tools based on natural language queries, and manages the selection and
 * injection of discovered tools into the conversation flow.
 * <p>
 * Tool indexes are cached across turns of the same conversation: re-indexing only occurs
 * when the tool set changes (detected via a fingerprint of tool names and descriptions).
 * Stale session indexes are cleaned up according to the configured
 * {@link ToolIndexEvictionStrategy}.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public class ToolSearchToolCallingAdvisor extends ToolCallAdvisor {

	private static final String TOOL_SEARCH_TOOL_SESSION_ID_KEY = "toolSearchToolSessionId";

	private static final String CACHED_TOOL_CALLBACKS_KEY = ToolSearchToolCallingAdvisor.class.getName()
			+ ".cachedToolCallbacks";

	/**
	 * Internal Tool implementing the tool search functionality. It is registered,
	 * automatically, under the name "toolSearchTool".
	 */
	private final ToolCallback toolSearchToolCallback;

	/**
	 * The ToolIndex used to find tools based on search queries.
	 */
	private final ToolIndex toolIndex;

	/**
	 * The Tool Search system message suffix augment to be added to the prompt during
	 * initialization.
	 */
	private final String systemMessageSuffix;

	/**
	 * If enabled, accumulates all tool search tool responses to find tool references.
	 * <p>
	 * If disabled, only the last tool search tool response is used to find tool
	 * references.
	 */
	private final boolean referenceToolNameAccumulation;

	@Nullable private final Integer maxResults;

	private final String sessionIdKeyName;

	/**
	 * Tracks the fingerprint (sorted name+description hash) of the tool set last indexed
	 * for each session. A fingerprint match means the session's index is still valid and
	 * re-indexing can be skipped.
	 */
	private final ConcurrentHashMap<String, String> indexedSessionFingerprints = new ConcurrentHashMap<>();

	private final ToolIndexEvictionStrategy evictionStrategy;

	protected ToolSearchToolCallingAdvisor(ToolCallingManager toolCallingManager, int advisorOrder,
			boolean streamToolCallResponses, ToolIndex toolIndex, String systemMessageSuffix,
			boolean referenceToolNameAccumulation, @Nullable Integer maxResults, boolean conversationHistoryEnabled,
			String sessionIdKeyName, ToolIndexEvictionStrategy evictionStrategy) {

		super(toolCallingManager, advisorOrder, conversationHistoryEnabled, streamToolCallResponses);
		this.toolIndex = toolIndex;
		this.systemMessageSuffix = systemMessageSuffix;
		this.referenceToolNameAccumulation = referenceToolNameAccumulation;
		this.maxResults = maxResults;
		this.sessionIdKeyName = sessionIdKeyName;
		this.evictionStrategy = evictionStrategy;
		this.toolSearchToolCallback = MethodToolCallbackProvider.builder()
			.toolObjects(new ToolSearchTool())
			.build()
			.getToolCallbacks()[0];
	}

	@Override
	@SuppressWarnings("null")
	public String getName() {
		return "ToolSearchToolCallingAdvisor";
	}

	// -------------------------------------------------------------------------
	// Sync hooks
	// -------------------------------------------------------------------------

	@Override
	protected ChatClientRequest doInitializeLoop(ChatClientRequest chatClientRequest,
			CallAdvisorChain callAdvisorChain) {
		if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions) {
			return initializeSession(chatClientRequest);
		}
		return super.doInitializeLoop(chatClientRequest, callAdvisorChain);
	}

	@Override
	protected ChatClientRequest doBeforeCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
		if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions) {
			return prepareIteration(chatClientRequest);
		}
		return super.doBeforeCall(chatClientRequest, callAdvisorChain);
	}

	// -------------------------------------------------------------------------
	// Stream hooks
	// -------------------------------------------------------------------------

	@Override
	protected ChatClientRequest doInitializeLoopStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions) {
			return initializeSession(chatClientRequest);
		}
		return super.doInitializeLoopStream(chatClientRequest, streamAdvisorChain);
	}

	@Override
	protected ChatClientRequest doBeforeStream(ChatClientRequest chatClientRequest,
			StreamAdvisorChain streamAdvisorChain) {
		if (chatClientRequest.prompt().getOptions() instanceof ToolCallingChatOptions) {
			return prepareIteration(chatClientRequest);
		}
		return super.doBeforeStream(chatClientRequest, streamAdvisorChain);
	}

	// -------------------------------------------------------------------------
	// Shared logic
	// -------------------------------------------------------------------------

	/**
	 * Indexes tools for the session (skipping re-indexing when the tool set is
	 * unchanged), runs eviction, and augments the system message.
	 */
	@SuppressWarnings("null")
	private ChatClientRequest initializeSession(ChatClientRequest chatClientRequest) {
		ToolCallingChatOptions toolOptions = (ToolCallingChatOptions) chatClientRequest.prompt().getOptions();

		String sessionId = this.getSessionId(chatClientRequest.context());

		// Evict stale sessions before touching the current one.
		this.evictionStrategy.onAccess(sessionId).forEach(this::doEvict);

		// validation of tool options happens in the tool calling advisor, so we can
		// assume that if tool options are present, they are valid and contain either tool
		// callbacks or tool names to search for.
		List<ToolReference> toolReferences = this.toolCallingManager
			.resolveToolDefinitions(Objects.requireNonNull(toolOptions))
			.stream()
			.map(toolDef -> ToolReference.builder().toolName(toolDef.name()).summary(toolDef.description()).build())
			.toList();

		// Re-index only when the tool set has changed for this session.
		// compute() serializes concurrent requests for the same session so that only one
		// thread performs clear+reindex when the fingerprint changes.
		String fingerprint = computeFingerprint(toolReferences);
		this.indexedSessionFingerprints.compute(sessionId, (id, current) -> {
			if (!fingerprint.equals(current)) {
				this.toolIndex.clearIndex(id);
				this.toolIndex.indexTools(id, toolReferences);
			}
			return fingerprint;
		});

		ConcurrentHashMap<String, ToolCallback> cachedResolvedToolCallbacks = new ConcurrentHashMap<>();
		if (!CollectionUtils.isEmpty(toolOptions.getToolCallbacks())) {
			toolOptions.getToolCallbacks()
				.forEach(tc -> cachedResolvedToolCallbacks.putIfAbsent(tc.getToolDefinition().name(), tc));
		}

		chatClientRequest.context().put(CACHED_TOOL_CALLBACKS_KEY, cachedResolvedToolCallbacks);
		chatClientRequest.context().put(TOOL_SEARCH_TOOL_SESSION_ID_KEY, sessionId);

		return chatClientRequest.mutate()
			.prompt(chatClientRequest.prompt()
				.copy()
				.augmentSystemMessage(systemMessage -> systemMessage.copy()
					.mutate()
					.text(systemMessage.getText() + this.systemMessageSuffix)
					.build()))
			.build();
	}

	// Selects tools discovered via previous toolSearchTool calls and injects them into
	// options.
	@SuppressWarnings({ "null", "unchecked" })
	private ChatClientRequest prepareIteration(ChatClientRequest chatClientRequest) {
		ToolCallingChatOptions toolOptions = Objects
			.requireNonNull((ToolCallingChatOptions) chatClientRequest.prompt().getOptions());

		Set<ToolCallback> selectedToolCallbacks = new HashSet<>(List.of(this.toolSearchToolCallback));
		Set<String> selectedToolNames = new HashSet<>();

		var cachedToolCallbacks = (Map<String, ToolCallback>) chatClientRequest.context()
			.get(CACHED_TOOL_CALLBACKS_KEY);

		if (cachedToolCallbacks != null) {
			this.extractToolNameReferences(chatClientRequest.prompt().getInstructions()).forEach(toolName -> {
				if (cachedToolCallbacks.containsKey(toolName)) {
					selectedToolCallbacks.add(cachedToolCallbacks.get(toolName));
				}
				else {
					selectedToolNames.add(toolName);
				}
			});
		}

		ToolCallingChatOptions toolOptionsCopy = ((ToolCallingChatOptions.Builder<?>) toolOptions.mutate())
			.toolCallbacks(new ArrayList<>(selectedToolCallbacks))
			.toolNames(selectedToolNames)
			.toolContext(TOOL_SEARCH_TOOL_SESSION_ID_KEY,
					Objects.requireNonNull(chatClientRequest.context().get(TOOL_SEARCH_TOOL_SESSION_ID_KEY)))
			.build();

		return chatClientRequest.mutate()
			.prompt(chatClientRequest.prompt().mutate().chatOptions(toolOptionsCopy).build())
			.build();
	}

	/**
	 * Explicitly evicts a session's tool index and removes it from the advisor's cache.
	 * <p>
	 * Call this when a conversation is known to be over (e.g., on logout or session
	 * expiry) to free resources held by the underlying {@link ToolIndex}.
	 * @param sessionId the session to evict
	 */
	public void evictSession(String sessionId) {
		doEvict(sessionId);
	}

	private void doEvict(String sessionId) {
		this.toolIndex.clearIndex(sessionId);
		this.indexedSessionFingerprints.remove(sessionId);
		this.evictionStrategy.onRemoved(sessionId);
	}

	private List<String> extractToolNameReferences(List<Message> messages) {

		List<ToolResponse> toolSearchToolResponses = messages.stream()
			.filter(m -> m.getMessageType() == MessageType.TOOL)
			.map(r -> ((ToolResponseMessage) r).getResponses())
			.flatMap(List::stream)
			.filter(r -> r.name().equalsIgnoreCase(this.toolSearchToolCallback.getToolDefinition().name()))
			.toList();

		if (CollectionUtils.isEmpty(toolSearchToolResponses)) {
			return List.of();
		}

		toolSearchToolResponses = (this.referenceToolNameAccumulation) ? toolSearchToolResponses
				: List.of(toolSearchToolResponses.get(toolSearchToolResponses.size() - 1));

		return toolSearchToolResponses.stream()
			.map(r -> JsonParser.fromJson(r.responseData(), new TypeReference<List<String>>() {
			}))
			.flatMap(List::stream)
			.toList();
	}

	private String getSessionId(Map<String, @Nullable Object> context) {
		Assert.notNull(context, "context cannot be null");
		Assert.noNullElements(context.keySet().toArray(), "context cannot contain null keys");
		Assert.notNull(context.get(this.sessionIdKeyName),
				"context must contain a non-null value for '" + this.sessionIdKeyName + "'");

		return context.get(this.sessionIdKeyName).toString();
	}

	/**
	 * Computes a stable SHA-256 fingerprint for the given tool set. Tools are sorted by
	 * name so that registration order does not affect equality. Hashing avoids false
	 * cache hits that string-concatenation with delimiters can produce when names or
	 * summaries contain those delimiter characters.
	 */
	private static String computeFingerprint(List<ToolReference> toolReferences) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			toolReferences.stream().sorted(Comparator.comparing(ToolReference::toolName)).forEachOrdered(tr -> {
				digest.update(tr.toolName().getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 0); // field separator
				digest.update(tr.summary().getBytes(StandardCharsets.UTF_8));
				digest.update((byte) 1); // entry separator
			});
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

	// -------------------------------------------------------------------------
	// Builder
	// -------------------------------------------------------------------------

	/**
	 * Creates a new Builder instance for constructing a ToolSearchToolCallingAdvisor.
	 * @return a new Builder instance
	 */
	public static Builder<?> builder() {
		return new Builder<>();
	}

	private class ToolSearchTool {

		// @formatter:off
		@Tool(name = "toolSearchTool", description = """
				Search for tools in the tool registry to discover capabilities for completing the current task.
				Use this when you need functionality not provided by your currently available tools.
				The search queries against tool names, descriptions, and parameter information to find the most relevant tools.
				Returns references to matching tools which will be expanded into full definitions you can then invoke.
				""")
		public List<String> toolSearchTool(
			@ToolParam(description = "A natural language search query describing the tool capability you need. Be specific and include relevant keywords.") String query,
			@ToolParam(description = "Maximum number of tool references to return (1-10). Default is 5.", required = false) Integer maxResults,
			@ToolParam(description = "Optional filter to narrow search to a specific tool category.", required = false) String categoryFilter,
			ToolContext toolContext) { // @formatter:on

			String sessionId = Objects.requireNonNull(toolContext.getContext().get(TOOL_SEARCH_TOOL_SESSION_ID_KEY))
				.toString();

			// Advisor-configured maxResults is the fallback when the LLM does not provide
			// one.
			maxResults = (maxResults != null) ? maxResults : ToolSearchToolCallingAdvisor.this.maxResults;

			ToolSearchResponse toolSearchResponse = ToolSearchToolCallingAdvisor.this.toolIndex
				.search(new ToolSearchRequest(sessionId, query, maxResults, categoryFilter));

			return toolSearchResponse.toolReferences().stream().map(tr -> tr.toolName()).toList();
		}

	}

	/**
	 * Builder for creating instances of ToolSearchToolCallingAdvisor.
	 * <p>
	 * This builder extends {@link ToolCallAdvisor.Builder} and adds configuration options
	 * specific to tool search functionality.
	 *
	 * @param <T> the builder type, used for self-referential generics to support method
	 * chaining in subclasses
	 */
	public static class Builder<T extends Builder<T>> extends ToolCallAdvisor.Builder<T> {

		@Nullable private ToolIndex toolIndex;

		@Nullable private String systemMessageSuffix;

		private boolean referenceToolNameAccumulation = true;

		@Nullable private Integer maxResults;

		private String sessionIdKeyName = ChatMemory.CONVERSATION_ID;

		private ToolIndexEvictionStrategy evictionStrategy = new LruEvictionStrategy(1000);

		protected Builder() {
		}

		public T referenceToolNameAccumulation(boolean referenceToolNameAccumulation) {
			this.referenceToolNameAccumulation = referenceToolNameAccumulation;
			return self();
		}

		public T systemMessageSuffix(String systemMessageSuffix) {
			Assert.hasText(systemMessageSuffix, "systemMessageSuffix cannot be null or empty");
			this.systemMessageSuffix = systemMessageSuffix;
			return self();
		}

		/**
		 * Sets the ToolIndex to be used for finding tools.
		 * @param toolIndex the ToolIndex instance
		 * @return this Builder instance for method chaining
		 */
		public T toolIndex(ToolIndex toolIndex) {
			Assert.notNull(toolIndex, "toolIndex cannot be null");
			this.toolIndex = toolIndex;
			return self();
		}

		/**
		 * Sets the maximum number of tool references to return in tool search results.
		 * This is the human/user defined default value used when invoking the tool search
		 * tool.
		 * @param maxResults
		 * @return
		 */
		public T maxResults(Integer maxResults) {
			this.maxResults = maxResults;
			return self();
		}

		/**
		 * Sets the key name in the context where the conversation ID is stored. By
		 * default, it is "conversationId", but it can be customized if the conversation
		 * ID is stored under a different key in the context.
		 * @param sessionIdKeyName
		 * @return
		 */
		public T sessionIdKeyName(String sessionIdKeyName) {
			Assert.hasText(sessionIdKeyName, "sessionIdKeyName cannot be null or empty");
			this.sessionIdKeyName = sessionIdKeyName;
			return self();
		}

		/**
		 * Sets the eviction strategy that determines when session tool indexes are
		 * cleared from the advisor's cache.
		 * <p>
		 * Defaults to {@code new LruEvictionStrategy(1000)}, which retains indexes for at
		 * most 1 000 concurrently active sessions and silently evicts the
		 * least-recently-used one when the cap is exceeded. Adjust the cap to match the
		 * expected peak concurrency of your deployment — lower values reduce memory
		 * pressure, higher values reduce unnecessary re-indexing for services with many
		 * parallel conversations.
		 * <p>
		 * Combine with {@link TtlEvictionStrategy} via {@link CompositeEvictionStrategy}
		 * to also release indexes for sessions that have been idle longer than a fixed
		 * duration.
		 * @param evictionStrategy the eviction strategy to use; must not be {@code null}
		 * @return this Builder instance for method chaining
		 * @see LruEvictionStrategy
		 * @see TtlEvictionStrategy
		 * @see CompositeEvictionStrategy
		 */
		public T evictionStrategy(ToolIndexEvictionStrategy evictionStrategy) {
			Assert.notNull(evictionStrategy, "evictionStrategy must not be null");
			this.evictionStrategy = evictionStrategy;
			return self();
		}

		/**
		 * Builds and returns a new ToolSearchToolCallingAdvisor instance with the
		 * configured properties.
		 * @return a new ToolSearchToolCallingAdvisor instance
		 * @throws IllegalArgumentException if required parameters are null or invalid
		 */
		@Override
		public ToolSearchToolCallingAdvisor build() {

			if (!StringUtils.hasText(this.systemMessageSuffix)) {
				try {
					this.systemMessageSuffix = new DefaultResourceLoader()
						.getResource("classpath:/DEFAULT_SYSTEM_PROMPT_SUFFIX.md")
						.getContentAsString(StandardCharsets.UTF_8);
				}
				catch (Exception ex) {
					throw new IllegalArgumentException(
							"Failed to load default system message suffix from classpath resource", ex);
				}
			}

			Assert.notNull(this.toolIndex, "toolIndex is required");
			return new ToolSearchToolCallingAdvisor(getToolCallingManager(), getAdvisorOrder(),
					isStreamToolCallResponses(), this.toolIndex, Objects.requireNonNull(this.systemMessageSuffix),
					this.referenceToolNameAccumulation, this.maxResults, this.isConversationHistoryEnabled(),
					this.sessionIdKeyName, this.evictionStrategy);
		}

	}

}
