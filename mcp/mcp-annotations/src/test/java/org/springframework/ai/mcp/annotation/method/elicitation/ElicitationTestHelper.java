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

package org.springframework.ai.mcp.annotation.method.elicitation;

import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.ElicitRequest;

/**
 * Test helper for creating elicitation test data.
 *
 * @author Christian Tzolov
 */
public final class ElicitationTestHelper {

	private ElicitationTestHelper() {
	}

	/**
	 * Helper method to create a sample elicit request.
	 * @return A sample elicit request
	 */
	public static ElicitRequest createSampleRequest() {
		return new ElicitRequest("Please provide your input for the following task",
				Map.of("taskType", "userInput", "required", true, "description", "Enter your response"));
	}

	/**
	 * Helper method to create a sample elicit request with custom prompt.
	 * @param prompt The prompt to use
	 * @return A sample elicit request with custom prompt
	 */
	public static ElicitRequest createSampleRequest(String prompt) {
		return new ElicitRequest(prompt, Map.of("taskType", "userInput", "required", true));
	}

	/**
	 * Helper method to create a sample elicit request with custom prompt and context.
	 * @param prompt The prompt to use
	 * @param context The context to use
	 * @return A sample elicit request with custom prompt and context
	 */
	public static ElicitRequest createSampleRequest(String prompt, Map<String, Object> context) {
		return new ElicitRequest(prompt, context);
	}

}
