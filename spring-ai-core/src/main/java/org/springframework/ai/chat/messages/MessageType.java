/*
 * Copyright 2023 the original author or authors.
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

public enum MessageType {

	USER("user"),

	ASSISTANT("assistant"),

	SYSTEM("system"),

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
