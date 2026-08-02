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

package org.springframework.ai.model.tool.autoconfigure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallLimitBehavior;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for tool calling.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
@ConfigurationProperties(ToolCallingProperties.CONFIG_PREFIX)
public class ToolCallingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.tools";

	private final Observations observations = new Observations();

	public Observations getObservations() {
		return this.observations;
	}

	private final Limits limits = new Limits();

	public Limits getLimits() {
		return this.limits;
	}

	/**
	 * If true, tool calling errors are thrown as exceptions for the caller to handle. If
	 * false, errors are converted to messages and sent back to the AI model, allowing it
	 * to process and respond to the error.
	 */
	private boolean throwExceptionOnError = false;

	public boolean isThrowExceptionOnError() {
		return this.throwExceptionOnError;
	}

	public void setThrowExceptionOnError(boolean throwExceptionOnError) {
		this.throwExceptionOnError = throwExceptionOnError;
	}

	public static class Observations {

		/**
		 * Whether to include the tool call content in the observations.
		 */
		private boolean includeContent = false;

		public boolean isIncludeContent() {
			return this.includeContent;
		}

		public void setIncludeContent(boolean includeContent) {
			this.includeContent = includeContent;
		}

	}

	public static class Limits {

		/**
		 * Default maximum number of times any single tool can be called within a turn,
		 * unless overridden for a specific tool name via {@link #maxCallsPerTool} or
		 * exempted via {@link #excludedTools}. Defaults to
		 * {@link DefaultToolCallingManager#DEFAULT_MAX_CALLS_PER_TOOL}. Set to {@code -1}
		 * to disable this limit entirely.
		 */
		private @Nullable Integer maxCallsPerToolDefault = DefaultToolCallingManager.DEFAULT_MAX_CALLS_PER_TOOL;

		/**
		 * Per-tool overrides for the maximum number of times a specific tool can be
		 * called within a turn, keyed by tool name. Set an entry's value to {@code -1} to
		 * exempt that tool from the per-tool call limit entirely.
		 */
		private Map<String, Integer> maxCallsPerTool = new HashMap<>();

		/**
		 * Names of tools exempt from the per-tool call limit. Calls to these tools still
		 * count toward {@link #maxTotalToolCalls}.
		 */
		private List<String> excludedTools = new ArrayList<>();

		/**
		 * Maximum number of tool calls, across all tools combined, allowed within a turn.
		 * Defaults to {@link DefaultToolCallingManager#DEFAULT_MAX_TOTAL_TOOL_CALLS}. Set
		 * to {@code -1} to disable this limit entirely.
		 */
		private @Nullable Integer maxTotalToolCalls = DefaultToolCallingManager.DEFAULT_MAX_TOTAL_TOOL_CALLS;

		/**
		 * What to do when a configured tool call limit is exceeded.
		 */
		private ToolCallLimitBehavior onLimitExceeded = ToolCallLimitBehavior.THROW;

		public @Nullable Integer getMaxCallsPerToolDefault() {
			return this.maxCallsPerToolDefault;
		}

		public void setMaxCallsPerToolDefault(@Nullable Integer maxCallsPerToolDefault) {
			this.maxCallsPerToolDefault = maxCallsPerToolDefault;
		}

		public Map<String, Integer> getMaxCallsPerTool() {
			return this.maxCallsPerTool;
		}

		public void setMaxCallsPerTool(Map<String, Integer> maxCallsPerTool) {
			this.maxCallsPerTool = maxCallsPerTool;
		}

		public List<String> getExcludedTools() {
			return this.excludedTools;
		}

		public void setExcludedTools(List<String> excludedTools) {
			this.excludedTools = excludedTools;
		}

		public @Nullable Integer getMaxTotalToolCalls() {
			return this.maxTotalToolCalls;
		}

		public void setMaxTotalToolCalls(@Nullable Integer maxTotalToolCalls) {
			this.maxTotalToolCalls = maxTotalToolCalls;
		}

		public ToolCallLimitBehavior getOnLimitExceeded() {
			return this.onLimitExceeded;
		}

		public void setOnLimitExceeded(ToolCallLimitBehavior onLimitExceeded) {
			this.onLimitExceeded = onLimitExceeded;
		}

	}

}
