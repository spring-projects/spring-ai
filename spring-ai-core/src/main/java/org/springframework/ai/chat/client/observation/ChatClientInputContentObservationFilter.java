/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.client.observation;

import java.util.stream.Collectors;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

/**
 * An {@link ObservationFilter} to include the chat prompt content in the observation.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class ChatClientInputContentObservationFilter implements ObservationFilter {

	private static final KeyValue CHAT_CLIENT_SYSTEM_TEXT_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_TEXT, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_SYSTEM_PARAM_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_PARAM, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_USER_TEXT_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_TEXT, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_USER_PARAM_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_PARAMS, KeyValue.NONE_VALUE);

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ChatClientObservationContext chatClientObservationContext)) {
			return context;
		}

		chatClientObservationContext.addHighCardinalityKeyValue(chatClientSystemText(chatClientObservationContext))
			.addHighCardinalityKeyValue(chatClientSystemParam(chatClientObservationContext))
			.addHighCardinalityKeyValue(chatClientUserText(chatClientObservationContext))
			.addHighCardinalityKeyValue(chatClientUserParam(chatClientObservationContext));

		return chatClientObservationContext;
	}

	protected KeyValue chatClientSystemText(ChatClientObservationContext context) {
		if (!StringUtils.hasText(context.getRequest().getUserText())) {
			return CHAT_CLIENT_SYSTEM_TEXT_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_TEXT,
				context.getRequest().getSystemText());
	}

	protected KeyValue chatClientSystemParam(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getSystemParams())) {
			return CHAT_CLIENT_SYSTEM_PARAM_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_PARAM,
				context.getRequest()
					.getSystemParams()
					.entrySet()
					.stream()
					.map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
					.collect(Collectors.joining(",", "[", "]")));
	}

	protected KeyValue chatClientUserText(ChatClientObservationContext context) {
		if (!StringUtils.hasText(context.getRequest().getUserText())) {
			return CHAT_CLIENT_USER_TEXT_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_TEXT,
				context.getRequest().getUserText());
	}

	protected KeyValue chatClientUserParam(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getUserParams())) {
			return CHAT_CLIENT_USER_PARAM_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_PARAMS,
				context.getRequest()
					.getUserParams()
					.entrySet()
					.stream()
					.map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
					.collect(Collectors.joining(",", "[", "]")));
	}

}
