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

package org.springframework.ai.model.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * A set of options that can be used to configure the interaction with a chat model,
 * including tool calling.
 *
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
public interface ToolCallingChatOptions extends ChatOptions {

	boolean DEFAULT_TOOL_EXECUTION_ENABLED = true;

	/**
	 * ToolCallbacks to be registered with the ChatModel.
	 */
	List<ToolCallback> getToolCallbacks();

	/**
	 * Set the ToolCallbacks to be registered with the ChatModel.
	 */
	void setToolCallbacks(List<ToolCallback> toolCallbacks);

	/**
	 * Names of the tools to register with the ChatModel.
	 */
	Set<String> getToolNames();

	/**
	 * Set the names of the tools to register with the ChatModel.
	 */
	void setToolNames(Set<String> toolNames);

	/**
	 * Whether the {@link ChatModel} is responsible for executing the tools requested by
	 * the model or if the tools should be executed directly by the caller.
	 */
	@Nullable
	Boolean getInternalToolExecutionEnabled();

	/**
	 * Set whether the {@link ChatModel} is responsible for executing the tools requested
	 * by the model or if the tools should be executed directly by the caller.
	 */
	void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled);

	/**
	 * Get the configured tool context.
	 * @return the tool context map.
	 */
	Map<String, Object> getToolContext();

	/**
	 * Set the tool context values as map.
	 * @param toolContext as map
	 */
	void setToolContext(Map<String, Object> toolContext);

	/**
	 * A builder to create a new {@link ToolCallingChatOptions} instance.
	 */
	static Builder builder() {
		return new DefaultToolCallingChatOptions.Builder();
	}

	static boolean isInternalToolExecutionEnabled(ChatOptions chatOptions) {
		Assert.notNull(chatOptions, "chatOptions cannot be null");
		boolean internalToolExecutionEnabled;
		if (chatOptions instanceof ToolCallingChatOptions toolCallingChatOptions
				&& toolCallingChatOptions.getInternalToolExecutionEnabled() != null) {
			internalToolExecutionEnabled = Boolean.TRUE
				.equals(toolCallingChatOptions.getInternalToolExecutionEnabled());
		}
		else {
			internalToolExecutionEnabled = DEFAULT_TOOL_EXECUTION_ENABLED;
		}
		return internalToolExecutionEnabled;
	}

	static Set<String> mergeToolNames(Set<String> runtimeToolNames, Set<String> defaultToolNames) {
		Assert.notNull(runtimeToolNames, "runtimeToolNames cannot be null");
		Assert.notNull(defaultToolNames, "defaultToolNames cannot be null");
		if (CollectionUtils.isEmpty(runtimeToolNames)) {
			return new HashSet<>(defaultToolNames);
		}
		return new HashSet<>(runtimeToolNames);
	}

	static List<ToolCallback> mergeToolCallbacks(List<ToolCallback> runtimeToolCallbacks,
			List<ToolCallback> defaultToolCallbacks) {
		Assert.notNull(runtimeToolCallbacks, "runtimeToolCallbacks cannot be null");
		Assert.notNull(defaultToolCallbacks, "defaultToolCallbacks cannot be null");
		if (CollectionUtils.isEmpty(runtimeToolCallbacks)) {
			return new ArrayList<>(defaultToolCallbacks);
		}
		return new ArrayList<>(runtimeToolCallbacks);
	}

	static Map<String, Object> mergeToolContext(Map<String, Object> runtimeToolContext,
			Map<String, Object> defaultToolContext) {
		Assert.notNull(runtimeToolContext, "runtimeToolContext cannot be null");
		Assert.noNullElements(runtimeToolContext.keySet(), "runtimeToolContext keys cannot be null");
		Assert.notNull(defaultToolContext, "defaultToolContext cannot be null");
		Assert.noNullElements(defaultToolContext.keySet(), "defaultToolContext keys cannot be null");
		var mergedToolContext = new HashMap<>(defaultToolContext);
		mergedToolContext.putAll(runtimeToolContext);
		return mergedToolContext;
	}

	static void validateToolCallbacks(List<ToolCallback> toolCallbacks) {
		List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
		if (!duplicateToolNames.isEmpty()) {
			throw new IllegalStateException("Multiple tools with the same name (%s) found in ToolCallingChatOptions"
				.formatted(String.join(", ", duplicateToolNames)));
		}
	}

	/**
	 * A builder to create a {@link ToolCallingChatOptions} instance.
	 */
	interface Builder extends ChatOptions.Builder {

		/**
		 * ToolCallbacks to be registered with the ChatModel.
		 */
		Builder toolCallbacks(List<ToolCallback> toolCallbacks);

		/**
		 * ToolCallbacks to be registered with the ChatModel.
		 */
		Builder toolCallbacks(ToolCallback... toolCallbacks);

		/**
		 * Names of the tools to register with the ChatModel.
		 */
		Builder toolNames(Set<String> toolNames);

		/**
		 * Names of the tools to register with the ChatModel.
		 */
		Builder toolNames(String... toolNames);

		/**
		 * Whether the {@link ChatModel} is responsible for executing the tools requested
		 * by the model or if the tools should be executed directly by the caller.
		 */
		Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled);

		/**
		 * Add a {@link Map} of context values into tool context.
		 * @param context the map representing the tool context.
		 * @return the {@link ToolCallingChatOptions} Builder.
		 */
		Builder toolContext(Map<String, Object> context);

		/**
		 * Add a specific key/value pair to the tool context.
		 * @param key the key to use.
		 * @param value the corresponding value.
		 * @return the {@link ToolCallingChatOptions} Builder.
		 */
		Builder toolContext(String key, Object value);

		// ChatOptions.Builder methods

		@Override
		Builder model(@Nullable String model);

		@Override
		Builder frequencyPenalty(@Nullable Double frequencyPenalty);

		@Override
		Builder maxTokens(@Nullable Integer maxTokens);

		@Override
		Builder presencePenalty(@Nullable Double presencePenalty);

		@Override
		Builder stopSequences(@Nullable List<String> stopSequences);

		@Override
		Builder temperature(@Nullable Double temperature);

		@Override
		Builder topK(@Nullable Integer topK);

		@Override
		Builder topP(@Nullable Double topP);

		@Override
		ToolCallingChatOptions build();

	}

}
