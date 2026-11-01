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

package org.springframework.ai.model.tool;

/**
 * Determines what {@link DefaultToolCallingManager} does when a configured tool call
 * limit (per tool name or total per turn) is exceeded.
 *
 * @author Christian Tzolov
 * @since 2.0.1
 */
public enum ToolCallLimitBehavior {

	/**
	 * Throw a {@link ToolCallLimitExceededException} carrying the tool calls executed so
	 * far, aborting the current batch of tool calls immediately.
	 */
	THROW,

	/**
	 * Skip invoking the tool callback and instead return a
	 * {@link org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse}
	 * explaining that the limit was reached, letting the model see the rejection and
	 * decide how to proceed.
	 */
	RETURN_ERROR_RESPONSE

}
