/*
 * Copyright 2024-2024 the original author or authors.
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.util.Assert;

/**
 * Builder for {@link FunctionCallingOptions}. Using the {@link FunctionCallingOptions}
 * permits options portability between different AI providers that support
 * function-calling.
 *
 * @author Christian Tzolov
 * @since 0.8.1
 */
public class FunctionCallingOptionsBuilder {

	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	private Set<String> functions = new HashSet<>();

	private ChatOptionsBuilder chatOptionsBuilder = ChatOptionsBuilder.builder();

	private FunctionCallingOptionsBuilder() {
	}

	/**
	 * Creates a new {@link FunctionCallingOptionsBuilder} instance.
	 * @return A new instance of FunctionCallingOptionsBuilder.
	 */
	public static FunctionCallingOptionsBuilder builder() {
		return new FunctionCallingOptionsBuilder();
	}

	/**
	 * Initializes a new {@link FunctionCallingOptionsBuilder} with settings from an
	 * existing {@link ChatOptions} object. This allows for creating a new
	 * FunctionCallingOptions object based on the settings of an existing ChatOptions
	 * instance.
	 * @param options The ChatOptions object whose settings are to be used.
	 * @return A FunctionCallingOptionsBuilder instance initialized with the provided
	 * ChatOptions settings.
	 */
	public static FunctionCallingOptionsBuilder builder(ChatOptions options) {
		return builder().withTopK(options.getTopK())
			.withTopP(options.getTopP())
			.withTemperature(options.getTemperature());
	}

	/**
	 * Initializes a new {@link FunctionCallingOptionsBuilder} with settings from an
	 * existing {@link FunctionCallingOptions} object. This method is useful for
	 * transferring settings between different instances of FunctionCallingOptions,
	 * including function callbacks and functions.
	 * @param options The PortableFunctionCallingOptions object whose settings are to be
	 * used.
	 * @return A FunctionCallingOptionsBuilder instance initialized with the provided
	 * PortableFunctionCallingOptions settings.
	 */
	public static FunctionCallingOptionsBuilder builder(FunctionCallingOptions options) {
		return builder().withTopK(options.getTopK())
			.withTopP(options.getTopP())
			.withTemperature(options.getTemperature())
			.withFunctions(options.getFunctions())
			.withFunctionCallbacks(options.getFunctionCallbacks());
	}

	public FunctionCallingOptionsBuilder withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		Assert.notNull(functionCallbacks, "FunctionCallbacks must not be null");
		this.functionCallbacks.addAll(functionCallbacks);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunctionCallback(FunctionCallback functionCallback) {
		Assert.notNull(functionCallback, "FunctionCallback must not be null");
		this.functionCallbacks.add(functionCallback);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunctions(Set<String> functions) {
		Assert.notNull(functions, "Functions must not be null");
		this.functions.addAll(functions);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunction(String function) {
		Assert.notNull(function, "Function must not be null");
		this.functions.add(function);
		return this;
	}

	public FunctionCallingOptionsBuilder withTemperature(Float temperature) {
		this.chatOptionsBuilder.withTemperature(temperature);
		return this;
	}

	public FunctionCallingOptionsBuilder withTopP(Float topP) {
		this.chatOptionsBuilder.withTopP(topP);
		return this;
	}

	public FunctionCallingOptionsBuilder withTopK(Integer topK) {
		this.chatOptionsBuilder.withTopK(topK);
		return this;
	}

	public PortableFunctionCallingOptions build() {
		return new PortableFunctionCallingOptions(this.functions, this.functionCallbacks,
				this.chatOptionsBuilder.build());
	}

	public class PortableFunctionCallingOptions implements FunctionCallingOptions {

		private final List<FunctionCallback> functionCallbacks;

		private final Set<String> functions;

		private final ChatOptions options;

		PortableFunctionCallingOptions(final Set<String> functions, final List<FunctionCallback> functionCallbacks,
				ChatOptions options) {
			this.functions = functions;
			this.functionCallbacks = functionCallbacks;
			this.options = options;
		}

		/**
		 * Retrieves a list of function callbacks. This method returns a new list
		 * containing all currently set function callbacks. The returned list is a copy,
		 * ensuring that modifications to the returned list do not affect the original
		 * list of function callbacks. This ensures the immutability of the collection
		 * exposed to the clients.
		 * @return An immutable list of {@link FunctionCallback} instances.
		 */
		@Override
		public List<FunctionCallback> getFunctionCallbacks() {
			return new ArrayList<>(this.functionCallbacks);
		}

		/**
		 * Retrieves a set of functions. This method returns a new set containing all
		 * currently set functions. The returned set is a copy, ensuring that
		 * modifications to the returned set do not affect the original set of functions.
		 * This ensures the immutability of the collection exposed to the clients.
		 * @return An immutable set of String representing the functions.
		 */
		@Override
		public Set<String> getFunctions() {
			return new HashSet<>(this.functions);
		}

		@Override
		public Float getTemperature() {
			return this.options.getTemperature();
		}

		@Override
		public Float getTopP() {
			return this.options.getTopP();
		}

		@Override
		public Integer getTopK() {
			return this.options.getTopK();
		}

	}

}