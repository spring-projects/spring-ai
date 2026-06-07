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

package org.springframework.ai.chat.client.advisor.toolsearch.autoconfigure;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for {@link ToolSearchAdvisorAutoConfiguration}.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
@ConfigurationProperties(ToolSearchAdvisorProperties.CONFIG_PREFIX)
public class ToolSearchAdvisorProperties {

	public static final String CONFIG_PREFIX = "spring.ai.chat.client.tool-search-advisor";

	/**
	 * Enable the {@code ToolSearchToolCallingAdvisor} as a replacement for the default
	 * {@code ToolCallingAdvisor}. When {@code true}, the advisor is registered in the
	 * {@code ChatClient} builder and the default advisor is suppressed via
	 * {@code @ConditionalOnMissingBean}.
	 */
	private boolean enabled = false;

	/**
	 * {@link ToolIndex} implementation to use for tool search.
	 * <p>
	 * Supported values:
	 * <ul>
	 * <li>{@code regex} — lightweight regex/keyword pattern matching; no additional
	 * dependencies needed (default when this property is not set).</li>
	 * <li>{@code lucene} — Apache Lucene keyword search; requires
	 * {@code org.apache.lucene:lucene-core} on the classpath.</li>
	 * <li>{@code vector} — embedding-based semantic search; requires a
	 * {@code VectorStore} bean and {@code spring-ai-vector-store} on the classpath.</li>
	 * </ul>
	 * When not set, {@code regex} is used as the default.
	 */
	@Nullable private String toolIndexType;

	/**
	 * Maximum number of tool references returned per tool-search call. When {@code null},
	 * the {@code ToolSearchTool} uses its own built-in default.
	 */
	@Nullable private Integer maxResults;

	/**
	 * Key name in the advisor context map that carries the conversation/session ID. Must
	 * match the key used by whatever provides it (e.g. a memory advisor). Defaults to
	 * {@link ChatMemory#CONVERSATION_ID}.
	 */
	private String sessionIdKeyName = ChatMemory.CONVERSATION_ID;

	/**
	 * When {@code true} (default), tool references discovered across all tool-search tool
	 * responses in a turn are accumulated. When {@code false}, only the last response is
	 * used to determine which tools to inject.
	 */
	private boolean referenceToolNameAccumulation = true;

	/**
	 * Custom system-message suffix injected at the start of each conversation to instruct
	 * the model on how to use the tool-search tool. When {@code null} (default), the
	 * built-in {@code classpath:/DEFAULT_SYSTEM_PROMPT_SUFFIX.md} resource is loaded.
	 */
	@Nullable private String systemMessageSuffix;

	/**
	 * Order of the advisor in the advisor chain. Controls which advisors participate in
	 * each tool-call iteration. Mirrors the semantics of
	 * {@code spring.ai.chat.client.tool-calling.advisor-order}.
	 */
	private int advisorOrder = ToolCallingAdvisor.DEFAULT_ORDER;

	/**
	 * Whether intermediate tool-call responses are streamed back to the caller during a
	 * {@code stream()} invocation. When {@code false} (default), only the final answer is
	 * streamed.
	 */
	private boolean streamToolCallResponses = false;

	private final Eviction eviction = new Eviction();

	private final Lucene lucene = new Lucene();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public @Nullable String getToolIndexType() {
		return this.toolIndexType;
	}

	public void setToolIndexType(@Nullable String toolIndexType) {
		this.toolIndexType = toolIndexType;
	}

	public @Nullable Integer getMaxResults() {
		return this.maxResults;
	}

	public void setMaxResults(@Nullable Integer maxResults) {
		this.maxResults = maxResults;
	}

	public String getSessionIdKeyName() {
		return this.sessionIdKeyName;
	}

	public void setSessionIdKeyName(String sessionIdKeyName) {
		this.sessionIdKeyName = sessionIdKeyName;
	}

	public boolean isReferenceToolNameAccumulation() {
		return this.referenceToolNameAccumulation;
	}

	public void setReferenceToolNameAccumulation(boolean referenceToolNameAccumulation) {
		this.referenceToolNameAccumulation = referenceToolNameAccumulation;
	}

	public @Nullable String getSystemMessageSuffix() {
		return this.systemMessageSuffix;
	}

	public void setSystemMessageSuffix(@Nullable String systemMessageSuffix) {
		this.systemMessageSuffix = systemMessageSuffix;
	}

	public int getAdvisorOrder() {
		return this.advisorOrder;
	}

	public void setAdvisorOrder(int advisorOrder) {
		this.advisorOrder = advisorOrder;
	}

	public boolean isStreamToolCallResponses() {
		return this.streamToolCallResponses;
	}

	public void setStreamToolCallResponses(boolean streamToolCallResponses) {
		this.streamToolCallResponses = streamToolCallResponses;
	}

	public Eviction getEviction() {
		return this.eviction;
	}

	public Lucene getLucene() {
		return this.lucene;
	}

	/**
	 * Eviction strategy settings for the per-session tool-index cache.
	 */
	public static class Eviction {

		/**
		 * Maximum number of concurrently active sessions to keep in the index cache. The
		 * least-recently-used session is evicted when the cap is exceeded.
		 */
		private int lruMaxSessions = 1000;

		/**
		 * Time-to-live for idle sessions. When set, a composite LRU+TTL strategy is used
		 * so that sessions idle longer than this duration are also evicted. When
		 * {@code null} (default), only LRU eviction applies.
		 */
		@Nullable private Duration ttl;

		public int getLruMaxSessions() {
			return this.lruMaxSessions;
		}

		public void setLruMaxSessions(int lruMaxSessions) {
			this.lruMaxSessions = lruMaxSessions;
		}

		public @Nullable Duration getTtl() {
			return this.ttl;
		}

		public void setTtl(@Nullable Duration ttl) {
			this.ttl = ttl;
		}

	}

	/**
	 * Settings specific to the Lucene-backed {@code ToolIndex}. Applies only when a
	 * {@link org.springframework.ai.tool.toolsearch.index.lucene.LuceneToolIndex} bean is
	 * auto-configured.
	 */
	public static class Lucene {

		/**
		 * Minimum Lucene score for a tool-search hit to be included in results. Hits
		 * below this threshold are silently dropped.
		 */
		private float minScoreThreshold = 0.25f;

		public float getMinScoreThreshold() {
			return this.minScoreThreshold;
		}

		public void setMinScoreThreshold(float minScoreThreshold) {
			this.minScoreThreshold = minScoreThreshold;
		}

	}

}
