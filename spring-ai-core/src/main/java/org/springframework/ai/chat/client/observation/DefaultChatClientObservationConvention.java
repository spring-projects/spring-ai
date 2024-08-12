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

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class DefaultChatClientObservationConvention implements ChatClientObservationConvention {

	public static final String DEFAULT_NAME = "spring.ai.chat.client.operation";

	private static final String CHAT_CLIENT_SPRING_AI_KIND = "chat_client";

	private static final KeyValue CHAT_CLIENT_TOOL_FUNCTION_CALLBACKS_NONE = KeyValue.of(
			ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_CALLBACKS,
			KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_TOOL_FUNCTION_NAMES_NONE = KeyValue.of(
			ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_NAMES,
			KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_ADVISOR_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISORS, KeyValue.NONE_VALUE);

	private static final KeyValue CHAT_CLIENT_ADVISOR_PARAM_NONE = KeyValue
		.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISOR_PARAMS, KeyValue.NONE_VALUE);

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
		return "%s %s".formatted(context.getOperationMetadata().provider(), CHAT_CLIENT_SPRING_AI_KIND);
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ChatClientObservationContext context) {
		return KeyValues.of(springAiKind(context), aiOperationType(context), aiProvider(context), stream(context));
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ChatClientObservationContext context) {
		return KeyValues.of(toolFunctionNames(context), toolFunctionCallbacks(context), chatClientAvisor(context),
				chatClientAvisorParam(context));
	}

	protected KeyValue springAiKind(ChatClientObservationContext context) {
		return KeyValue.of(ChatClientObservationDocumentation.LowCardinalityKeyNames.SPRING_AI_KIND,
				CHAT_CLIENT_SPRING_AI_KIND);
	}

	protected KeyValue stream(ChatClientObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.STREAM, "" + context.isStream());
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
			return CHAT_CLIENT_TOOL_FUNCTION_NAMES_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_NAMES,
				context.getRequest()
					.getFunctionNames()
					.stream()
					.map(v -> "\"" + v + "\"")
					.collect(Collectors.joining(",", "[", "]")));
	}

	protected KeyValue toolFunctionCallbacks(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getFunctionCallbacks())) {
			return CHAT_CLIENT_TOOL_FUNCTION_CALLBACKS_NONE;
		}

		return KeyValue.of(
				ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_CALLBACKS,
				context.getRequest()
					.getFunctionCallbacks()
					.stream()
					.map(fc -> "\"" + fc.getName() + "\"")
					.collect(Collectors.joining(",", "[", "]")));
	}

	protected KeyValue chatClientAvisor(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getAdvisors())) {
			return CHAT_CLIENT_ADVISOR_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISORS,
				context.getRequest()
					.getAdvisors()
					.stream()
					.map(a -> "\"" + a.getName() + "\"")
					.collect(Collectors.joining(",", "[", "]")));
	}

	protected KeyValue chatClientAvisorParam(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getAdvisorParams())) {
			return CHAT_CLIENT_ADVISOR_PARAM_NONE;
		}
		return KeyValue.of(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISOR_PARAMS,
				context.getRequest()
					.getAdvisorParams()
					.entrySet()
					.stream()
					.map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
					.collect(Collectors.joining(",", "[", "]")));
	}

}