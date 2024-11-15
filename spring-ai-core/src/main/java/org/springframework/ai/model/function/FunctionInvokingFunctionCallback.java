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

import java.lang.reflect.Type;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.util.Assert;

/**
 * Note that the underlying function is responsible for converting the output into format
 * that can be consumed by the Model. The default implementation converts the output into
 * String before sending it to the Model. Provide a custom function responseConverter
 * implementation to override this.
 *
 * @author Christian Tzolov
 */
public final class FunctionInvokingFunctionCallback<I, O> extends AbstractFunctionCallback<I, O> {

	private final BiFunction<I, ToolContext, O> biFunction;

	FunctionInvokingFunctionCallback(String name, String description, String inputTypeSchema, Type inputType,
			Function<O, String> responseConverter, ObjectMapper objectMapper, BiFunction<I, ToolContext, O> function) {
		super(name, description, inputTypeSchema, inputType, responseConverter, objectMapper);
		Assert.notNull(function, "Function must not be null");
		this.biFunction = function;
	}

	@Override
	public O apply(I input, ToolContext context) {
		return this.biFunction.apply(input, context);
	}

}
