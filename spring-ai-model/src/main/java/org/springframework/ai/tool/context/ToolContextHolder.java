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

/**
 * Thread-local holder for the {@link ToolContext} that flows through a tool call
 * invocation. Used in combination with {@link ContextAwareToolCallback} to expose the
 * current {@code ToolContext} to {@code @Tool} methods whose signatures do not declare a
 * {@code ToolContext} parameter, including tools defined in third-party frameworks whose
 * method signatures cannot be changed.
 *
 * <p>
 * Typical usage inside a {@code @Tool} method:
 * </p>
 *
 * <pre>{@code
 * &#64;Tool
 * String myTool(String input) {
 *     ToolContext ctx = ToolContextHolder.get();
 *     String userId = ctx != null ? (String) ctx.getContext().get("userId") : null;
 *     // ...
 * }
 * }</pre>
 *
 * <p>
 * The storage is an {@link InheritableThreadLocal}, so the context is visible to child
 * threads spawned from the current thread. Callers are responsible for invoking
 * {@link #clear()} when the context is no longer needed, typically in a
 * {@code try/finally} block, to prevent leakage when threads are reused.
 * </p>
 *
 * @author zz_zhi
 * @since 2.0.0
 */
public final class ToolContextHolder {

	private static final InheritableThreadLocal<@Nullable ToolContext> CONTEXT = new InheritableThreadLocal<>();

	private ToolContextHolder() {
	}

	/**
	 * Bind the given {@link ToolContext} to the current thread, replacing any value
	 * previously set. Passing {@code null} clears any value bound to the current thread.
	 * @param toolContext the context to bind, or {@code null} to clear
	 */
	public static void set(@Nullable ToolContext toolContext) {
		CONTEXT.set(toolContext);
	}

	/**
	 * Return the {@link ToolContext} bound to the current thread, or {@code null} if none
	 * has been set.
	 * @return the current context, or {@code null}
	 */
	public static @Nullable ToolContext get() {
		return CONTEXT.get();
	}

	/**
	 * Remove the {@link ToolContext} bound to the current thread. Should be invoked when
	 * the scope that established the context has finished (typically in a {@code finally}
	 * block) to prevent the context from leaking into subsequently reused threads.
	 */
	public static void clear() {
		CONTEXT.remove();
	}

}
