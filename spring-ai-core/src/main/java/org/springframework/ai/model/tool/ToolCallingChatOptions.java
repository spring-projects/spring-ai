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

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A set of options that can be used to configure the interaction with a chat model,
 * including tool calling.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ToolCallingChatOptions extends FunctionCallingOptions {

	boolean DEFAULT_TOOL_EXECUTION_ENABLED = true;

	/**
	 * ToolCallbacks to be registered with the ChatModel.
	 */
	List<FunctionCallback> getToolCallbacks();

	/**
	 * Set the ToolCallbacks to be registered with the ChatModel.
	 */
	void setToolCallbacks(List<FunctionCallback> toolCallbacks);

	/**
	 * Names of the tools to register with the ChatModel.
	 */
	Set<String> getTools();

	/**
	 * Set the names of the tools to register with the ChatModel.
	 */
	void setTools(Set<String> toolNames);

	/**
	 * Whether the {@link ChatModel} is responsible for executing the tools requested by
	 * the model or if the tools should be executed directly by the caller.
	 */
	@Nullable
	Boolean isInternalToolExecutionEnabled();

	/**
	 * Set whether the {@link ChatModel} is responsible for executing the tools requested
	 * by the model or if the tools should be executed directly by the caller.
	 */
	void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled);

	/**
	 * A builder to create a new {@link ToolCallingChatOptions} instance.
	 */
	static Builder builder() {
		return new DefaultToolCallingChatOptions.Builder();
	}

	/**
	 * A builder to create a {@link ToolCallingChatOptions} instance.
	 */
	interface Builder extends FunctionCallingOptions.Builder {

		/**
		 * ToolCallbacks to be registered with the ChatModel.
		 */
		Builder toolCallbacks(List<FunctionCallback> functionCallbacks);

		/**
		 * ToolCallbacks to be registered with the ChatModel.
		 */
		Builder toolCallbacks(FunctionCallback... functionCallbacks);

		/**
		 * Names of the tools to register with the ChatModel.
		 */
		Builder tools(Set<String> toolNames);

		/**
		 * Names of the tools to register with the ChatModel.
		 */
		Builder tools(String... toolNames);

		/**
		 * Whether the {@link ChatModel} is responsible for executing the tools requested
		 * by the model or if the tools should be executed directly by the caller.
		 */
		Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled);

		// FunctionCallingOptions.Builder methods

		@Override
		Builder toolContext(Map<String, Object> context);

		@Override
		Builder toolContext(String key, Object value);

		@Override
		@Deprecated // Use toolCallbacks() instead
		Builder functionCallbacks(List<FunctionCallback> functionCallbacks);

		@Override
		@Deprecated // Use toolCallbacks() instead
		Builder functionCallbacks(FunctionCallback... functionCallbacks);

		@Override
		@Deprecated // Use tools() instead
		Builder functions(Set<String> functions);

		@Override
		@Deprecated // Use tools() instead
		Builder function(String function);

		@Override
		@Deprecated // Use internalToolExecutionEnabled() instead
		Builder proxyToolCalls(@Nullable Boolean proxyToolCalls);

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

	static boolean isInternalToolExecutionEnabled(ChatOptions chatOptions) {
		Assert.notNull(chatOptions, "chatOptions cannot be null");
		boolean internalToolExecutionEnabled;
		if (chatOptions instanceof ToolCallingChatOptions toolCallingChatOptions
				&& toolCallingChatOptions.isInternalToolExecutionEnabled() != null) {
			internalToolExecutionEnabled = Boolean.TRUE.equals(toolCallingChatOptions.isInternalToolExecutionEnabled());
		}
		else if (chatOptions instanceof FunctionCallingOptions functionCallingOptions
				&& functionCallingOptions.getProxyToolCalls() != null) {
			internalToolExecutionEnabled = Boolean.TRUE.equals(!functionCallingOptions.getProxyToolCalls());
		}
		else {
			internalToolExecutionEnabled = DEFAULT_TOOL_EXECUTION_ENABLED;
		}
		return internalToolExecutionEnabled;
	}

	static Set<String> mergeToolNames(Set<String> runtimeToolNames, Set<String> defaultToolNames) {
		Assert.notNull(runtimeToolNames, "runtimeToolNames cannot be null");
		Assert.notNull(defaultToolNames, "defaultToolNames cannot be null");
		var mergedToolNames = new HashSet<>(runtimeToolNames);
		mergedToolNames.addAll(defaultToolNames);
		return mergedToolNames;
	}

	static List<FunctionCallback> mergeToolCallbacks(List<FunctionCallback> runtimeToolCallbacks,
			List<FunctionCallback> defaultToolCallbacks) {
		Assert.notNull(runtimeToolCallbacks, "runtimeToolCallbacks cannot be null");
		Assert.notNull(defaultToolCallbacks, "defaultToolCallbacks cannot be null");
		var mergedToolCallbacks = new ArrayList<>(runtimeToolCallbacks);
		mergedToolCallbacks.addAll(defaultToolCallbacks);
		return mergedToolCallbacks;
	}

}
