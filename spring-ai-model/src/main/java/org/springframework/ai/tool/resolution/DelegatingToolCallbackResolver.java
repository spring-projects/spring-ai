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

package org.springframework.ai.tool.resolution;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * A {@link ToolCallbackResolver} that delegates to a list of {@link ToolCallbackResolver}
 * instances.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DelegatingToolCallbackResolver implements ToolCallbackResolver {

	private final List<ToolCallbackResolver> toolCallbackResolvers;

	public DelegatingToolCallbackResolver(List<ToolCallbackResolver> toolCallbackResolvers) {
		Assert.notNull(toolCallbackResolvers, "toolCallbackResolvers cannot be null");
		Assert.noNullElements(toolCallbackResolvers, "toolCallbackResolvers cannot contain null elements");
		this.toolCallbackResolvers = toolCallbackResolvers;
	}

	@Override
	public @Nullable ToolCallback resolve(String toolName) {
		Assert.hasText(toolName, "toolName cannot be null or empty");

		for (ToolCallbackResolver toolCallbackResolver : this.toolCallbackResolvers) {
			ToolCallback toolCallback = toolCallbackResolver.resolve(toolName);
			if (toolCallback != null) {
				return toolCallback;
			}
		}
		return null;
	}

}
