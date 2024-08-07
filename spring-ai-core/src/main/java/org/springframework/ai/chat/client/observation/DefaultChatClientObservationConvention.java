/*
* Copyright 2024 - 2024 the original author or authors.
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

import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class DefaultChatClientObservationConvention implements ChatClientObservationConvention {

	public static final String DEFAULT_NAME = "gen_ai.chat.client.operation";

	private static final KeyValue STATUS_NONE = KeyValue.of(LowCardinalityKeyNames.STATUS, KeyValue.NONE_VALUE);

	private static final KeyValue TOOL_FUNCTION_NAMES_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.TOOL_FUNCTION_NAMES, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_ADVISOR_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISOR, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_ADVISOR_PARAM_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISOR_PARAM, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_USER_TEXT_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_TEXT, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_USER_PARAM_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_PARAM, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_SYSTEM_TEXT_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_TEXT, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_SYSTEM_PARAM_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_SYSTEM_PARAM, KeyValue.NONE_VALUE);

	private final String name;

	public DefaultChatClientObservationConvention() {
		this(DEFAULT_NAME);
	}

	public DefaultChatClientObservationConvention(String name) {
		this.name = name;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	@Nullable
	public String getContextualName(ChatClientObservationContext context) {
		return "%s %s".formatted(context.getOperationMetadata().operationType(),
				context.getOperationMetadata().provider());
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ChatClientObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), stream(context), status(context),
				toolFunctionNames(context), chatClientAvisor(context), chatClientAvisorParam(context),
				chatClientUserText(context), chatClientUserParam(context), chatClientSystemText(context),
				chatClientSystemParam(context));
	}

	protected KeyValue stream(ChatClientObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.STREAM, "" + context.isStream());
	}

	protected KeyValue status(ChatClientObservationContext context) {
		return STATUS_NONE;
	}

	protected KeyValue aiOperationType(ChatClientObservationContext context) {
		return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(ChatClientObservationContext context) {
		return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue toolFunctionNames(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getFunctionNames())) {
			return TOOL_FUNCTION_NAMES_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.TOOL_FUNCTION_NAMES,
				context.getRequest().getFunctionNames().stream().collect(Collectors.joining(",")));
	}

	protected KeyValue chatClientAvisor(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getAdvisors())) {
			return CHAT_CLIENT_ADVISOR_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISOR,
				context.getRequest().getAdvisors().stream().map(a -> a.getName()).collect(Collectors.joining(",")));
	}

	protected KeyValue chatClientAvisorParam(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getAdvisorParams())) {
			return CHAT_CLIENT_ADVISOR_PARAM_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISOR,
				context.getRequest()
					.getAdvisorParams()
					.entrySet()
					.stream()
					.map(e -> e.getKey() + ":" + e.getValue())
					.collect(Collectors.joining(",")));
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
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_USER_PARAM,
				context.getRequest()
					.getUserParams()
					.entrySet()
					.stream()
					.map(e -> e.getKey() + ":" + e.getValue())
					.collect(Collectors.joining(",")));
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
					.map(e -> e.getKey() + ":" + e.getValue())
					.collect(Collectors.joining(",")));
	}

}