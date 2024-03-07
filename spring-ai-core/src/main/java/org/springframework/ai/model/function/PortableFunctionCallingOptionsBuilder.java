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

import java.util.List;
import java.util.Set;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;

/**
 * Builder for {@link PortableFunctionCallingOptions}. Using the
 * {@link PortableFunctionCallingOptions} permits options portability between different AI
 * providers that support function-calling.
 *
 * @author youngmon
 * @author Christian Tzolov
 * @since 0.8.1
 */
public class PortableFunctionCallingOptionsBuilder {

	private FunctionCallingOptionsBuilder functionCallingOptionBuilder = FunctionCallingOptionsBuilder.builder();

	private ChatOptionsBuilder chatOptionsBuilder = ChatOptionsBuilder.builder();

	private PortableFunctionCallingOptionsBuilder() {
	}

	/**
	 * Creates a new {@link PortableFunctionCallingOptionsBuilder} instance.
	 * @return A new instance of PortableFunctionCallingOptionsBuilder.
	 */
	public static PortableFunctionCallingOptionsBuilder builder() {
		return new PortableFunctionCallingOptionsBuilder();
	}

	/**
	 * Initializes a new {@link PortableFunctionCallingOptionsBuilder} with settings from
	 * an existing {@link PortableFunctionCallingOptions} object.
	 * @param options The PortableFunctionCallingOptions object whose settings are to be
	 * used.
	 * @return A PortableFunctionCallingOptionsBuilder instance initialized with the
	 * provided PortableFunctionCallingOptions settings.
	 */
	public static PortableFunctionCallingOptionsBuilder builder(final PortableFunctionCallingOptions options) {
		return builder().withChatOptions(options).withFunctionCallingOptions(options);
	}

	public PortableFunctionCallingOptions build() {
		return new PortableFunctionCallingOptionsImpl(this.functionCallingOptionBuilder.build(),
				this.chatOptionsBuilder.build());
	}

	public PortableFunctionCallingOptionsBuilder withFunctionCallbacks(final List<FunctionCallback> functionCallbacks) {
		this.functionCallingOptionBuilder.withFunctionCallbacks(functionCallbacks);
		return this;
	}

	public PortableFunctionCallingOptionsBuilder withFunctionCallback(final FunctionCallback functionCallback) {
		this.functionCallingOptionBuilder.withFunctionCallback(functionCallback);
		return this;
	}

	public PortableFunctionCallingOptionsBuilder withFunctions(final Set<String> functions) {
		this.functionCallingOptionBuilder.withFunctions(functions);
		return this;
	}

	public PortableFunctionCallingOptionsBuilder withFunction(final String function) {
		this.functionCallingOptionBuilder.withFunction(function);
		return this;
	}

	public PortableFunctionCallingOptionsBuilder withTemperature(final Float temperature) {
		this.chatOptionsBuilder.withTemperature(temperature);
		return this;
	}

	public PortableFunctionCallingOptionsBuilder withTopP(final Float topP) {
		this.chatOptionsBuilder.withTopP(topP);
		return this;
	}

	public PortableFunctionCallingOptionsBuilder withTopK(final Integer topK) {
		this.chatOptionsBuilder.withTopK(topK);
		return this;
	}

	public PortableFunctionCallingOptionsBuilder withChatOptions(final ChatOptions options) {
		this.chatOptionsBuilder = ChatOptionsBuilder.builder(options);
		return this;
	}

	public PortableFunctionCallingOptionsBuilder withFunctionCallingOptions(final FunctionCallingOptions options) {
		this.functionCallingOptionBuilder = FunctionCallingOptionsBuilder.builder(options);
		return this;
	}

	private class PortableFunctionCallingOptionsImpl implements PortableFunctionCallingOptions {

		private final FunctionCallingOptions functionCallingOptions;

		private final ChatOptions chatOptions;

		PortableFunctionCallingOptionsImpl(final FunctionCallingOptions functionCallingOptions,
				final ChatOptions chatOptions) {
			this.functionCallingOptions = functionCallingOptions;
			this.chatOptions = chatOptions;
		}

		@Override
		public List<FunctionCallback> getFunctionCallbacks() {
			return this.functionCallingOptions.getFunctionCallbacks();
		}

		@Override
		public Set<String> getFunctions() {
			return this.functionCallingOptions.getFunctions();
		}

		@Override
		public Float getTemperature() {
			return this.chatOptions.getTemperature();
		}

		@Override
		public Float getTopP() {
			return this.chatOptions.getTopP();
		}

		@Override
		public Integer getTopK() {
			return this.chatOptions.getTopK();
		}

	}

}