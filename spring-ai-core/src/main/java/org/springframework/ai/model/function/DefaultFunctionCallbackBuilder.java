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
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback.FunctionInvokingSpec;
import org.springframework.ai.model.function.FunctionCallback.MethodInvokingSpec;
import org.springframework.ai.model.function.FunctionCallback.SchemaType;
import org.springframework.ai.util.ParsingUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link FunctionCallback.Builder}.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class DefaultFunctionCallbackBuilder implements FunctionCallback.Builder {

	private final static Logger logger = LoggerFactory.getLogger(DefaultFunctionCallbackBuilder.class);

	@Override
	public <I, O> FunctionInvokingSpec<I, O> function(String name, Function<I, O> function) {
		return new DefaultFunctionInvokingSpec<>(name, function);
	}

	@Override
	public <I, O> FunctionInvokingSpec<I, O> function(String name, BiFunction<I, ToolContext, O> biFunction) {
		return new DefaultFunctionInvokingSpec<>(name, biFunction);
	}

	@Override
	public <O> FunctionInvokingSpec<Void, O> function(String name, Supplier<O> supplier) {
		Function<Void, O> function = input -> supplier.get();
		return new DefaultFunctionInvokingSpec<>(name, function).inputType(Void.class);
	}

	public <I> FunctionInvokingSpec<I, Void> function(String name, Consumer<I> consumer) {
		Function<I, Void> function = (I input) -> {
			consumer.accept(input);
			return null;
		};
		return new DefaultFunctionInvokingSpec<>(name, function);
	}

	@Override
	public MethodInvokingSpec method(String methodName, Class<?>... argumentTypes) {
		return new DefaultMethodInvokingSpec(methodName, argumentTypes);
	}

	private String generateDescription(String fromName) {

		String generatedDescription = ParsingUtils.reConcatenateCamelCase(fromName, " ");

		logger.info("Description is not set! A best effort attempt to generate a description:'{}' from the:'{}'",
				generatedDescription, fromName);
		logger.info("It is recommended to set the Description explicitly! Use the 'description()' method!");

		return generatedDescription;
	}

	final class DefaultFunctionInvokingSpec<I, O> extends DefaultCommonCallbackInvokingSpec<FunctionInvokingSpec<I, O>>
			implements FunctionInvokingSpec<I, O> {

		private final String name;

		private Type inputType;

		private final BiFunction<I, ToolContext, O> biFunction;

		private final Function<I, O> function;

		private DefaultFunctionInvokingSpec(String name, BiFunction<I, ToolContext, O> biFunction) {
			Assert.hasText(name, "Name must not be empty");
			Assert.notNull(biFunction, "BiFunction must not be null");
			this.name = name;
			this.biFunction = biFunction;
			this.function = null;
		}

		private DefaultFunctionInvokingSpec(String name, Function<I, O> function) {
			Assert.hasText(name, "Name must not be empty");
			Assert.notNull(function, "Function must not be null");
			this.name = name;
			this.biFunction = null;
			this.function = function;
		}

		@Override
		public FunctionInvokingSpec<I, O> inputType(Class<?> inputType) {
			Assert.notNull(inputType, "InputType must not be null");
			this.inputType = inputType;
			return this;
		}

		@Override
		public FunctionInvokingSpec<I, O> inputType(ParameterizedTypeReference<?> inputType) {
			Assert.notNull(inputType, "InputType must not be null");
			this.inputType = inputType.getType();
			return this;
		}

		@Override
		public FunctionCallback build() {

			Assert.notNull(this.getObjectMapper(), "ObjectMapper must not be null");
			Assert.hasText(this.name, "Name must not be empty");
			Assert.notNull(this.getResponseConverter(), "ResponseConverter must not be null");
			Assert.notNull(this.inputType, "InputType must not be null");

			if (this.getInputTypeSchema() == null) {
				boolean upperCaseTypeValues = schemaType == SchemaType.OPEN_API_SCHEMA;
				this.inputTypeSchema = ModelOptionsUtils.getJsonSchema(this.inputType, upperCaseTypeValues);
			}

			BiFunction<I, ToolContext, O> finalBiFunction = (this.biFunction != null) ? this.biFunction
					: (request, context) -> this.function.apply(request);

			return new FunctionInvokingFunctionCallback<>(this.name, this.getDescriptionExt(),
					this.getInputTypeSchema(), this.inputType, (Function<O, String>) this.getResponseConverter(),
					this.getObjectMapper(), finalBiFunction);
		}

		private String getDescriptionExt() {
			if (StringUtils.hasText(this.getDescription())) {
				return this.getDescription();
			}
			return generateDescription(this.name);
		}

	}

	final class DefaultMethodInvokingSpec extends DefaultCommonCallbackInvokingSpec<MethodInvokingSpec>
			implements FunctionCallback.MethodInvokingSpec {

		private String name;

		private final String methodName;

		private Class<?> targetClass;

		private Object targetObject;

		private final Class<?>[] argumentTypes;

		private DefaultMethodInvokingSpec(String methodName, Class<?>... argumentTypes) {
			Assert.hasText(methodName, "Method name must not be null");
			Assert.notNull(argumentTypes, "Argument types must not be null");
			this.methodName = methodName;
			this.argumentTypes = argumentTypes;
		}

		public MethodInvokingSpec name(String name) {
			Assert.hasText(name, "Name must not be empty");
			this.name = name;
			return this;
		}

		public MethodInvokingSpec targetClass(Class<?> targetClass) {
			Assert.notNull(targetClass, "Target class must not be null");
			this.targetClass = targetClass;
			return this;
		}

		@Override
		public MethodInvokingSpec targetObject(Object methodObject) {
			Assert.notNull(methodObject, "Method object must not be null");
			this.targetObject = methodObject;
			this.targetClass = methodObject.getClass();
			return this;
		}

		@Override
		public FunctionCallback build() {
			Assert.isTrue(this.targetClass != null || this.targetObject != null,
					"Target class or object must not be null");
			var method = ReflectionUtils.findMethod(this.targetClass, this.methodName, this.argumentTypes);
			Assert.notNull(method, "Method: '" + this.methodName + "' with arguments:"
					+ Arrays.toString(this.argumentTypes) + " not found!");
			return new MethodInvokingFunctionCallback(this.targetObject, method, this.getDescriptionExt(),
					this.getObjectMapper(), this.name, this.getResponseConverter());
		}

		private String getDescriptionExt() {
			if (StringUtils.hasText(this.getDescription())) {
				return this.getDescription();
			}

			return generateDescription(StringUtils.hasText(this.name) ? this.name : this.methodName);
		}

	}

}
