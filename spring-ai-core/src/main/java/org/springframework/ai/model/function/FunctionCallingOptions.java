/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.function;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * FunctionCallingOptions is a set of options that can be used to configure the function
 * calling behavior of the ChatModel.
 *
 * @author Christian Tzolov
 * @author Ilayaperumal Gopinathan
 */
public interface FunctionCallingOptions extends ChatOptions {

	/**
	 * @return Returns {@link DefaultFunctionCallingOptionsBuilder} to create a new
	 * instance of {@link FunctionCallingOptions}.
	 */
	static FunctionCallingOptions.Builder builder() {
		return new DefaultFunctionCallingOptionsBuilder();
	}

	/**
	 * Function Callbacks to be registered with the ChatModel. For Prompt Options the
	 * functionCallbacks are automatically enabled for the duration of the prompt
	 * execution. For Default Options the FunctionCallbacks are registered but disabled by
	 * default. You have to use "functions" property to list the function names from the
	 * ChatModel registry to be used in the chat completion requests.
	 * @return Return the Function Callbacks to be registered with the ChatModel.
	 */
	List<FunctionCallback> getFunctionCallbacks();

	/**
	 * Set the Function Callbacks to be registered with the ChatModel.
	 * @param functionCallbacks the Function Callbacks to be registered with the
	 * ChatModel.
	 */
	void setFunctionCallbacks(List<FunctionCallback> functionCallbacks);

	/**
	 * @return {@link Set} of function names from the ChatModel registry to be used in the
	 * next chat completion requests.
	 */
	Set<String> getFunctions();

	/**
	 * Set the list of function names from the ChatModel registry to be used in the next
	 * chat completion requests.
	 * @param functions the list of function names from the ChatModel registry to be used
	 * in the next chat completion requests.
	 */
	void setFunctions(Set<String> functions);

	default Boolean getProxyToolCalls() {
		return false;
	}

	default void setProxyToolCalls(Boolean proxyToolCalls) {
		if (proxyToolCalls != null) {
			throw new UnsupportedOperationException("Setting Proxy Tool Calls are not supported!");
		}
	}

	Map<String, Object> getToolContext();

	void setToolContext(Map<String, Object> tooContext);

	/**
	 * Builder for creating {@link FunctionCallingOptions} instance.
	 */
	interface Builder extends ChatOptions.Builder {

		/**
		 * The list of Function Callbacks to be registered with the Chat model.
		 * @param functionCallbacks the list of Function Callbacks.
		 * @return the FunctionCallOptions Builder.
		 */
		Builder functionCallbacks(List<FunctionCallback> functionCallbacks);

		/**
		 * The Function Callbacks to be registered with the Chat model.
		 * @param functionCallbacks the function callbacks.
		 * @return the FunctionCallOptions Builder.
		 */
		Builder functionCallbacks(FunctionCallback... functionCallbacks);

		/**
		 * {@link Set} of function names to be registered with the Chat model.
		 * @param functions the {@link Set} of function names
		 * @return the FunctionCallOptions Builder.
		 */
		Builder functions(Set<String> functions);

		/**
		 * The function name to be registered with the chat model.
		 * @param function the name of the function.
		 * @return the FunctionCallOptions Builder.
		 */
		Builder function(String function);

		/**
		 * Boolean flag to indicate if the proxy ToolCalls is enabled.
		 * @param proxyToolCalls boolean value to enable proxy ToolCalls.
		 * @return the FunctionCallOptions Builder.
		 */
		Builder proxyToolCalls(Boolean proxyToolCalls);

		/**
		 * Add a {@link Map} of context values into tool context.
		 * @param context the map representing the tool context.
		 * @return the FunctionCallOptions Builder.
		 */
		Builder toolContext(Map<String, Object> context);

		/**
		 * Add a specific key/value pair to the tool context.
		 * @param key the key to use.
		 * @param value the corresponding value.
		 * @return the FunctionCallOptions Builder.
		 */
		Builder toolContext(String key, Object value);

		/**
		 * Builds the {@link FunctionCallingOptions}.
		 * @return the FunctionCalling options.
		 */
		@Override
		FunctionCallingOptions build();

		// Override all ChatOptions.Builder methods to return
		// FunctionCallingOptions.Builder
		@Override
		Builder model(String model);

		@Override
		Builder frequencyPenalty(Double frequencyPenalty);

		@Override
		Builder maxTokens(Integer maxTokens);

		@Override
		Builder presencePenalty(Double presencePenalty);

		@Override
		Builder stopSequences(List<String> stopSequences);

		@Override
		Builder temperature(Double temperature);

		@Override
		Builder topK(Integer topK);

		@Override
		Builder topP(Double topP);

		/**
		 * @deprecated Use {@link #model(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withModel(String model) {
			return model(model);
		}

		/**
		 * @deprecated Use {@link #frequencyPenalty(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withFrequencyPenalty(Double frequencyPenalty) {
			return frequencyPenalty(frequencyPenalty);
		}

		/**
		 * @deprecated Use {@link #maxTokens(Integer)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withMaxTokens(Integer maxTokens) {
			return maxTokens(maxTokens);
		}

		/**
		 * @deprecated Use {@link #presencePenalty(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withPresencePenalty(Double presencePenalty) {
			return presencePenalty(presencePenalty);
		}

		/**
		 * @deprecated Use {@link #stopSequences(List)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withStopSequences(List<String> stopSequences) {
			return stopSequences(stopSequences);
		}

		/**
		 * @deprecated Use {@link #temperature(Double)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withTemperature(Double temperature) {
			return temperature(temperature);
		}

		/**
		 * @deprecated Use {@link #functionCallbacks(List)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
			return functionCallbacks(functionCallbacks);
		}

		/**
		 * @deprecated Use {@link #functionCallbacks(FunctionCallback...)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withFunctionCallbacks(FunctionCallback... functionCallbacks) {
			return functionCallbacks(functionCallbacks);
		}

		/**
		 * @deprecated Use {@link #functions(Set)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withFunctions(Set<String> functions) {
			return functions(functions);
		}

		/**
		 * @deprecated Use {@link #function(String)} instead.
		 */
		@Deprecated(forRemoval = true, since = "1.0.0-M5")
		default Builder withFunction(String function) {
			return function(function);
		}

	}

}
