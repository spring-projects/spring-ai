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

package org.springframework.ai.chat.observation;

import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.observation.ToolDefinitionsSerializer;
import org.springframework.util.CollectionUtils;

/**
 * An {@link ObservationFilter} to include the definitions of the tools provided to the
 * model in the chat model observation, as the {@code gen_ai.tool.definitions} attribute.
 * The value follows the OpenTelemetry tool definitions schema, see
 * {@link ToolDefinitionsSerializer}. By default, only the {@code type} and {@code name}
 * of each tool are included.
 *
 * @author Ajax-1
 * @since 2.1.0
 * @see ToolDefinitionsSerializer
 */
public class ChatModelToolDefinitionsObservationFilter implements ObservationFilter {

	private final ToolDefinitionsSerializer serializer;

	/**
	 * Create a filter that includes the type and name of each tool.
	 */
	public ChatModelToolDefinitionsObservationFilter() {
		this(false, false);
	}

	/**
	 * Create a filter.
	 * @param includeDescriptions whether to include the tool descriptions
	 * @param includeParameters whether to include the tool parameter schemas
	 */
	public ChatModelToolDefinitionsObservationFilter(boolean includeDescriptions, boolean includeParameters) {
		this.serializer = new ToolDefinitionsSerializer(includeDescriptions, includeParameters);
	}

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
			return context;
		}
		if (!(chatModelObservationContext.getRequest().getOptions() instanceof ToolCallingChatOptions options)) {
			return chatModelObservationContext;
		}
		List<ToolCallback> toolCallbacks = options.getToolCallbacks();
		if (CollectionUtils.isEmpty(toolCallbacks)) {
			return chatModelObservationContext;
		}

		chatModelObservationContext.addHighCardinalityKeyValue(
				ChatModelObservationDocumentation.HighCardinalityKeyNames.REQUEST_TOOL_DEFINITIONS
					.withValue(this.serializer
						.serialize(toolCallbacks.stream().map(ToolCallback::getToolDefinition).toList())));

		return chatModelObservationContext;
	}

}
