/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.messages;

/**
 * Enumeration representing types of {@link Message Messages} in a chat application. It
 * can be one of the following: USER, ASSISTANT, SYSTEM, FUNCTION.
 */
public enum MessageType {

	/**
	 * A {@link Message} of type {@literal user}, having the user role and originating
	 * from an end-user or developer.
	 * @see UserMessage
	 */
	USER("user"),

	/**
	 * A {@link Message} of type {@literal assistant} passed in subsequent input
	 * {@link Message Messages} as the {@link Message} generated in response to the user.
	 * @see AssistantMessage
	 */
	ASSISTANT("assistant"),

	/**
	 * A {@link Message} of type {@literal system} passed as input {@link Message
	 * Messages} containing high-level instructions for the conversation, such as behave
	 * like a certain character or provide answers in a specific format.
	 * @see SystemMessage
	 */
	SYSTEM("system"),

	/**
	 * A {@link Message} of type {@literal function} passed as input {@link Message
	 * Messages} with function content in a chat application.
	 * @see ToolResponseMessage
	 */
	TOOL("tool");

	private final String value;

	MessageType(String value) {
		this.value = value;
	}

	public String getValue() {
		return this.value;
	}

}
