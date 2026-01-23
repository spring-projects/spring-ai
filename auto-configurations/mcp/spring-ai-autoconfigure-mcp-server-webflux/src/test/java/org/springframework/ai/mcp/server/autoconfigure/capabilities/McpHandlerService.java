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

package org.springframework.ai.mcp.server.autoconfigure.capabilities;

import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpSampling;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class McpHandlerService {

	private static final Logger logger = LoggerFactory.getLogger(McpHandlerService.class);

	private final ChatClient client;

	public McpHandlerService(ChatClient.Builder chatClientBuilder) {
		this.client = chatClientBuilder.build();
	}

	@McpSampling(clients = "server1")
	public McpSchema.CreateMessageResult samplingHandler(McpSchema.CreateMessageRequest llmRequest) {
		logger.info("MCP SAMPLING: {}", llmRequest);

		String userPrompt = ((McpSchema.TextContent) llmRequest.messages().get(0).content()).text();
		String modelHint = llmRequest.modelPreferences().hints().get(0).name();
		// In a real use-case, we would use the chat client to call the LLM again
		logger.info("MCP SAMPLING: simulating using chat client {}", this.client);

		return McpSchema.CreateMessageResult.builder()
			.content(new McpSchema.TextContent("Response " + userPrompt + " with model hint " + modelHint))
			.build();
	}

}
