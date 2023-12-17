/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.bedrock;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.prompt.messages.Message;
import org.springframework.ai.prompt.messages.MessageType;

/**
 * Converts a list of messages to a prompt for bedrock models.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
public class MessageToPromptConverter {

	private static final String HUMAN_PROMPT = "Human:";

	private static final String ASSISTANT_PROMPT = "Assistant:";

	private String humanPrompt = HUMAN_PROMPT;

	private String assistantPrompt = ASSISTANT_PROMPT;

	private MessageToPromptConverter() {
	}

	public static MessageToPromptConverter create() {
		return new MessageToPromptConverter();
	}

	public MessageToPromptConverter withHumanPrompt(String humanPrompt) {
		this.humanPrompt = humanPrompt;
		return this;
	}

	public MessageToPromptConverter withAssistantPrompt(String assistantPrompt) {
		this.assistantPrompt = assistantPrompt;
		return this;
	}

	public String toPrompt(List<Message> messages) {

		final String systemMessages = messages.stream()
			.filter(message -> message.getMessageType() == MessageType.SYSTEM)
			.map(Message::getContent)
			.collect(Collectors.joining("\n"));

		final String userMessages = messages.stream()
			.filter(message -> message.getMessageType() == MessageType.USER
					|| message.getMessageType() == MessageType.ASSISTANT)
			.map(this::messageToString)
			.collect(Collectors.joining("\n"));

		final String prompt = String.format("%s%n%n%s%n%s", systemMessages, userMessages, ASSISTANT_PROMPT);

		return prompt;
	}

	protected String messageToString(Message message) {
		switch (message.getMessageType()) {
			case SYSTEM:
				return message.getContent();
			case USER:
				return humanPrompt + " " + message.getContent();
			case ASSISTANT:
				return assistantPrompt + " " + message.getContent();
			case FUNCTION:
				throw new IllegalArgumentException("Tool execution results are not supported for Bedrock models");
		}

		throw new IllegalArgumentException("Unknown message type: " + message.getMessageType());
	}

}
