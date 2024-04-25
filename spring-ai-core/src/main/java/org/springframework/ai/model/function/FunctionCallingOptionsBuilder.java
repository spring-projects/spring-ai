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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

/**
 * Builder for {@link FunctionCallingOptions}. This builder creates option objects
 * required for function-calling.
 *
 * @author youngmon
 * @since 0.8.1
 */
public class FunctionCallingOptionsBuilder {

	private final List<FunctionCallback> functionCallbacks = new ArrayList<>();

	private final Set<String> functions = new HashSet<>();

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
	 * existing {@link FunctionCallingOptions} object.
	 * @param options The FunctionCallingOptions object whose settings are to be used.
	 * @return A FunctionCallingOptionsBuilder instance initialized with the provided
	 * FunctionCallingOptions settings.
	 */
	public static FunctionCallingOptionsBuilder builder(final FunctionCallingOptions options) {
		return builder().withFunctions(options.getFunctions()).withFunctionCallbacks(options.getFunctionCallbacks());
	}

	public FunctionCallingOptions build() {
		return new FunctionCallingOptionsImpl(this.functionCallbacks, this.functions);
	}

	public FunctionCallingOptionsBuilder withFunctionCallbacks(
			@NonNull final List<FunctionCallback> functionCallbacks) {
		Assert.notNull(functionCallbacks, "FunctionCallback must not be null");
		this.functionCallbacks.addAll(functionCallbacks);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunctionCallback(@NonNull final FunctionCallback functionCallback) {
		Assert.notNull(functionCallback, "FunctionCallback must not be null");
		this.functionCallbacks.add(functionCallback);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunctions(@NonNull final Set<String> functions) {
		Assert.notNull(functions, "Functions must not be null");
		this.functions.addAll(functions);
		return this;
	}

	public FunctionCallingOptionsBuilder withFunction(@NonNull final String function) {
		Assert.notNull(function, "Function must not be null");
		this.functions.add(function);
		return this;
	}

	private static class FunctionCallingOptionsImpl implements FunctionCallingOptions {

		private final List<FunctionCallback> functionCallbacks;

		private final Set<String> functions;

		FunctionCallingOptionsImpl(final List<FunctionCallback> functionCallbacks, final Set<String> functions) {
			this.functionCallbacks = functionCallbacks;
			this.functions = functions;
		}

		@Override
		public List<FunctionCallback> getFunctionCallbacks() {
			return this.functionCallbacks;
		}

		@Override
		public Set<String> getFunctions() {
			return this.functions;
		}

	}

}
