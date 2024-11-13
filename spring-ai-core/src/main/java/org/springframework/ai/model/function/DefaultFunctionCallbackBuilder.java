/*
* Copyright 2024 - 2024 the original author or authors.
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

import java.lang.reflect.Type;
import java.util.function.BiFunction;
import java.util.function.Function;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback.Builder;
import org.springframework.ai.model.function.FunctionCallbackContext.SchemaType;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class DefaultFunctionCallbackBuilder<I, O> implements FunctionCallback.Builder<I, O> {

	private final Function<I, O> function;

	private final BiFunction<I, ToolContext, O> biFunction;

	private String name;

	private String description;

	private Type inputType;

	private SchemaType schemaType = SchemaType.JSON_SCHEMA;

	// By default the response is converted to a JSON string.
	private Function<O, String> responseConverter = ModelOptionsUtils::toJsonString;

	// optional
	private String inputTypeSchema;

	private ObjectMapper objectMapper = JsonMapper.builder()
		.addModules(JacksonUtils.instantiateAvailableModules())
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
		.build();

	public DefaultFunctionCallbackBuilder(Function<I, O> function) {
		this.function = function;
		this.biFunction = null;
	}

	public DefaultFunctionCallbackBuilder(BiFunction<I, ToolContext, O> biFunction) {
		this.function = null;
		this.biFunction = biFunction;
	}

	@Override
	public Builder<I, O> name(String name) {
		Assert.hasText(name, "Name must not be empty");
		this.name = name;
		return this;
	}

	@Override
	public Builder<I, O> description(String description) {
		Assert.hasText(description, "Description must not be empty");
		this.description = description;
		return this;
	}

	@Override
	public Builder<I, O> inputType(Class<?> inputType) {
		Assert.notNull(inputType, "InputType must not be null");
		this.inputType = inputType;
		return this;
	}

	@Override
	public Builder<I, O> inputType(ParameterizedTypeReference<?> inputType) {
		Assert.notNull(inputType, "InputType must not be null");
		this.inputType = inputType.getType();
		return this;
	}

	@Override
	public Builder<I, O> schemaType(SchemaType schemaType) {
		Assert.notNull(schemaType, "SchemaType must not be null");
		this.schemaType = schemaType;
		return this;
	}

	@Override
	public Builder<I, O> responseConverter(Function<O, String> responseConverter) {
		Assert.notNull(responseConverter, "ResponseConverter must not be null");
		this.responseConverter = responseConverter;
		return this;
	}

	@Override
	public Builder<I, O> inputTypeSchema(String inputTypeSchema) {
		Assert.hasText(inputTypeSchema, "InputTypeSchema must not be empty");
		this.inputTypeSchema = inputTypeSchema;
		return this;
	}

	@Override
	public Builder<I, O> objectMapper(ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		this.objectMapper = objectMapper;
		return this;
	}

	@Override
	public FunctionCallback build() {

		Assert.hasText(this.description, "Description must not be empty");
		Assert.notNull(this.objectMapper, "ObjectMapper must not be null");
		Assert.hasText(this.name, "Name must not be empty");
		Assert.notNull(this.responseConverter, "ResponseConverter must not be null");
		Assert.notNull(this.inputType, "InputType must not be null");

		if (this.inputTypeSchema == null) {
			boolean upperCaseTypeValues = this.schemaType == SchemaType.OPEN_API_SCHEMA;
			this.inputTypeSchema = ModelOptionsUtils.getJsonSchema(this.inputType, upperCaseTypeValues);
		}

		BiFunction<I, ToolContext, O> finalBiFunction = (this.biFunction != null) ? this.biFunction
				: (request, context) -> this.function.apply(request);

		return new FunctionCallbackWrapper<>(this.name, this.description, this.inputTypeSchema, this.inputType,
				this.responseConverter, this.objectMapper, finalBiFunction);
	}

}
