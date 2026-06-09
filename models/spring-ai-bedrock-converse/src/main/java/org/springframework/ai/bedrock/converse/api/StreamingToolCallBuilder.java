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

package org.springframework.ai.bedrock.converse.api;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.util.Assert;

/**
 * @author Jared Rufer
 * @since 1.1.0
 */
public class StreamingToolCallBuilder {

	private final StringBuffer arguments = new StringBuffer();

	private volatile @Nullable String id;

	private volatile @Nullable String name;

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
		Assert.state(this.id != null, "Tool call id must not be null");
		Assert.state(this.name != null, "Tool call name must not be null");
		// Workaround to handle streaming tool calling with no input arguments.
		String toolArgs = this.arguments.isEmpty() ? "{}" : this.arguments.toString();
		return new AssistantMessage.ToolCall(this.id, "function", this.name, toolArgs);
	}

}
