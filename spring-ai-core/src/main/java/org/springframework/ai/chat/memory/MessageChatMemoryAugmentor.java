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

package org.springframework.ai.chat.memory;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.transformer.AbstractPromptTransformer;
import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import org.springframework.ai.chat.prompt.transformer.PromptChange;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;

/**
 * @author Christian Tzolov
 */
public class MessageChatMemoryAugmentor extends AbstractPromptTransformer {

	@Override
	public ChatServiceContext transform(ChatServiceContext chatServiceContext) {

		var originalPrompt = chatServiceContext.getPrompt();

		// Convert the retrieved contents into a list of messages.
		List<Message> historyMessages = chatServiceContext.getContents()
			.stream()
			.filter(content -> content.getMetadata().containsKey(TransformerContentType.MEMORY))
			.map(content -> {
				MessageType messageType = MessageType
					.valueOf("" + content.getMetadata().get(AbstractMessage.MESSAGE_TYPE));
				Message message = null;
				if (messageType == MessageType.ASSISTANT) {
					message = new AssistantMessage(content.getContent(), content.getMetadata());
				}
				else if (messageType == MessageType.USER) {
					message = new UserMessage(content.getContent(), List.of(), content.getMetadata());
				}
				return message;
			})
			.filter(m -> m != null)
			.toList();

		var promptMessages = new ArrayList<>(historyMessages);
		promptMessages.addAll(originalPrompt.getInstructions());

		Prompt newPrompt = new Prompt(promptMessages, (ChatOptions) originalPrompt.getOptions());
		PromptChange promptChange = new PromptChange(originalPrompt, newPrompt, this.getName(),
				"Added chat memory as individual messages in the prompt");

		return ChatServiceContext.from(chatServiceContext).withPrompt(newPrompt).withPromptChange(promptChange).build();
	}

}