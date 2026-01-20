/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.skill.spi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.skill.core.SkillKit;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;

/**
 * Skill-aware ToolCallingManager that automatically merges skill tools with base tools.
 *
 * <p>
 * <b>Why We Need This:</b>
 *
 * <p>
 * We implement {@code ToolCallingManager} interface and delegate to the underlying
 * {@code ToolCallingManager} implementation while adding skill tool merging logic.
 *
 * <p>
 * <b>How It Works:</b>
 *
 * <p>
 * This class works together with {@code SkillAwareToolCallbackResolver}:
 *
 * <pre>
 * 1. resolveToolDefinitions() - Provides tool definitions to LLM:
 *    - Delegate to underlying ToolCallingManager to resolve base tool definitions
 *    - Get default skill tools from skillKit.getSkillTools()
 *    - Get active skill tools from skillKit.getAllActiveTools()
 *    - Merge with deduplication by tool name (priority: base > default > active)
 *    - Return merged ToolDefinition list
 *
 * 2. executeToolCalls() - Executes tool calls from LLM:
 *    - Delegates to the underlying ToolCallingManager
 *    - The delegate uses SkillAwareToolCallbackResolver
 *    - SkillAwareToolCallbackResolver finds tools in skillKit.getAllActiveTools()
 * </pre>
 *
 * @see ToolCallingManager
 * @see SkillKit
 * @since 1.1.3
 */
public class SkillAwareToolCallingManager implements ToolCallingManager {

	private static final Logger logger = LoggerFactory.getLogger(SkillAwareToolCallingManager.class);

	private final SkillKit skillKit;

	private final ToolCallingManager delegate;

	/**
	 * Create a SkillAwareToolCallingManager with custom delegate.
	 * @param skillKit The SkillKit containing skill management and tools
	 * @param delegate The underlying ToolCallingManager to delegate to
	 */
	public SkillAwareToolCallingManager(SkillKit skillKit, ToolCallingManager delegate) {
		this.skillKit = skillKit;
		this.delegate = delegate;
	}

	/**
	 * Builder for SkillAwareToolCallingManager.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Resolve tool definitions by merging base tools with skill tools.
	 *
	 * <p>
	 * This is the KEY method where we inject skill tools dynamically!
	 * @param chatOptions The chat options containing base tools
	 * @return Merged list of tool definitions (base + skill tools)
	 */
	@Override
	public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
		// Delegate to the underlying ToolCallingManager to resolve base definitions
		List<ToolDefinition> baseDefinitions = this.delegate.resolveToolDefinitions(chatOptions);

		if (this.skillKit == null) {
			logger.warn("No SkillKit configured, returning base tools only");
			return baseDefinitions != null ? baseDefinitions : List.of();
		}

		List<ToolCallback> defaultSkillToolCallbacks = this.skillKit.getSkillLoaderTools();
		List<ToolDefinition> defaultSkillToolDefinitions = new ArrayList<>();
		for (ToolCallback callback : defaultSkillToolCallbacks) {
			if (callback != null && callback.getToolDefinition() != null) {
				defaultSkillToolDefinitions.add(callback.getToolDefinition());
			}
		}

		List<ToolCallback> activeSkillCallbacks = this.skillKit.getAllActiveTools();

		List<ToolDefinition> skillDefinitions = new ArrayList<>();
		for (ToolCallback callback : activeSkillCallbacks) {
			if (callback != null && callback.getToolDefinition() != null) {
				skillDefinitions.add(callback.getToolDefinition());
			}
		}

		return this.mergeToolDefinitions(baseDefinitions, defaultSkillToolDefinitions, skillDefinitions);
	}

	/**
	 * Merge tool definitions with deduplication by tool name.
	 *
	 * <p>
	 * <b>Priority order</b>: base > default > skills
	 * <ul>
	 * <li>Base tools take the highest precedence (from chatOptions)</li>
	 * <li>Default SkillTools added next (for progressive loading)</li>
	 * <li>Skill tools added last (from activated skills)</li>
	 * </ul>
	 * @param baseDefinitions Base tool definitions from chatOptions
	 * @param defaultSkillToolDefinitions Default SkillTools for progressive loading
	 * @param skillDefinitions Skill tool definitions from activated skills
	 * @return Merged list with no duplicates
	 */
	private List<ToolDefinition> mergeToolDefinitions(List<ToolDefinition> baseDefinitions,
			List<ToolDefinition> defaultSkillToolDefinitions, List<ToolDefinition> skillDefinitions) {

		List<ToolDefinition> merged = new ArrayList<>();
		Set<String> toolNames = new HashSet<>();

		for (ToolDefinition def : baseDefinitions) {
			if (def != null && def.name() != null) {
				merged.add(def);
				toolNames.add(def.name());
			}
		}

		for (ToolDefinition def : defaultSkillToolDefinitions) {
			if (def != null && def.name() != null) {
				if (!toolNames.contains(def.name())) {
					merged.add(def);
					toolNames.add(def.name());
				}
			}
		}

		for (ToolDefinition def : skillDefinitions) {
			if (def != null && def.name() != null) {
				if (!toolNames.contains(def.name())) {
					merged.add(def);
					toolNames.add(def.name());
				}
			}
		}

		return merged;
	}

	/**
	 * Execute tool calls - delegate to the underlying ToolCallingManager.
	 *
	 * <p>
	 * Tool execution logic is delegated to the underlying manager. The delegate uses
	 * {@code
	 * SkillAwareToolCallbackResolver} to find tool callbacks from both chatOptions and
	 * skillBox.getAllActiveTools().
	 * @param prompt The prompt
	 * @param chatResponse The chat response containing tool calls
	 * @return Tool execution result
	 */
	@Override
	public ToolExecutionResult executeToolCalls(Prompt prompt, ChatResponse chatResponse) {
		return this.delegate.executeToolCalls(prompt, chatResponse);
	}

	public static class Builder {

		private @Nullable SkillKit skillKit;

		private @Nullable ToolCallingManager delegate;

		public Builder skillKit(SkillKit skillKit) {
			this.skillKit = skillKit;
			return this;
		}

		public Builder delegate(ToolCallingManager delegate) {
			this.delegate = delegate;
			return this;
		}

		public SkillAwareToolCallingManager build() {
			if (this.skillKit == null) {
				throw new IllegalArgumentException("skillKit cannot be null");
			}
			ToolCallingManager delegateManager = this.delegate;
			if (delegateManager == null) {
				// Create delegate with SkillAwareToolCallbackResolver
				delegateManager = DefaultToolCallingManager.builder()
					.toolCallbackResolver(new DelegatingToolCallbackResolver(
							List.of(SkillAwareToolCallbackResolver.builder().skillKit(this.skillKit).build())))
					.build();
			}
			return new SkillAwareToolCallingManager(this.skillKit, delegateManager);
		}

	}

}
