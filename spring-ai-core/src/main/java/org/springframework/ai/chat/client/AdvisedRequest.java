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

package org.springframework.ai.chat.client;

import java.util.List;
import java.util.Map;

import org.springframework.ai.model.Media;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;

/**
 * The data of the chat client request that can be modified before the execution of the
 * ChatClient's call method
 *
 * @author Christian Tzolov
 * @since 1.0.0
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
 */
public record AdvisedRequest(ChatModel chatModel, String userText, String systemText, ChatOptions chatOptions,
		List<Media> media, List<String> functionNames, List<FunctionCallback> functionCallbacks, List<Message> messages,
		Map<String, Object> userParams, Map<String, Object> systemParams, List<Advisor> advisors,
		Map<String, Object> advisorParams) {

	public static Builder from(AdvisedRequest from) {
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
		return builder;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private ChatModel chatModel;

		private String userText = "";

		private String systemText = "";

		private ChatOptions chatOptions = null;

		private List<Media> media = List.of();

		private List<String> functionNames = List.of();

		private List<FunctionCallback> functionCallbacks = List.of();

		private List<Message> messages = List.of();

		private Map<String, Object> userParams = Map.of();

		private Map<String, Object> systemParams = Map.of();

		private List<Advisor> advisors = List.of();

		private Map<String, Object> advisorParams = Map.of();

		public Builder withChatModel(ChatModel chatModel) {
			this.chatModel = chatModel;
			return this;
		}

		public Builder withUserText(String userText) {
			this.userText = userText;
			return this;
		}

		public Builder withSystemText(String systemText) {
			this.systemText = systemText;
			return this;
		}

		public Builder withChatOptions(ChatOptions chatOptions) {
			this.chatOptions = chatOptions;
			return this;
		}

		public Builder withMedia(List<Media> media) {
			this.media = media;
			return this;
		}

		public Builder withFunctionNames(List<String> functionNames) {
			this.functionNames = functionNames;
			return this;
		}

		public Builder withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
			this.functionCallbacks = functionCallbacks;
			return this;
		}

		public Builder withMessages(List<Message> messages) {
			this.messages = messages;
			return this;
		}

		public Builder withUserParams(Map<String, Object> userParams) {
			this.userParams = userParams;
			return this;
		}

		public Builder withSystemParams(Map<String, Object> systemParams) {
			this.systemParams = systemParams;
			return this;
		}

		public Builder withAdvisors(List<Advisor> advisors) {
			this.advisors = advisors;
			return this;
		}

		public Builder withAdvisorParams(Map<String, Object> advisorParams) {
			this.advisorParams = advisorParams;
			return this;
		}

		public AdvisedRequest build() {
			return new AdvisedRequest(chatModel, this.userText, this.systemText, this.chatOptions, this.media,
					this.functionNames, this.functionCallbacks, this.messages, this.userParams, this.systemParams,
					this.advisors, this.advisorParams);
		}

	}

}