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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.observation.ChatClientObservationDocumentation.LowCardinalityKeyNames;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.observation.conventions.SpringAiKind;
import org.springframework.ai.observation.tracing.TracingHelper;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

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
		keyValues = advisors(keyValues, context);
		keyValues = conversationId(keyValues, context);
		keyValues = tools(keyValues, context);
		// @deprecated remove before 1.0.0-RC1.
		keyValues = chatClientAdvisorParams(keyValues, context);
		// @deprecated remove before 1.0.0-RC1.
		keyValues = toolNames(keyValues, context);
		// @deprecated remove before 1.0.0-RC1.
		keyValues = toolCallbacks(keyValues, context);
		return keyValues;
	}

	protected KeyValues advisors(KeyValues keyValues, ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getAdvisors())) {
			return keyValues;
		}
		var advisorNames = context.getAdvisors().stream().map(Advisor::getName).toList();
		return keyValues.and(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISORS.asString(),
				TracingHelper.concatenateStrings(advisorNames));
	}

	protected KeyValues conversationId(KeyValues keyValues, ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().context())) {
			return keyValues;
		}

		var conversationIdValue = context.getRequest()
			.context()
			.get(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY);
		if (!(conversationIdValue instanceof String conversationId) || StringUtils.isEmpty(conversationId)) {
			return keyValues;
		}

		return keyValues.and(
				ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_CONVERSATION_ID.asString(),
				conversationId);
	}

	protected KeyValues tools(KeyValues keyValues, ChatClientObservationContext context) {
		if (context.getRequest().prompt().getOptions() == null) {
			return keyValues;
		}
		if (!(context.getRequest().prompt().getOptions() instanceof ToolCallingChatOptions options)) {
			return keyValues;
		}

		var toolNames = new ArrayList<>(options.getToolNames());
		var toolCallbacks = options.getToolCallbacks();

		if (CollectionUtils.isEmpty(toolNames) && CollectionUtils.isEmpty(toolCallbacks)) {
			return keyValues;
		}

		toolCallbacks.forEach(toolCallback -> toolNames.add(toolCallback.getToolDefinition().name()));

		return keyValues.and(
				ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_TOOL_NAMES.asString(),
				TracingHelper.concatenateStrings(toolNames.stream().sorted().toList()));
	}

	/**
	 * @deprecated risk to expose sensitive information or break the instrumentation since
	 * the advisor context map is used to pass arbitrary Java objects between advisors and
	 * not necessarily serializable. The conversation ID, previously part of this, is
	 * already included in the
	 * {@link #conversationId(KeyValues, ChatClientObservationContext)} method.
	 */
	@Deprecated
	protected KeyValues chatClientAdvisorParams(KeyValues keyValues, ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().context())) {
			return keyValues;
		}
		var chatClientContext = new HashMap<>(context.getRequest().context());
		Arrays.stream(ChatClientAttributes.values()).forEach(attribute -> chatClientContext.remove(attribute.getKey()));
		return keyValues.and(
				ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_ADVISOR_PARAMS.asString(),
				TracingHelper.concatenateMaps(chatClientContext));
	}

	/**
	 * @deprecated in favor of {@link #tools(KeyValues, ChatClientObservationContext)}
	 */
	@Deprecated
	protected KeyValues toolNames(KeyValues keyValues, ChatClientObservationContext context) {
		if (context.getRequest().prompt().getOptions() == null) {
			return keyValues;
		}
		if (!(context.getRequest().prompt().getOptions() instanceof ToolCallingChatOptions options)) {
			return keyValues;
		}

		var toolNames = options.getToolNames();
		if (CollectionUtils.isEmpty(toolNames)) {
			return keyValues;
		}

		return keyValues.and(
				ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_NAMES.asString(),
				TracingHelper.concatenateStrings(toolNames.stream().sorted().toList()));
	}

	/**
	 * @deprecated in favor of {@link #tools(KeyValues, ChatClientObservationContext)}
	 */
	@Deprecated
	protected KeyValues toolCallbacks(KeyValues keyValues, ChatClientObservationContext context) {
		if (context.getRequest().prompt().getOptions() == null) {
			return keyValues;
		}
		if (!(context.getRequest().prompt().getOptions() instanceof ToolCallingChatOptions options)) {
			return keyValues;
		}

		var toolCallbacks = options.getToolCallbacks();
		if (CollectionUtils.isEmpty(toolCallbacks)) {
			return keyValues;
		}

		var toolCallbackNames = toolCallbacks.stream()
			.map(toolCallback -> toolCallback.getToolDefinition().name())
			.sorted()
			.toList();
		return keyValues
			.and(ChatClientObservationDocumentation.HighCardinalityKeyNames.CHAT_CLIENT_TOOL_FUNCTION_CALLBACKS
				.asString(), TracingHelper.concatenateStrings(toolCallbackNames));
	}

}
