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

package org.springframework.ai.model.tool;

/**
 * Exception thrown when the tool execution limit is exceeded.
 *
 * @author lambochen
 * @see ToolCallingChatOptions#getInternalToolExecutionMaxIterations()
 */
public class ToolExecutionLimitExceededException extends RuntimeException {

	private final Integer maxIterations;

	public ToolExecutionLimitExceededException(Integer maxIterations) {
		this("Tool execution limit exceeded: " + maxIterations, maxIterations);
	}

	public ToolExecutionLimitExceededException(String message, Integer maxIterations) {
		super(message);
		this.maxIterations = maxIterations;
	}

	public Integer getMaxIterations() {
		return maxIterations;
	}

}
