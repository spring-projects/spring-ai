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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.messages.AbstractMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.transformer.AbstractPromptTransformer;
import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import org.springframework.ai.chat.prompt.transformer.PromptChange;
import org.springframework.ai.chat.prompt.transformer.TransformerContentType;
import org.springframework.util.Assert;

/**
 * @deprecated Use the {@link PromptChatMemoryAdvisor} instead.
 * @author Christian Tzolov
 */
@Deprecated
public class SystemPromptChatMemoryAugmentor extends AbstractPromptTransformer {

	public static final String DEFAULT_HISTORY_PROMPT = """
			Use the conversation history from the HISTORY section to provide accurate answers.

			HISTORY:
			{history}
			 """;

	private final String historyPrompt;

	/**
	 * Only Content entries with the following metadata tags will be included in the
	 * history.
	 */
	private final Set<String> filterTags;

	public SystemPromptChatMemoryAugmentor() {
		this(DEFAULT_HISTORY_PROMPT, new HashSet<>());
	}

	public SystemPromptChatMemoryAugmentor(Set<String> filterTags) {
		this(DEFAULT_HISTORY_PROMPT, filterTags);
	}

	public SystemPromptChatMemoryAugmentor(String historyPrompt, Set<String> metadataFilterTags) {
		Assert.hasText(historyPrompt, "The historyPrompt must not be empty!");
		Assert.notNull(metadataFilterTags, "The metadataFilterTags must not be null!");
		this.historyPrompt = historyPrompt;
		this.filterTags = new HashSet<>(metadataFilterTags);

		// Always include the message history type tag.
		this.filterTags.add(TransformerContentType.MEMORY);
	}

	@Override
	public ChatServiceContext transform(ChatServiceContext chatServiceContext) {

		var originalPrompt = chatServiceContext.getPrompt();

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

		String historyContext = chatServiceContext.getContents()
			.stream()
			.filter(content -> this.filterTags.stream().allMatch(tag -> content.getMetadata().containsKey(tag)))
			.map(content -> content.getMetadata().get(AbstractMessage.MESSAGE_TYPE) + ": " + content.getContent())
			.collect(Collectors.joining(System.lineSeparator()));

		SystemMessage newSystemMessage = new SystemMessage(originalSystemMessage.getContent() + System.lineSeparator()
				+ this.historyPrompt.replace("{history}", historyContext));

		List<Message> newPromptMessages = new ArrayList<>();
		newPromptMessages.add(newSystemMessage);
		newPromptMessages.addAll(nonSystemMessages);

		Prompt newPrompt = new Prompt(newPromptMessages, (ChatOptions) originalPrompt.getOptions());
		PromptChange promptChange = new PromptChange(originalPrompt, newPrompt, this.getName(),
				"Added chat memory into the system prompt");
		return ChatServiceContext.from(chatServiceContext).withPrompt(newPrompt).withPromptChange(promptChange).build();
	}

}