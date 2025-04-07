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

package org.springframework.ai.model.function;

import java.lang.reflect.Type;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.Assert;

/**
 * Abstract implementation of the {@link FunctionCallback} for interacting with the
 * Model's function calling protocol and a {@link Function} wrapping the interaction with
 * the 3rd party service/function.
 *
 * Implement the {@code O apply(I request) } method to implement the interaction with the
 * 3rd party service/function.
 *
 * The {@link #responseConverter} function is responsible to convert the 3rd party
 * function's output type into a string expected by the LLM model.
 *
 * @param <I> the 3rd party service input type.
 * @param <O> the 3rd party service output type.
 * @author Christian Tzolov
 * @deprecated in favor of {@link FunctionToolCallback}.
 */
@Deprecated
abstract class AbstractFunctionCallback<I, O> implements BiFunction<I, ToolContext, O>, FunctionCallback {

	private final String name;

	private final String description;

	private final Type inputType;

	private final String inputTypeSchema;

	private final ObjectMapper objectMapper;

	private final Function<O, String> responseConverter;

	/**
	 * Constructs a new {@link AbstractFunctionCallback} with the given name, description,
	 * input type and default object mapper.
	 * @param name Function name. Should be unique within the ChatModel's function
	 * registry.
	 * @param description Function description. Used as a "system prompt" by the model to
	 * decide if the function should be called.
	 * @param inputTypeSchema Used to compute, the argument's Schema (such as JSON Schema
	 * or OpenAPI Schema)required by the Model's function calling protocol.
	 * @param inputType Used to compute, the argument's JSON schema required by the
	 * Model's function calling protocol.
	 * @param responseConverter Used to convert the function's output type to a string.
	 * @param objectMapper Used to convert the function's input and output types to and
	 * from JSON.
	 */
	protected AbstractFunctionCallback(String name, String description, String inputTypeSchema, Type inputType,
			Function<O, String> responseConverter, ObjectMapper objectMapper) {
		Assert.notNull(name, "Name must not be null");
		Assert.notNull(description, "Description must not be null");
		Assert.notNull(inputType, "InputType must not be null");
		Assert.notNull(inputTypeSchema, "InputTypeSchema must not be null");
		Assert.notNull(responseConverter, "ResponseConverter must not be null");
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		this.name = name;
		this.description = description;
		this.inputType = inputType;
		this.inputTypeSchema = inputTypeSchema;
		this.responseConverter = responseConverter;
		this.objectMapper = objectMapper;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	@Override
	public String getInputTypeSchema() {
		return this.inputTypeSchema;
	}

	@Override
	public String call(String functionInput, ToolContext toolContext) {
		I request = fromJson(functionInput, this.inputType);
		O response = this.apply(request, toolContext);
		return this.responseConverter.apply(response);
	}

	@Override
	public String call(String functionArguments) {
		// Convert the tool calls JSON arguments into a Java function request object.
		I request = fromJson(functionArguments, this.inputType);
		// extend conversation with function response.
		return this.andThen(this.responseConverter).apply(request, null);
	}

	private <T> T fromJson(String json, Type targetType) {
		try {
			return this.objectMapper.readValue(json, this.objectMapper.constructType(targetType));
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.description, this.inputType);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		AbstractFunctionCallback other = (AbstractFunctionCallback) obj;

		return Objects.equals(this.name, other.name) && Objects.equals(this.description, other.description)
				&& Objects.equals(this.inputType, other.inputType);

	}

}
