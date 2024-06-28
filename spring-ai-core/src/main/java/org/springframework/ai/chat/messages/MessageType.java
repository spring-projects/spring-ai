/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.messages;

/**
 * The MessageType enum represents the type of message in a chat application. It can be
 * one of the following: USER, ASSISTANT, SYSTEM, FUNCTION.
 */
public enum MessageType {

	/**
	 * A message of the type 'user' passed as input Messages with the user role are from
	 * the end-user or developer.
	 * @see UserMessage
	 */
	USER("user"),

	/**
	 * A message of the type 'assistant' passed as input Messages with the message is
	 * generated as a response to the user.
	 * @see AssistantMessage
	 */
	ASSISTANT("assistant"),

	/**
	 * A message of the type 'system' passed as input Messages with high level
	 * instructions for the conversation, such as behave like a certain character or
	 * provide answers in a specific format.
	 * @see SystemMessage
	 */
	SYSTEM("system"),

	/**
	 * A message of the type 'function' passed as input Messages with a function content
	 * in a chat application.
	 * @see FunctionMessage
	 */
	FUNCTION("function");

	private final String value;

	MessageType(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static MessageType fromValue(String value) {
		for (MessageType messageType : MessageType.values()) {
			if (messageType.getValue().equals(value)) {
				return messageType;
			}
		}
		throw new IllegalArgumentException("Invalid MessageType value: " + value);
	}

}
