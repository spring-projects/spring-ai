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

import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import org.springframework.ai.mcp.annotation.McpSampling;

/**
 * Example class with methods annotated with {@link McpSampling} for testing the
 * synchronous sampling method callback.
 *
 * @author Christian Tzolov
 */
public class SyncMcpSamplingMethodCallbackExample {

	/**
	 * Example method that handles a sampling request and returns a result.
	 * @param request The sampling request
	 * @return The sampling result
	 */
	@McpSampling(clients = "test-client")
	public CreateMessageResult handleSamplingRequest(CreateMessageRequest request) {
		// Process the request and return a result
		return CreateMessageResult.builder()
			.role(Role.ASSISTANT)
			.content(new TextContent("This is a response to the sampling request"))
			.model("test-model")
			.build();
	}

	/**
	 * Example method with an invalid return type.
	 * @param request The sampling request
	 * @return A string (invalid return type)
	 */
	@McpSampling(clients = "test-client")
	public String invalidReturnType(CreateMessageRequest request) {
		return "This method has an invalid return type";
	}

	/**
	 * Example method with an invalid parameter type.
	 * @param invalidParam An invalid parameter type
	 * @return The sampling result
	 */
	@McpSampling(clients = "test-client")
	public CreateMessageResult invalidParameterType(String invalidParam) {
		return CreateMessageResult.builder()
			.role(Role.ASSISTANT)
			.content(new TextContent("This method has an invalid parameter type"))
			.model("test-model")
			.build();
	}

	/**
	 * Example method with no parameters.
	 * @return The sampling result
	 */
	@McpSampling(clients = "test-client")
	public CreateMessageResult noParameters() {
		return CreateMessageResult.builder()
			.role(Role.ASSISTANT)
			.content(new TextContent("This method has no parameters"))
			.model("test-model")
			.build();
	}

	/**
	 * Example method with too many parameters.
	 * @param request The sampling request
	 * @param extraParam An extra parameter
	 * @return The sampling result
	 */
	@McpSampling(clients = "test-client")
	public CreateMessageResult tooManyParameters(CreateMessageRequest request, String extraParam) {
		return CreateMessageResult.builder()
			.role(Role.ASSISTANT)
			.content(new TextContent("This method has too many parameters"))
			.model("test-model")
			.build();
	}

}
