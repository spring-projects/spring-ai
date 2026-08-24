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

package org.springframework.ai.gpullama3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.beehive.gpullama3.model.Model;
import org.beehive.gpullama3.model.format.ChatFormat;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.MediaContent;
import org.springframework.util.Assert;

/**
 * Encodes Spring AI prompts into the token sequence expected by the GPULlama3 inference
 * engine.
 *
 * <p>
 * Spring AI represents a chat request as a {@link Prompt}, which contains an ordered list
 * of {@link Message} objects. GPULlama3 does not consume those Spring AI message objects
 * directly. It expects a list of token ids formatted according to the model's
 * {@link ChatFormat}.
 * </p>
 *
 * <p>
 * This class is responsible for that conversion:
 * </p>
 *
 * <pre>
 * Spring AI Prompt
 *     -> Spring AI Message list
 *     -> GPULlama3 ChatFormat.Message
 *     -> token ids
 * </pre>
 *
 * <p>
 * The mapping is:
 * </p>
 *
 * <ul>
 * <li>{@link SystemMessage} -> {@link ChatFormat.Role#SYSTEM}</li>
 * <li>{@link UserMessage} -> {@link ChatFormat.Role#USER}</li>
 * <li>{@link AssistantMessage} -> {@link ChatFormat.Role#ASSISTANT}</li>
 * </ul>
 *
 * <p>
 * Tool messages, assistant tool calls, and media content are rejected because the
 * GPULlama3 text generation engine does not currently support Spring AI tool calling or
 * multimodal input.
 * </p>
 *
 * @since 2.0.1
 */
public final class GpuLlama3PromptEncoder {

	private static final Logger logger = LoggerFactory.getLogger(GpuLlama3PromptEncoder.class);

	/**
	 * Converts a Spring AI {@link Prompt} into immutable GPULlama3 prompt tokens.
	 * <p>
	 * The token sequence follows the same structure used by the GPULlama3 / LangChain4j
	 * adapter:
	 * </p>
	 *
	 * <ol>
	 * <li>Add the model begin-of-text token if the model requires it.</li>
	 * <li>Encode every Spring AI message in prompt order using the model's
	 * {@link ChatFormat}.</li>
	 * <li>Append an empty assistant header so the model knows the next generated tokens
	 * should belong to the assistant response.</li>
	 * </ol>
	 *
	 */
	public List<Integer> encode(Prompt prompt, Model model) {
		Assert.notNull(prompt, "prompt must not be null");
		Assert.notNull(model, "model must not be null");

		ChatFormat chatFormat = model.chatFormat();
		Assert.notNull(chatFormat, "model chatFormat must not be null");

		List<Integer> promptTokens = new ArrayList<>();
		if (model.shouldAddBeginOfText()) {
			promptTokens.add(chatFormat.getBeginOfText());
		}

		for (Message message : prompt.getInstructions()) {
			appendMessage(promptTokens, chatFormat, model, message);
		}

		promptTokens.addAll(chatFormat.encodeHeader(new ChatFormat.Message(ChatFormat.Role.ASSISTANT, "")));
		return List.copyOf(promptTokens);
	}

	/**
	 * Appends one Spring AI message to the GPULlama3 prompt token list.
	 *
	 * <p>
	 * Only text chat messages are supported. Unsupported Spring AI features are rejected
	 * explicitly instead of being ignored, because silently dropping tool or media
	 * content would make the model receive a different conversation from what the caller
	 * requested.
	 * </p>
	 */
	private static void appendMessage(List<Integer> promptTokens, ChatFormat chatFormat, Model model, Message message) {

		// System prompts are only encoded when the underlying GPULlama3 model declares
		// support for them. Some model formats do not include a system role.
		if (message instanceof SystemMessage systemMessage) {
			if (model.shouldAddSystemPrompt()) {
				appendChatMessage(promptTokens, chatFormat, ChatFormat.Role.SYSTEM, systemMessage.getText());
			}
			else {
				logger.warn(
						"Skipping SystemMessage because the GPULlama3 model chat format does not support system prompts");
			}
			return;
		}

		// User messages are mapped to the GPULlama3 USER role.
		// GPULlama3 is text-only in this integration.
		if (message instanceof UserMessage userMessage) {
			rejectMedia(userMessage, "UserMessage");
			appendChatMessage(promptTokens, chatFormat, ChatFormat.Role.USER, userMessage.getText());
			return;
		}

		// Assistant messages represent conversation history.
		// GPULlama3 does not implement Spring AI's tool-calling protocol.
		if (message instanceof AssistantMessage assistantMessage) {
			if (assistantMessage.hasToolCalls()) {
				throw new UnsupportedOperationException("GPULlama3 does not support assistant tool calls");
			}
			rejectMedia(assistantMessage, "AssistantMessage");
			appendChatMessage(promptTokens, chatFormat, ChatFormat.Role.ASSISTANT, assistantMessage.getText());
			return;
		}

		// Tool response messages have no equivalent role in GPULlama3's chat format.
		if (message instanceof ToolResponseMessage) {
			throw new UnsupportedOperationException("GPULlama3 does not support tool response messages");
		}

		throw new UnsupportedOperationException("Unsupported Spring AI message type: " + message.getClass().getName());
	}

	/**
	 * Encodes one role/content pair using the model-specific chat format.
	 *
	 * <p>
	 * Null text is treated as an empty string so that the chat format never receives null
	 * content.
	 * </p>
	 */
	private static void appendChatMessage(List<Integer> promptTokens, ChatFormat chatFormat, ChatFormat.Role role,
			@Nullable String content) {
		promptTokens
			.addAll(chatFormat.encodeMessage(new ChatFormat.Message(role, Objects.requireNonNullElse(content, ""))));
	}

	/**
	 * Rejects multimodal message content.
	 *
	 * <p>
	 * Spring AI messages can carry media such as images, but GPULlama3 only accepts text
	 * tokens. Failing fast keeps the caller aware that the media was not used.
	 * </p>
	 */
	private static void rejectMedia(MediaContent mediaContent, String messageType) {
		if (!mediaContent.getMedia().isEmpty()) {
			throw new UnsupportedOperationException("GPULlama3 does not support media content in " + messageType);
		}
	}

}
