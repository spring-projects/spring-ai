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

package org.springframework.ai.chat.client.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.observation.tracing.TracingHelper;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * An {@link ObservationFilter} to include the chat prompt content in the observation.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ChatClientInputContentObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ChatClientObservationContext chatClientObservationContext)) {
			return context;
		}
		// TODO: we really want these? Should probably align with same format as chat
		// model observation
		chatClientSystemText(chatClientObservationContext);
		chatClientSystemParams(chatClientObservationContext);
		chatClientUserText(chatClientObservationContext);
		chatClientUserParams(chatClientObservationContext);

		return chatClientObservationContext;
	}

	protected void chatClientSystemText(ChatClientObservationContext context) {
		List<Message> messages = context.getRequest().prompt().getInstructions();
		if (CollectionUtils.isEmpty(messages)) {
			return;
		}

		var systemMessage = messages.stream()
			.filter(message -> message instanceof SystemMessage)
			.reduce((first, second) -> second);
		if (systemMessage.isEmpty()) {
			return;
		}
		context.addHighCardinalityKeyValue(
				KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_TEXT,
						systemMessage.get().getText()));
	}

	@SuppressWarnings("unchecked")
	protected void chatClientSystemParams(ChatClientObservationContext context) {
		if (!(context.getRequest()
			.context()
			.get(ChatClientAttributes.SYSTEM_PARAMS.getKey()) instanceof Map<?, ?> systemParams)) {
			return;
		}
		if (CollectionUtils.isEmpty(systemParams)) {
			return;
		}

		context.addHighCardinalityKeyValue(
				KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_PARAM,
						TracingHelper.concatenateMaps((Map<String, Object>) systemParams)));
	}

	protected void chatClientUserText(ChatClientObservationContext context) {
		List<Message> messages = context.getRequest().prompt().getInstructions();
		if (CollectionUtils.isEmpty(messages)) {
			return;
		}

		if (!(messages.get(messages.size() - 1) instanceof UserMessage userMessage)) {
			return;
		}
		context.addHighCardinalityKeyValue(
				KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_TEXT,
						userMessage.getText()));
	}

	@SuppressWarnings("unchecked")
	protected void chatClientUserParams(ChatClientObservationContext context) {
		if (!(context.getRequest()
			.context()
			.get(ChatClientAttributes.USER_PARAMS.getKey()) instanceof Map<?, ?> userParams)) {
			return;
		}
		if (CollectionUtils.isEmpty(userParams)) {
			return;
		}
		context.addHighCardinalityKeyValue(
				KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_PARAMS,
						TracingHelper.concatenateMaps((Map<String, Object>) userParams)));
	}

}
