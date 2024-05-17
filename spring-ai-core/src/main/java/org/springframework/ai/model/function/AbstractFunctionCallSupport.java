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
package org.springframework.ai.model.function;

import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Christian Tzolov
 * @author Grogdunn
 */
public abstract class AbstractFunctionCallSupport<Msg, Req, Resp> {

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

	protected AbstractFunctionCallSupport(FunctionCallbackContext functionCallbackContext) {
		this.functionCallbackContext = functionCallbackContext;
	}

	public Map<String, FunctionCallback> getFunctionCallbackRegister() {
		return this.functionCallbackRegister;
	}

	protected Set<String> handleFunctionCallbackConfigurations(FunctionCallingOptions options, boolean isRuntimeCall) {

		Set<String> functionToCall = new HashSet<>();

		if (options != null) {
			if (!CollectionUtils.isEmpty(options.getFunctionCallbacks())) {
				options.getFunctionCallbacks().forEach(functionCallback -> {
					// Register the tool callback.
					if (isRuntimeCall) {
						this.functionCallbackRegister.put(functionCallback.getName(), functionCallback);
						// Automatically enable the function, usually from prompt
						// callback.
						functionToCall.add(functionCallback.getName());
					}
					else {
						this.functionCallbackRegister.putIfAbsent(functionCallback.getName(), functionCallback);
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

	///
	protected Resp callWithFunctionSupport(Req request) {
		Resp response = this.doChatCompletion(request);
		return this.handleFunctionCallOrReturn(request, response);
	}

	protected Resp handleFunctionCallOrReturn(Req request, Resp response) {

		if (!this.isToolFunctionCall(response)) {
			return response;
		}

		// The chat completion tool call requires the complete conversation
		// history. Including the initial user message.
		List<Msg> conversationHistory = new ArrayList<>();

		conversationHistory.addAll(this.doGetUserMessages(request));

		Msg responseMessage = this.doGetToolResponseMessage(response);

		// Add the assistant response to the message conversation history.
		conversationHistory.add(responseMessage);

		Req newRequest = this.doCreateToolResponseRequest(request, responseMessage, conversationHistory);

		if (!this.hasReturningFunction(responseMessage)) {
			return response;
		}
		return this.callWithFunctionSupport(newRequest);
	}

	protected Flux<Resp> callWithFunctionSupportStream(Req request) {
		final Flux<Resp> response = this.doChatCompletionStream(request);
		return this.handleFunctionCallOrReturnStream(request, response);
	}

	protected Flux<Resp> handleFunctionCallOrReturnStream(Req request, Flux<Resp> response) {

		return response.switchMap(resp -> {
			if (!this.isToolFunctionCall(resp)) {
				return Mono.just(resp);
			}

			// The chat completion tool call requires the complete conversation
			// history. Including the initial user message.
			List<Msg> conversationHistory = new ArrayList<>();

			conversationHistory.addAll(this.doGetUserMessages(request));

			Msg responseMessage = this.doGetToolResponseMessage(resp);

			// Add the assistant response to the message conversation history.
			conversationHistory.add(responseMessage);

			Req newRequest = this.doCreateToolResponseRequest(request, responseMessage, conversationHistory);

			return this.callWithFunctionSupportStream(newRequest);
		});

	}

	abstract protected boolean hasReturningFunction(Msg responseMessage);

	abstract protected Req doCreateToolResponseRequest(Req previousRequest, Msg responseMessage,
			List<Msg> conversationHistory);

	abstract protected List<Msg> doGetUserMessages(Req request);

	abstract protected Msg doGetToolResponseMessage(Resp response);

	abstract protected Resp doChatCompletion(Req request);

	abstract protected Flux<Resp> doChatCompletionStream(Req request);

	abstract protected boolean isToolFunctionCall(Resp response);

}
