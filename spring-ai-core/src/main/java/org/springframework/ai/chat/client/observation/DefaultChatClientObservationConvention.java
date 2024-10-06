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

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.observation.tracing.TracingHelper;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * Default conventions to populate observations for chat client workflows.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultChatClientObservationConvention implements ChatClientObservationConvention {

	public static final String DEFAULT_NAME = "spring.ai.chat.client";

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
		return "%s %s".formatted(context.getOperationMetadata().provider(), SpringAiKind.CHAT_CLIENT.value());
	}

	@Override
	public KeyValues getLowCardinalityKeyValues(ChatClientObservationContext context) {
		return KeyValues.of(aiOperationType(context), aiProvider(context), springAiKind(), stream(context));
	}

	protected KeyValue aiOperationType(ChatClientObservationContext context) {
		return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_OPERATION_TYPE,
				context.getOperationMetadata().operationType());
	}

	protected KeyValue aiProvider(ChatClientObservationContext context) {
		return KeyValue.of(ChatModelObservationDocumentation.LowCardinalityKeyNames.AI_PROVIDER,
				context.getOperationMetadata().provider());
	}

	protected KeyValue springAiKind() {
		return KeyValue.of(ChatClientObservationDocumentation.LowCardinalityKeyNames.SPRING_AI_KIND,
				SpringAiKind.CHAT_CLIENT.value());
	}

	protected KeyValue stream(ChatClientObservationContext context) {
		return KeyValue.of(LowCardinalityKeyNames.STREAM, "" + context.isStream());
	}

	@Override
	public KeyValues getHighCardinalityKeyValues(ChatClientObservationContext context) {
		var keyValues = KeyValues.empty();
		keyValues = chatClientAdvisorNames(keyValues, context);
		keyValues = chatClientAdvisorParams(keyValues, context);
		keyValues = toolFunctionNames(keyValues, context);
		keyValues = toolFunctionCallbacks(keyValues, context);
		return keyValues;
	}

	protected KeyValues chatClientAdvisorNames(KeyValues keyValues, ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getAdvisors())) {
			return keyValues;
		}
		var advisorNames = context.getRequest().getAdvisors().stream().map(Advisor::getName).toList();
		return keyValues.and(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISORS.asString(),
				TracingHelper.concatenateStrings(advisorNames));
	}

	protected KeyValues chatClientAdvisorParams(KeyValues keyValues, ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getAdvisorParams())) {
			return keyValues;
		}
		var advisorParams = context.getRequest().getAdvisorParams();
		return keyValues.and(
				ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISOR_PARAMS.asString(),
				TracingHelper.concatenateMaps(advisorParams));
	}

	protected KeyValues toolFunctionNames(KeyValues keyValues, ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getFunctionNames())) {
			return keyValues;
		}
		var functionNames = context.getRequest().getFunctionNames();
		return keyValues.and(
				ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_NAMES.asString(),
				TracingHelper.concatenateStrings(functionNames));
	}

	protected KeyValues toolFunctionCallbacks(KeyValues keyValues, ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getFunctionCallbacks())) {
			return keyValues;
		}
		var functionCallbacks = context.getRequest()
			.getFunctionCallbacks()
			.stream()
			.map(FunctionCallback::getName)
			.toList();
		return keyValues
			.and(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_CALLBACKS
				.asString(), TracingHelper.concatenateStrings(functionCallbacks));
	}

}