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

package org.springframework.ai.chat.client.advisor.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * The data of the chat client request that can be modified before the execution of the
 * ChatClient's call method
 *
 * @param chatModel the chat model used
 * @param userText the text provided by the user
 * @param systemText the text provided by the system
 * @param chatOptions the options for the chat
 * @param media the list of media items
 * @param functionNames the list of function names
 * @param functionCallbacks the list of function callbacks
 * @param messages the list of messages
 * @param userParams the map of user parameters
 * @param systemParams the map of system parameters
 * @param advisors the list of request response advisors
 * @param advisorParams the map of advisor parameters
 * @param adviseContext the map of advise context
 * @param toolContext the tool context
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public record AdvisedRequest(
// @formatter:off
		ChatModel chatModel,
		String userText,
		@Nullable
		String systemText,
		@Nullable
		ChatOptions chatOptions,
		List<Media> media,
		List<String> functionNames,
		List<FunctionCallback> functionCallbacks,
		List<Message> messages,
		Map<String, Object> userParams,
		Map<String, Object> systemParams,
		List<Advisor> advisors,
		Map<String, Object> advisorParams,
		Map<String, Object> adviseContext,
		Map<String, Object> toolContext
// @formatter:on
) {

	public AdvisedRequest {
		Assert.notNull(chatModel, "chatModel cannot be null");
		Assert.isTrue(StringUtils.hasText(userText) || !CollectionUtils.isEmpty(messages),
				"userText cannot be null or empty unless messages are provided and contain Tool Response message.");
		Assert.notNull(media, "media cannot be null");
		Assert.noNullElements(media, "media cannot contain null elements");
		Assert.notNull(functionNames, "functionNames cannot be null");
		Assert.noNullElements(functionNames, "functionNames cannot contain null elements");
		Assert.notNull(functionCallbacks, "functionCallbacks cannot be null");
		Assert.noNullElements(functionCallbacks, "functionCallbacks cannot contain null elements");
		Assert.notNull(messages, "messages cannot be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");
		Assert.notNull(userParams, "userParams cannot be null");
		Assert.noNullElements(userParams.keySet(), "userParams keys cannot contain null elements");
		Assert.noNullElements(userParams.values(), "userParams values cannot contain null elements");
		Assert.notNull(systemParams, "systemParams cannot be null");
		Assert.noNullElements(systemParams.keySet(), "systemParams keys cannot contain null elements");
		Assert.noNullElements(systemParams.values(), "systemParams values cannot contain null elements");
		Assert.notNull(advisors, "advisors cannot be null");
		Assert.noNullElements(advisors, "advisors cannot contain null elements");
		Assert.notNull(advisorParams, "advisorParams cannot be null");
		Assert.noNullElements(advisorParams.keySet(), "advisorParams keys cannot contain null elements");
		Assert.noNullElements(advisorParams.values(), "advisorParams values cannot contain null elements");
		Assert.notNull(adviseContext, "adviseContext cannot be null");
		Assert.noNullElements(adviseContext.keySet(), "adviseContext keys cannot contain null elements");
		Assert.noNullElements(adviseContext.values(), "adviseContext values cannot contain null elements");
		Assert.notNull(toolContext, "toolContext cannot be null");
		Assert.noNullElements(toolContext.keySet(), "toolContext keys cannot contain null elements");
		Assert.noNullElements(toolContext.values(), "toolContext values cannot contain null elements");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static Builder from(AdvisedRequest from) {
		Assert.notNull(from, "AdvisedRequest cannot be null");

		Builder builder = new Builder();
		builder.chatModel = from.chatModel;
		builder.userText = from.userText;
		builder.systemText = from.systemText;
		builder.chatOptions = from.chatOptions;
		builder.media = from.media;
		builder.functionNames = from.functionNames;
		builder.functionCallbacks = from.functionCallbacks;
		builder.messages = from.messages;
		builder.userParams = from.userParams;
		builder.systemParams = from.systemParams;
		builder.advisors = from.advisors;
		builder.advisorParams = from.advisorParams;
		builder.adviseContext = from.adviseContext;
		builder.toolContext = from.toolContext;
		return builder;
	}

	public AdvisedRequest updateContext(Function<Map<String, Object>, Map<String, Object>> contextTransform) {
		Assert.notNull(contextTransform, "contextTransform cannot be null");
		return from(this)
			.adviseContext(Collections.unmodifiableMap(contextTransform.apply(new HashMap<>(this.adviseContext))))
			.build();
	}

	public Prompt toPrompt() {
		var messages = new ArrayList<>(this.messages());

		String processedSystemText = this.systemText();
		if (StringUtils.hasText(processedSystemText)) {
			if (!CollectionUtils.isEmpty(this.systemParams())) {
				processedSystemText = new PromptTemplate(processedSystemText, this.systemParams()).render();
			}
			messages.add(new SystemMessage(processedSystemText));
		}

		String formatParam = (String) this.adviseContext().get("formatParam");

		var processedUserText = StringUtils.hasText(formatParam)
				? this.userText() + System.lineSeparator() + "{spring_ai_soc_format}" : this.userText();

		if (StringUtils.hasText(processedUserText)) {
			Map<String, Object> userParams = new HashMap<>(this.userParams());
			if (StringUtils.hasText(formatParam)) {
				userParams.put("spring_ai_soc_format", formatParam);
			}
			if (!CollectionUtils.isEmpty(userParams)) {
				processedUserText = new PromptTemplate(processedUserText, userParams).render();
			}
			messages.add(new UserMessage(processedUserText, this.media()));
		}

		if (this.chatOptions() instanceof FunctionCallingOptions functionCallingOptions) {
			if (!this.functionNames().isEmpty()) {
				functionCallingOptions.setFunctions(new HashSet<>(this.functionNames()));
			}
			if (!this.functionCallbacks().isEmpty()) {
				functionCallingOptions.setFunctionCallbacks(this.functionCallbacks());
			}
			if (!CollectionUtils.isEmpty(this.toolContext())) {
				functionCallingOptions.setToolContext(this.toolContext());
			}
		}

		return new Prompt(messages, this.chatOptions());
	}

	/**
	 * Builder for {@link AdvisedRequest}.
	 */
	public static final class Builder {

		private ChatModel chatModel;

		private String userText;

		private String systemText;

		private ChatOptions chatOptions;

		private List<Media> media = List.of();

		private List<String> functionNames = List.of();

		private List<FunctionCallback> functionCallbacks = List.of();

		private List<Message> messages = List.of();

		private Map<String, Object> userParams = Map.of();

		private Map<String, Object> systemParams = Map.of();

		private List<Advisor> advisors = List.of();

		private Map<String, Object> advisorParams = Map.of();

		private Map<String, Object> adviseContext = Map.of();

		public Map<String, Object> toolContext = Map.of();

		private Builder() {
		}

		/**
		 * Set the chat model.
		 * @param chatModel the chat model
		 * @return this {@link Builder} instance
		 */
		public Builder chatModel(ChatModel chatModel) {
			this.chatModel = chatModel;
			return this;
		}

		/**
		 * Set the user text.
		 * @param userText the user text
		 * @return this {@link Builder} instance
		 */
		public Builder userText(String userText) {
			this.userText = userText;
			return this;
		}

		/**
		 * Set the system text.
		 * @param systemText the system text
		 * @return this {@link Builder} instance
		 */
		public Builder systemText(String systemText) {
			this.systemText = systemText;
			return this;
		}

		/**
		 * Set the chat options.
		 * @param chatOptions the chat options
		 * @return this {@link Builder} instance
		 */
		public Builder chatOptions(ChatOptions chatOptions) {
			this.chatOptions = chatOptions;
			return this;
		}

		/**
		 * Set the media.
		 * @param media the media
		 * @return this {@link Builder} instance
		 */
		public Builder media(List<Media> media) {
			this.media = media;
			return this;
		}

		/**
		 * Set the function names.
		 * @param functionNames the function names
		 * @return this {@link Builder} instance
		 */
		public Builder functionNames(List<String> functionNames) {
			this.functionNames = functionNames;
			return this;
		}

		/**
		 * Set the function callbacks.
		 * @param functionCallbacks the function callbacks
		 * @return this {@link Builder} instance
		 */
		public Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			this.functionCallbacks = functionCallbacks;
			return this;
		}

		/**
		 * Set the messages.
		 * @param messages the messages
		 * @return this {@link Builder} instance
		 */
		public Builder messages(List<Message> messages) {
			this.messages = messages;
			return this;
		}

		/**
		 * Set the user params.
		 * @param userParams the user params
		 * @return this {@link Builder} instance
		 */
		public Builder userParams(Map<String, Object> userParams) {
			this.userParams = userParams;
			return this;
		}

		/**
		 * Set the system params.
		 * @param systemParams the system params
		 * @return this {@link Builder} instance
		 */
		public Builder systemParams(Map<String, Object> systemParams) {
			this.systemParams = systemParams;
			return this;
		}

		/**
		 * Set the advisors.
		 * @param advisors the advisors
		 * @return this {@link Builder} instance
		 */
		public Builder advisors(List<Advisor> advisors) {
			this.advisors = advisors;
			return this;
		}

		/**
		 * Set the advisor params.
		 * @param advisorParams the advisor params
		 * @return this {@link Builder} instance
		 */
		public Builder advisorParams(Map<String, Object> advisorParams) {
			this.advisorParams = advisorParams;
			return this;
		}

		/**
		 * Set the advise context.
		 * @param adviseContext the advise context
		 * @return this {@link Builder} instance
		 */
		public Builder adviseContext(Map<String, Object> adviseContext) {
			this.adviseContext = adviseContext;
			return this;
		}

		/**
		 * Set the tool context.
		 * @param toolContext the tool context
		 * @return this {@link Builder} instance
		 */
		public Builder toolContext(Map<String, Object> toolContext) {
			this.toolContext = toolContext;
			return this;
		}

		/**
		 * Build the {@link AdvisedRequest} instance.
		 * @return a new {@link AdvisedRequest} instance
		 */
		public AdvisedRequest build() {
			return new AdvisedRequest(this.chatModel, this.userText, this.systemText, this.chatOptions, this.media,
					this.functionNames, this.functionCallbacks, this.messages, this.userParams, this.systemParams,
					this.advisors, this.advisorParams, this.adviseContext, this.toolContext);
		}

	}

}
