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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback.Builder;
import org.springframework.ai.model.function.FunctionCallback.FunctionInvokerBuilder;
import org.springframework.ai.model.function.FunctionCallback.MethodInvokerBuilder;
import org.springframework.ai.model.function.FunctionCallbackContext.SchemaType;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */

public class DefaultFunctionCallbackBuilder implements FunctionCallback.Builder {

	private String description;

	private SchemaType schemaType = SchemaType.JSON_SCHEMA;

	// By default the response is converted to a JSON string.
	private Function<?, String> responseConverter = ModelOptionsUtils::toJsonString;

	// optional
	private String inputTypeSchema;

	private ObjectMapper objectMapper = JsonMapper.builder()
		.addModules(JacksonUtils.instantiateAvailableModules())
		.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
		.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
		.build();

	@Override
	public Builder description(String description) {
		Assert.hasText(description, "Description must not be empty");
		this.description = description;
		return this;
	}

	@Override
	public Builder schemaType(SchemaType schemaType) {
		Assert.notNull(schemaType, "SchemaType must not be null");
		this.schemaType = schemaType;
		return this;
	}

	@Override
	public Builder responseConverter(Function<?, String> responseConverter) {
		Assert.notNull(responseConverter, "ResponseConverter must not be null");
		this.responseConverter = responseConverter;
		return this;
	}

	@Override
	public Builder inputTypeSchema(String inputTypeSchema) {
		Assert.hasText(inputTypeSchema, "InputTypeSchema must not be empty");
		this.inputTypeSchema = inputTypeSchema;
		return this;
	}

	@Override
	public Builder objectMapper(ObjectMapper objectMapper) {
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		this.objectMapper = objectMapper;
		return this;
	}

	@Override
	public <I, O> FunctionInvokerBuilder<I, O> function(Function<I, O> function) {
		return new FunctionInvokerBuilderImpl<>(function);
	}

	@Override
	public <O> FunctionInvokerBuilder<?, O> supplier(Supplier<O> function) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'supplier'");
	}

	@Override
	public <I> FunctionInvokerBuilder<I, ?> consumer(Consumer<I> consumer) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'consumer'");
	}

	@Override
	public <I, O> FunctionInvokerBuilder<I, O> function(BiFunction<I, ToolContext, O> biFunction) {
		return new FunctionInvokerBuilderImpl<>(biFunction);
	}

	@Override
	public MethodInvokerBuilder method(String methodName) {
		Assert.hasText(methodName, "Method name must not be empty");
		return new MethodInvokerBuilderImpl(methodName);
	}

	public class FunctionInvokerBuilderImpl<I, O> implements FunctionInvokerBuilder<I, O> {

		private String name;

		private Type inputType;

		private final BiFunction<I, ToolContext, O> biFunction;

		private final Function<I, O> function;

		private FunctionInvokerBuilderImpl(BiFunction<I, ToolContext, O> biFunction) {
			this.biFunction = biFunction;
			this.function = null;
		}

		private FunctionInvokerBuilderImpl(Function<I, O> function) {
			this.biFunction = null;
			this.function = function;
		}

		@Override
		public FunctionInvokerBuilder<I, O> name(String name) {
			Assert.hasText(name, "Name must not be empty");
			this.name = name;
			return this;
		}

		@Override
		public FunctionInvokerBuilder<I, O> inputType(Class<?> inputType) {
			Assert.notNull(inputType, "InputType must not be null");
			this.inputType = inputType;
			return this;
		}

		@Override
		public FunctionInvokerBuilder<I, O> inputType(ParameterizedTypeReference<?> inputType) {
			Assert.notNull(inputType, "InputType must not be null");
			this.inputType = inputType.getType();
			;
			return this;
		}

		@Override
		public FunctionCallback build() {

			Assert.hasText(description, "Description must not be empty");
			Assert.notNull(objectMapper, "ObjectMapper must not be null");
			Assert.hasText(this.name, "Name must not be empty");
			Assert.notNull(responseConverter, "ResponseConverter must not be null");
			Assert.notNull(this.inputType, "InputType must not be null");

			if (inputTypeSchema == null) {
				boolean upperCaseTypeValues = schemaType == SchemaType.OPEN_API_SCHEMA;
				inputTypeSchema = ModelOptionsUtils.getJsonSchema(this.inputType, upperCaseTypeValues);
			}

			BiFunction<I, ToolContext, O> finalBiFunction = (this.biFunction != null) ? this.biFunction
					: (request, context) -> this.function.apply(request);

			return new FunctionCallbackWrapper(this.name, description, inputTypeSchema, this.inputType,
					(Function<I, String>) responseConverter, objectMapper, finalBiFunction);
		}

	}

	public class MethodInvokerBuilderImpl implements FunctionCallback.MethodInvokerBuilder {

		private final String methodName;

		private Class<?> targetClass;

		private Object targetObject;

		private Class<?>[] arguments = new Class[0];

		private MethodInvokerBuilderImpl(String methodName) {
			this.methodName = methodName;
		}

		public MethodInvokerBuilder targetClass(Class<?> targetClass) {
			Assert.notNull(targetClass, "Target class must not be null");
			this.targetClass = targetClass;
			return this;
		}

		@Override
		public MethodInvokerBuilder targetObject(Object methodObject) {
			Assert.notNull(methodObject, "Method object must not be null");
			this.targetObject = methodObject;
			this.targetClass = methodObject.getClass();
			return this;
		}

		@Override
		public MethodInvokerBuilder argumentTypes(Class<?>... arguments) {
			Assert.notNull(arguments, "Arguments must not be null");
			this.arguments = arguments;
			return this;
		}

		@Override
		public FunctionCallback build() {
			Assert.isTrue(this.targetClass != null || this.targetObject != null,
					"Target class or object must not be null");
			var method = ReflectionUtils.findMethod(targetClass, methodName, arguments);
			return new MethodFunctionCallback(this.targetObject, method, description, objectMapper);
		}

	}

}
