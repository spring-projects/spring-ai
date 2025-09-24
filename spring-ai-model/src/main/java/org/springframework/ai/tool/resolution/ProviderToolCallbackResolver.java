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

package org.springframework.ai.tool.resolution;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.function.SingletonSupplier;

/**
 * A {@link ToolCallbackResolver} that resolves tool callbacks from
 * {@link ToolCallbackProvider} lazily.
 *
 * @author Yanming Zhou
 */
public class ProviderToolCallbackResolver implements ToolCallbackResolver {

	private static final Logger logger = LoggerFactory.getLogger(ProviderToolCallbackResolver.class);

	private final SingletonSupplier<List<ToolCallback>> toolCallbackSupplier;

	public ProviderToolCallbackResolver(List<ToolCallbackProvider> toolCallbackProviders) {
		Assert.notNull(toolCallbackProviders, "toolCallbackProviders cannot be null");

		this.toolCallbackSupplier = SingletonSupplier.of(() -> toolCallbackProviders.stream()
			.flatMap(provider -> Stream.of(provider.getToolCallbacks()))
			.toList());
	}

	@Override
	@Nullable
	public ToolCallback resolve(String toolName) {
		Assert.hasText(toolName, "toolName cannot be null or empty");
		logger.debug("ToolCallback resolution attempt from tool callback provider");
		return this.toolCallbackSupplier.obtain()
			.stream()
			.filter(toolCallback -> toolName.equals(toolCallback.getToolDefinition().name()))
			.findAny()
			.orElse(null);
	}

}
