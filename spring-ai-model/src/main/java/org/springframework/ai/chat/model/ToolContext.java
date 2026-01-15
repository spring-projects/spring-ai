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

package org.springframework.ai.chat.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.Message;

/**
 * Represents the context for tool execution in a function calling scenario.
 *
 * <p>
 * This class encapsulates a map of contextual information that can be passed to tools
 * (functions) when they are called. It provides an immutable view of the context to
 * ensure thread-safety and prevent modification after creation.
 * </p>
 *
 * <p>
 * The context is typically populated from the {@code toolContext} field of
 * {@code ToolCallingChatOptions} and is used in the function execution process.
 * </p>
 *
 * <p>
 * The context map can contain any information that is relevant to the tool execution.
 * </p>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public final class ToolContext {

	/**
	 * The key for the running, tool call history stored in the context map.
	 */
	public static final String TOOL_CALL_HISTORY = "TOOL_CALL_HISTORY";

	private final Map<String, Object> context;

	/**
	 * Constructs a new ToolContext with the given context map.
	 * @param context A map containing the tool context information. This map is wrapped
	 * in an unmodifiable view to prevent changes.
	 */
	public ToolContext(Map<String, Object> context) {
		this.context = Collections.unmodifiableMap(context);
	}

	/**
	 * Returns the immutable context map.
	 * @return An unmodifiable view of the context map.
	 */
	public Map<String, Object> getContext() {
		return this.context;
	}

	/**
	 * Returns the tool call history from the context map.
	 * @return The tool call history. TODO: review whether we still need this or
	 * ToolCallingManager solves the original issue
	 */
	@SuppressWarnings({ "unchecked", "NullAway" })
	public List<Message> getToolCallHistory() {
		return (List<Message>) this.context.get(TOOL_CALL_HISTORY);
	}

}
