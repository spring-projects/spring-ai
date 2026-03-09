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

package org.springframework.ai.mcp.annotation.method.sampling;

import java.util.List;

import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.ModelPreferences;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.SamplingMessage;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Test helper for sampling tests.
 *
 * @author Christian Tzolov
 */
public final class SamplingTestHelper {

	private SamplingTestHelper() {
	}

	/**
	 * Helper method to create a sample request.
	 * @return A sample request
	 */
	public static CreateMessageRequest createSampleRequest() {
		SamplingMessage userMessage = new SamplingMessage(Role.USER,
				new TextContent("Hello, can you help me with a task?"));

		return CreateMessageRequest.builder()
			.messages(List.of(userMessage))
			.modelPreferences(ModelPreferences.builder().addHint("claude-3-haiku").build())
			.systemPrompt("You are a helpful assistant.")
			.temperature(0.7)
			.build();
	}

}
