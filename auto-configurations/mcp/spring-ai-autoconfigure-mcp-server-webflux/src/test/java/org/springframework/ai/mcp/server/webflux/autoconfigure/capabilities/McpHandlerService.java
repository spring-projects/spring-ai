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

package org.springframework.ai.mcp.server.webflux.autoconfigure.capabilities;

import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.annotation.McpSampling;
import org.springframework.stereotype.Service;

@Service
public class McpHandlerService {

	private final ChatClient client;

	public McpHandlerService(ChatClient.Builder chatClientBuilder) {
		this.client = chatClientBuilder.build();
	}

	@McpSampling(clients = "server1")
	public McpSchema.CreateMessageResult samplingHandler(McpSchema.CreateMessageRequest llmRequest) {

		String userPrompt = ((McpSchema.TextContent) llmRequest.messages().get(0).content()).text();
		String modelHint = llmRequest.modelPreferences().hints().get(0).name();
		// In a real use-case, we would use the chat client to call the LLM again

		return McpSchema.CreateMessageResult
			.builder(McpSchema.Role.ASSISTANT, "Response " + userPrompt + " with model hint " + modelHint, modelHint)
			.build();
	}

}
