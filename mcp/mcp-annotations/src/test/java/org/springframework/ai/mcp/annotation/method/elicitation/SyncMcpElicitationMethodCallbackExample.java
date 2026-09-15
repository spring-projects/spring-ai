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

package org.springframework.ai.mcp.annotation.method.elicitation;

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;

import org.springframework.ai.mcp.annotation.McpElicitation;

/**
 * Example class demonstrating synchronous elicitation method usage.
 *
 * @author Christian Tzolov
 */
public class SyncMcpElicitationMethodCallbackExample {

	@McpElicitation(clients = "my-client-id")
	public ElicitResult handleElicitationRequest(ElicitRequest request) {
		// Example implementation that accepts the request and returns some content
		return new ElicitResult(ElicitResult.Action.ACCEPT,
				Map.of("userInput", "Example user input", "confirmed", true));
	}

	@McpElicitation(clients = "my-client-id")
	public ElicitResult handleDeclineElicitationRequest(ElicitRequest request) {
		// Example implementation that declines the request
		return new ElicitResult(ElicitResult.Action.DECLINE, null);
	}

	@McpElicitation(clients = "my-client-id")
	public ElicitResult handleCancelElicitationRequest(ElicitRequest request) {
		// Example implementation that cancels the request
		return new ElicitResult(ElicitResult.Action.CANCEL, null);
	}

	// Test methods for invalid scenarios

	@McpElicitation(clients = "my-client-id")
	public String invalidReturnType(ElicitRequest request) {
		return "Invalid return type";
	}

	@McpElicitation(clients = "my-client-id")
	public ElicitResult invalidParameterType(String request) {
		return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value"));
	}

	@McpElicitation(clients = "my-client-id")
	public ElicitResult noParameters() {
		return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value"));
	}

	@McpElicitation(clients = "my-client-id")
	public ElicitResult tooManyParameters(ElicitRequest request, String extra) {
		return new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("test", "value"));
	}

}
