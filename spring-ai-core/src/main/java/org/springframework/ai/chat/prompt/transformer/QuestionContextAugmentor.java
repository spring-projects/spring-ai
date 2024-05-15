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

package org.springframework.ai.chat.prompt.transformer;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.Content;

/**
 * Transforms the Prompt by taking to the current prompt in the Prompt Context and adding
 * additional context to create a new prompt. The default user text contains the
 * placeholder names "question" and "context". The "question" placeholder is filled using
 * the value of the current UserMessage and the "context" placeholder is filled with
 * Documents contained in the ChatServiceContext's Nodes.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0.0 M1
 */
public class QuestionContextAugmentor extends AbstractPromptTransformer {

	private static final String DEFAULT_USER_TEXT = """
			   "Context information is below.\\n"
			   "---------------------\\n"
			   "{context}\\n"
			   "---------------------\\n"
			   "Given the context and provided history information and not prior knowledge, "
			   "reply to the user comment. If the answer is not in the context, inform "
			   "the user that you can't answer the question.\\n"
			   "User comment: {question}\\n"
			   "Answer: "
			""";

	private String userText;

	public QuestionContextAugmentor() {
		this.userText = DEFAULT_USER_TEXT;
		this.setName("QuestionContextAugmentor");
	}

	public String getUserText() {
		return userText;
	}

	@Override
	public ChatServiceContext transform(ChatServiceContext chatServiceContext) {
		String context = doCreateContext(chatServiceContext.getContents());
		Map<String, Object> contextMap = doCreateContextMap(chatServiceContext.getPrompt(), context);
		Prompt prompt = doCreatePrompt(chatServiceContext.getPrompt(), contextMap);
		chatServiceContext.updatePrompt(prompt, this.getName(), "Updated prompt with Q/A user text");

		// For now return the modified instance instead of a copy
		return chatServiceContext;
	}

	protected String doCreateContext(List<Content> data) {
		return data.stream().map(Content::getContent).collect(Collectors.joining(System.lineSeparator()));
	}

	private Map<String, Object> doCreateContextMap(Prompt prompt, String context) {
		String originalUserMessage = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.USER)
			.map(m -> m.getContent())
			.collect(Collectors.joining(System.lineSeparator()));

		return Map.of("context", context, "question", originalUserMessage);
	}

	protected Prompt doCreatePrompt(Prompt originalPrompt, Map<String, Object> contextMap) {
		PromptTemplate promptTemplate = new PromptTemplate(getUserText());
		Message userMessageToAppend = promptTemplate.createMessage(contextMap);
		List<Message> messageList = originalPrompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() != MessageType.USER)
			.collect(Collectors.toList());
		messageList.add(userMessageToAppend);
		return new Prompt(messageList, (ChatOptions) originalPrompt.getOptions());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String name;

		private String userText;

		public Builder withName(String name) {
			this.name = name;
			return this;
		}

		public Builder withUserText(String userText) {
			this.userText = userText;
			return this;
		}

		public QuestionContextAugmentor build() {
			QuestionContextAugmentor instance = new QuestionContextAugmentor();
			instance.userText = this.userText != null ? this.userText : instance.userText;
			instance.setName(this.name != null ? this.name : instance.getName());
			return instance;
		}

	}

}
