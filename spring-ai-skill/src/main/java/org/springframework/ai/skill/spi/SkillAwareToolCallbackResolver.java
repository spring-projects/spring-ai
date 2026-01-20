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

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.skill.core.SkillKit;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;

/**
 * Skill-aware ToolCallbackResolver - dynamically resolves tools from SkillKit.
 *
 * <p>
 * This resolver works with {@code SkillAwareToolCallingManager} to enable dynamic tool
 * execution. When the LLM calls a tool, this resolver finds the tool callback from the
 * SkillKit's active tools.
 *
 * @see ToolCallbackResolver
 * @see SkillKit
 */
public class SkillAwareToolCallbackResolver implements ToolCallbackResolver {

	private static final Logger logger = LoggerFactory.getLogger(SkillAwareToolCallbackResolver.class);

	private final SkillKit skillKit;

	private SkillAwareToolCallbackResolver(SkillKit skillKit) {
		this.skillKit = skillKit;
	}

	/**
	 * Creates a new builder instance.
	 * @return new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable ToolCallback resolve(String toolName) {
		if (this.skillKit == null) {
			logger.warn("No SkillKit configured");
			return null;
		}

		List<ToolCallback> activeSkillCallbacks = this.skillKit.getAllActiveTools();
		for (ToolCallback callback : activeSkillCallbacks) {
			if (callback != null && callback.getToolDefinition() != null
					&& toolName.equals(callback.getToolDefinition().name())) {
				return callback;
			}
		}

		List<ToolCallback> defaultSkillTools = this.skillKit.getSkillLoaderTools();
		for (ToolCallback callback : defaultSkillTools) {
			if (callback != null && callback.getToolDefinition() != null
					&& toolName.equals(callback.getToolDefinition().name())) {
				return callback;
			}
		}

		logger.warn("Tool '{}' not found", toolName);
		return null;
	}

	/**
	 * Builder for SkillAwareToolCallbackResolver.
	 */
	public static class Builder {

		private @Nullable SkillKit skillKit;

		private Builder() {
		}

		/**
		 * Sets the skill kit.
		 * @param skillKit skill kit instance
		 * @return this builder
		 */
		public Builder skillKit(SkillKit skillKit) {
			this.skillKit = skillKit;
			return this;
		}

		/**
		 * Builds the SkillAwareToolCallbackResolver instance.
		 * @return new SkillAwareToolCallbackResolver instance
		 */
		public SkillAwareToolCallbackResolver build() {
			if (this.skillKit == null) {
				throw new IllegalArgumentException("skillKit cannot be null");
			}
			return new SkillAwareToolCallbackResolver(this.skillKit);
		}

	}

}
