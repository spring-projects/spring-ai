/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.chat.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Abstract base class for tool call support. Provides functionality for handling function
 * callbacks and executing functions.
 *
 * @author Christian Tzolov
 * @author Grogdunn
 * @author Thomas Vitale
 * @since 1.0.0
 */
public abstract class AbstractToolCallSupport {

	protected final static boolean IS_RUNTIME_CALL = true;

	/**
	 * The function callback register is used to resolve the function callbacks by name.
	 */
	protected final Map<String, FunctionCallback> functionCallbackRegister = new ConcurrentHashMap<>();

	/**
	 * The function callback context is used to resolve the function callbacks by name
	 * from the Spring context. It is optional and usually used with Spring
	 * auto-configuration.
	 */
	protected final FunctionCallbackContext functionCallbackContext;

	protected AbstractToolCallSupport(FunctionCallbackContext functionCallbackContext) {
		this.functionCallbackContext = functionCallbackContext;
	}

	public Map<String, FunctionCallback> getFunctionCallbackRegister() {
		return this.functionCallbackRegister;
	}

	protected Set<String> handleFunctionCallbackConfigurations(FunctionCallingOptions options, boolean isRuntimeCall) {

		Set<String> functionToCall = new HashSet<>();

		if (options != null) {
			if (!CollectionUtils.isEmpty(options.getFunctionCallbacks())) {
				options.getFunctionCallbacks().stream().forEach(functionCallback -> {

					// Register the tool callback.
					if (isRuntimeCall) {
						this.functionCallbackRegister.put(functionCallback.getName(), functionCallback);
					}
					else {
						this.functionCallbackRegister.putIfAbsent(functionCallback.getName(), functionCallback);
					}

					// Automatically enable the function, usually from prompt callback.
					if (isRuntimeCall) {
						functionToCall.add(functionCallback.getName());
					}
				});
			}

			// Add the explicitly enabled functions.
			if (!CollectionUtils.isEmpty(options.getFunctions())) {
				functionToCall.addAll(options.getFunctions());
			}
		}

		return functionToCall;
	}

	protected List<Message> handleToolCalls(Prompt prompt, ChatResponse response) {
		AssistantMessage assistantMessage = response.getResult().getOutput();
		ToolResponseMessage toolMessageResponse = this.executeFuncitons(assistantMessage);
		return this.buildToolCallConversation(prompt.getInstructions(), assistantMessage, toolMessageResponse);
	}

	protected List<Message> buildToolCallConversation(List<Message> previousMessages, AssistantMessage assistantMessage,
			ToolResponseMessage toolResponseMessage) {
		List<Message> messages = new ArrayList<>(previousMessages);
		messages.add(assistantMessage);
		messages.add(toolResponseMessage);
		return messages;
	}

	/**
	 * Resolve the function callbacks by name. Retrieve them from the registry or try to
	 * resolve them from the Application Context.
	 * @param functionNames Name of function callbacks to retrieve.
	 * @return list of resolved FunctionCallbacks.
	 */
	protected List<FunctionCallback> resolveFunctionCallbacks(Set<String> functionNames) {

		List<FunctionCallback> retrievedFunctionCallbacks = new ArrayList<>();

		for (String functionName : functionNames) {
			if (!this.functionCallbackRegister.containsKey(functionName)) {

				if (this.functionCallbackContext != null) {
					FunctionCallback functionCallback = this.functionCallbackContext.getFunctionCallback(functionName,
							null);
					if (functionCallback != null) {
						this.functionCallbackRegister.put(functionName, functionCallback);
					}
					else {
						throw new IllegalStateException(
								"No function callback [" + functionName + "] fund in tht FunctionCallbackContext");
					}
				}
				else {
					throw new IllegalStateException("No function callback found for name: " + functionName);
				}
			}
			FunctionCallback functionCallback = this.functionCallbackRegister.get(functionName);

			retrievedFunctionCallbacks.add(functionCallback);
		}

		return retrievedFunctionCallbacks;
	}

	protected ToolResponseMessage executeFuncitons(AssistantMessage assistantMessage) {

		List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

		for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

			var functionName = toolCall.name();
			String functionArguments = toolCall.arguments();

			if (!this.functionCallbackRegister.containsKey(functionName)) {
				throw new IllegalStateException("No function callback found for function name: " + functionName);
			}

			String functionResponse = this.functionCallbackRegister.get(functionName).call(functionArguments);

			toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), functionName, functionResponse));
		}

		return new ToolResponseMessage(toolResponses, Map.of());
	}

	protected boolean isToolCall(ChatResponse chatResponse, Set<String> toolCallFinishReasons) {
		Assert.isTrue(!CollectionUtils.isEmpty(toolCallFinishReasons), "Tool call finish reasons cannot be empty!");

		if (chatResponse == null) {
			return false;
		}

		var generations = chatResponse.getResults();
		if (CollectionUtils.isEmpty(generations)) {
			return false;
		}

		var generation = generations.get(0);
		return !CollectionUtils.isEmpty(generation.getOutput().getToolCalls()) && toolCallFinishReasons.stream()
			.map(s -> s.toLowerCase())
			.toList()
			.contains(generation.getMetadata().getFinishReason().toLowerCase());
	}

}
