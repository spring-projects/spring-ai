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

import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.util.Assert;

/**
 * Note that the underlying function is responsible for converting the output into format
 * that can be consumed by the Model. The default implementation converts the output into
 * String before sending it to the Model. Provide a custom function responseConverter
 * implementation to override this.
 *
 */
public class FunctionCallbackWrapper<I, O> extends AbstractFunctionCallback<I, O> {

	private final Function<I, O> function;

	private FunctionCallbackWrapper(String name, String description, String inputTypeSchema, Class<I> inputType,
			Class<O> outputType, Function<O, String> responseConverter, ObjectMapper objectMapper,
			Function<I, O> function) {
		super(name, description, inputTypeSchema, inputType, outputType, responseConverter, objectMapper);
		Assert.notNull(function, "Function must not be null");
		this.function = function;
	}

	@SuppressWarnings("unchecked")
	private static <I, O> Class<I> resolveInputType(Function<I, O> function) {
		return (Class<I>) TypeResolverHelper.getFunctionInputClass((Class<Function<I, O>>) function.getClass());
	}

	@SuppressWarnings("unchecked")
	private static <I, O> Class<O> resolveOutputType(Function<I, O> function) {
		return (Class<O>) TypeResolverHelper.getFunctionOutputClass((Class<Function<I, O>>) function.getClass());
	}

	@Override
	public O apply(I input) {
		return this.function.apply(input);
	}

	public static <I, O> Builder<I, O> builder(Function<I, O> function) {
		return new Builder<>(function);
	}

	public static <I> Builder<I, Void> builder(Consumer<I> consumer) {
		final Function<I, Void> adapter = i -> {
			consumer.accept(i);
			return null;
		};
		return new Builder<>(adapter);
	}

	public static class Builder<I, O> {

		public enum SchemaType {

			JSON_SCHEMA, OPEN_API_SCHEMA

		}

		private String name;

		private String description;

		private Class<I> inputType;

		private Class<O> outputType;

		private final Function<I, O> function;

		private SchemaType schemaType = SchemaType.JSON_SCHEMA;

		public Builder(Function<I, O> function) {
			Assert.notNull(function, "Function must not be null");
			this.function = function;
		}

		// By default the response is converted to a JSON string.
		private Function<O, String> responseConverter = (response) -> ModelOptionsUtils.toJsonString(response);

		private String inputTypeSchema;

		private ObjectMapper objectMapper = new ObjectMapper()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
			.registerModule(new JavaTimeModule());

		public Builder<I, O> withName(String name) {
			Assert.hasText(name, "Name must not be empty");
			this.name = name;
			return this;
		}

		public Builder<I, O> withDescription(String description) {
			Assert.hasText(description, "Description must not be empty");
			this.description = description;
			return this;
		}

		@SuppressWarnings("unchecked")
		public Builder<I, O> withInputType(Class<?> inputType) {
			this.inputType = (Class<I>) inputType;
			return this;
		}

		@SuppressWarnings("unchecked")
		public Builder<I, O> withOutputType(Class<?> outputType) {
			this.outputType = (Class<O>) outputType;
			return this;
		}

		public Builder<I, O> withResponseConverter(Function<O, String> responseConverter) {
			Assert.notNull(responseConverter, "ResponseConverter must not be null");
			this.responseConverter = responseConverter;
			return this;
		}

		public Builder<I, O> withInputTypeSchema(String inputTypeSchema) {
			Assert.hasText(inputTypeSchema, "InputTypeSchema must not be empty");
			this.inputTypeSchema = inputTypeSchema;
			return this;
		}

		public Builder<I, O> withObjectMapper(ObjectMapper objectMapper) {
			Assert.notNull(objectMapper, "ObjectMapper must not be null");
			this.objectMapper = objectMapper;
			return this;
		}

		public Builder<I, O> withSchemaType(SchemaType schemaType) {
			Assert.notNull(schemaType, "SchemaType must not be null");
			this.schemaType = schemaType;
			return this;
		}

		public FunctionCallbackWrapper<I, O> build() {

			Assert.hasText(this.name, "Name must not be empty");
			Assert.hasText(this.description, "Description must not be empty");
			Assert.notNull(this.function, "Function must not be null");
			Assert.notNull(this.responseConverter, "ResponseConverter must not be null");
			Assert.notNull(this.objectMapper, "ObjectMapper must not be null");

			if (this.inputType == null) {
				this.inputType = resolveInputType(this.function);
			}
			if (this.outputType == null) {
				this.outputType = resolveOutputType(this.function);
			}

			if (this.inputTypeSchema == null) {
				boolean upperCaseTypeValues = this.schemaType == SchemaType.OPEN_API_SCHEMA;
				this.inputTypeSchema = ModelOptionsUtils.getJsonSchema(this.inputType, upperCaseTypeValues);
			}

			return new FunctionCallbackWrapper<>(this.name, this.description, this.inputTypeSchema, this.inputType,
					this.outputType, this.responseConverter, this.objectMapper, this.function);
		}

	}

}
