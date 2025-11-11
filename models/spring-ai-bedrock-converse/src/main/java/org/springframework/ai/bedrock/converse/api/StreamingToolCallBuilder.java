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

package org.springframework.ai.bedrock.converse.api;

import org.springframework.ai.chat.messages.AssistantMessage;

/**
 * @author Jared Rufer
 * @since 1.1.0
 */
public class StreamingToolCallBuilder {

	private final StringBuffer arguments = new StringBuffer();

	private volatile String id;

	private volatile String name;

	public StreamingToolCallBuilder id(String id) {
		this.id = id;
		return this;
	}

	public StreamingToolCallBuilder name(String name) {
		this.name = name;
		return this;
	}

	public StreamingToolCallBuilder delta(String delta) {
		this.arguments.append(delta);
		return this;
	}

	public AssistantMessage.ToolCall build() {
		// Workaround to handle streaming tool calling with no input arguments.
		String toolArgs = this.arguments.isEmpty() ? "{}" : this.arguments.toString();
		return new AssistantMessage.ToolCall(this.id, "function", this.name, toolArgs);
	}

}
