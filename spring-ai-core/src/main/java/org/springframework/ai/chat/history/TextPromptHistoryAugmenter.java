/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat.history;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * @author Christian Tzolov
 */
public class TextPromptHistoryAugmenter implements PromptHistoryAugmenter {

	public static final String HISTORY_PROMPT = """
			Use the conversation history from the HISTORY section to provide accurate answers.

			HISTORY:
			{history}
				""";

	@Override
	public Prompt augment(Prompt originalPrompt, List<ChatExchange> chatExchangeList) {

		List<Message> systemMessages = (originalPrompt.getInstructions() != null) ? originalPrompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.SYSTEM)
			.toList() : List.of();

		List<Message> nonSystemMessages = (originalPrompt.getInstructions() != null) ? originalPrompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() != MessageType.SYSTEM)
			.toList() : List.of();

		SystemMessage originalSystemMessage = (!systemMessages.isEmpty()) ? (SystemMessage) systemMessages.get(0)
				: new SystemMessage("");

		String historyContext = chatExchangeList.stream()
			.map(ce -> ce.getMessages())
			.flatMap(List::stream)
			.map(msg -> msg.getMessageType() + ": " + msg.getContent())
			.collect(Collectors.joining(System.lineSeparator()));

		SystemMessage newSystemMessage = new SystemMessage(originalSystemMessage.getContent() + System.lineSeparator()
				+ HISTORY_PROMPT.replace("{history}", historyContext));

		System.out.println(newSystemMessage.getContent());

		List<Message> newPromptMessages = new ArrayList<>();
		newPromptMessages.add(newSystemMessage);
		newPromptMessages.addAll(nonSystemMessages);

		return new Prompt(newPromptMessages, (ChatOptions) originalPrompt.getOptions());
	}

}
