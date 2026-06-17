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

package org.springframework.ai.tool.context;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.Assert;

/**
 * A {@link ToolCallback} decorator that publishes the {@link ToolContext} received in
 * {@link #call(String, ToolContext)} to the current thread's {@link ToolContextHolder}
 * for the duration of the delegate invocation.
 *
 * <p>
 * This allows {@code @Tool} methods whose signatures do <strong>not</strong> declare a
 * {@code ToolContext} parameter &mdash; including tools defined in third-party frameworks
 * whose method signatures cannot be changed &mdash; to access the {@code ToolContext} by
 * reading it from {@link ToolContextHolder#get()}.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * ToolCallback original = MethodToolCallback.builder()...build();
 * ToolCallback wrapped = new ContextAwareToolCallback(original);
 *
 * // Inside the @Tool method, the signature does not declare ToolContext:
 * &#64;Tool
 * String myTool(String input) {
 *     ToolContext ctx = ToolContextHolder.get();
 *     String userId = ctx != null ? (String) ctx.getContext().get("userId") : null;
 *     // ...
 * }
 * }</pre>
 *
 * <p>
 * The {@link ToolContextHolder} is cleared automatically after the delegate returns or
 * throws, so callers do not need to manage the thread-local lifecycle themselves.
 * However, callers that {@link ToolContextHolder#set(ToolContext) set} the holder outside
 * of this decorator remain responsible for invoking {@link ToolContextHolder#clear()}.
 * </p>
 *
 * @author zz_zhi
 * @since 2.0.0
 */
public class ContextAwareToolCallback implements ToolCallback {

	private final ToolCallback delegate;

	public ContextAwareToolCallback(ToolCallback delegate) {
		Assert.notNull(delegate, "delegate must not be null");
		this.delegate = delegate;
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.delegate.getToolDefinition();
	}

	@Override
	public ToolMetadata getToolMetadata() {
		return this.delegate.getToolMetadata();
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, @Nullable ToolContext toolContext) {
		ToolContextHolder.set(toolContext);
		try {
			return this.delegate.call(toolInput, toolContext);
		}
		finally {
			ToolContextHolder.clear();
		}
	}

}
