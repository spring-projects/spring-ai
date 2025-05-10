/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.client;

import org.springframework.ai.chat.messages.DeveloperMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilities for supporting the {@link DefaultChatClient} implementation.
 *
 * @author Thomas Vitale
 * @author Andres da Silva Santos
 * @since 1.0.0
 */
class DefaultChatClientUtils {

	static ChatClientRequest toChatClientRequest(DefaultChatClient.DefaultChatClientRequestSpec inputRequest) {
		Assert.notNull(inputRequest, "inputRequest cannot be null");

		/*
		 * ==========* MESSAGES * ==========
		 */

		List<Message> processedMessages = new ArrayList<>();

		// System Text => First in the list
		String processedSystemText = inputRequest.getSystemText();
		if (StringUtils.hasText(processedSystemText)) {
			if (!CollectionUtils.isEmpty(inputRequest.getSystemParams())) {
				processedSystemText = PromptTemplate.builder()
					.template(processedSystemText)
					.variables(inputRequest.getSystemParams())
					.renderer(inputRequest.getTemplateRenderer())
					.build()
					.render();
			}
			processedMessages.add(new SystemMessage(processedSystemText));
		}

		// Developer Text => First in the list
		String processedDeveloperText = inputRequest.getDeveloperText();
		if (StringUtils.hasText(processedDeveloperText)) {
			if (!CollectionUtils.isEmpty(inputRequest.getDeveloperParams())) {
				processedDeveloperText = PromptTemplate.builder()
					.template(processedDeveloperText)
					.variables(inputRequest.getDeveloperParams())
					.renderer(inputRequest.getTemplateRenderer())
					.build()
					.render();
			}
			processedMessages.add(new DeveloperMessage(processedDeveloperText));
		}

		// Messages => In the middle of the list
		if (!CollectionUtils.isEmpty(inputRequest.getMessages())) {
			processedMessages.addAll(inputRequest.getMessages());
		}

		// User Test => Last in the list
		String processedUserText = inputRequest.getUserText();
		if (StringUtils.hasText(processedUserText)) {
			if (!CollectionUtils.isEmpty(inputRequest.getUserParams())) {
				processedUserText = PromptTemplate.builder()
					.template(processedUserText)
					.variables(inputRequest.getUserParams())
					.renderer(inputRequest.getTemplateRenderer())
					.build()
					.render();
			}
			processedMessages.add(UserMessage.builder().text(processedUserText).media(inputRequest.getMedia()).build());
		}

		/*
		 * ==========* OPTIONS * ==========
		 */

		ChatOptions processedChatOptions = inputRequest.getChatOptions();
		if (processedChatOptions instanceof ToolCallingChatOptions toolCallingChatOptions) {
			if (!inputRequest.getToolNames().isEmpty()) {
				Set<String> toolNames = ToolCallingChatOptions
					.mergeToolNames(new HashSet<>(inputRequest.getToolNames()), toolCallingChatOptions.getToolNames());
				toolCallingChatOptions.setToolNames(toolNames);
			}
			if (!inputRequest.getToolCallbacks().isEmpty()) {
				List<ToolCallback> toolCallbacks = ToolCallingChatOptions
					.mergeToolCallbacks(inputRequest.getToolCallbacks(), toolCallingChatOptions.getToolCallbacks());
				ToolCallingChatOptions.validateToolCallbacks(toolCallbacks);
				toolCallingChatOptions.setToolCallbacks(toolCallbacks);
			}
			if (!CollectionUtils.isEmpty(inputRequest.getToolContext())) {
				Map<String, Object> toolContext = ToolCallingChatOptions.mergeToolContext(inputRequest.getToolContext(),
						toolCallingChatOptions.getToolContext());
				toolCallingChatOptions.setToolContext(toolContext);
			}
		}

		/*
		 * ==========* REQUEST * ==========
		 */

		return ChatClientRequest.builder()
			.prompt(Prompt.builder().messages(processedMessages).chatOptions(processedChatOptions).build())
			.context(new ConcurrentHashMap<>(inputRequest.getAdvisorParams()))
			.build();
	}

}
