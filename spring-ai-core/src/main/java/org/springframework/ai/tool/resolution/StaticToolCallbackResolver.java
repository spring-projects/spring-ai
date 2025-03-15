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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link ToolCallbackResolver} that resolves tool callbacks from a static registry.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class StaticToolCallbackResolver implements ToolCallbackResolver {

	private static final Logger logger = LoggerFactory.getLogger(StaticToolCallbackResolver.class);

	private final Map<String, FunctionCallback> toolCallbacks = new HashMap<>();

	public StaticToolCallbackResolver(List<FunctionCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");

		toolCallbacks.forEach(callback -> {
			if (callback instanceof ToolCallback toolCallback) {
				this.toolCallbacks.put(toolCallback.getToolDefinition().name(), toolCallback);
			}
			this.toolCallbacks.put(callback.getName(), callback);
		});
	}

	@Override
	public FunctionCallback resolve(String toolName) {
		Assert.hasText(toolName, "toolName cannot be null or empty");
		logger.debug("ToolCallback resolution attempt from static registry");
		return toolCallbacks.get(toolName);
	}

}
