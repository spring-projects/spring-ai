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
import org.springframework.ai.model.function.FunctionCallbackContext.SchemaType;
import org.springframework.ai.util.JacksonUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;

/**
 * Note that the underlying function is responsible for converting the output into format
 * that can be consumed by the Model. The default implementation converts the output into
 * String before sending it to the Model. Provide a custom function responseConverter
 * implementation to override this.
 *
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 *
 */
public final class FunctionCallbackWrapper<I, O> extends AbstractFunctionCallback<I, O> {

	private final BiFunction<I, ToolContext, O> biFunction;

	private FunctionCallbackWrapper(String name, String description, String inputTypeSchema,
			ParameterizedTypeReference<I> inputType, Function<O, String> responseConverter, ObjectMapper objectMapper,
			BiFunction<I, ToolContext, O> function) {
		super(name, description, inputTypeSchema, inputType, responseConverter, objectMapper);
		Assert.notNull(function, "Function must not be null");
		this.biFunction = function;
	}

	public static <I, O> Builder<I, O> builder(BiFunction<I, ToolContext, O> biFunction) {
		return new Builder<>(biFunction);
	}

	public static <I, O> Builder<I, O> builder(Function<I, O> function) {
		return new Builder<>(function);
	}

	public static <Void, O> Builder<Void, O> builder(Supplier<O> supplier) {
		Function<Void, O> function = (input) -> supplier.get();
		return new Builder<>(function);
	}

	public static <I, O> Builder<I, O> builder(Consumer<I> consumer) {
		return new Builder<>(consumer);
	}

	@Override
	public O apply(I input, ToolContext context) {
		return this.biFunction.apply(input, context);
	}

	public static class Builder<I, O> {

		private final BiFunction<I, ToolContext, O> biFunction;

		private final Function<I, O> function;

		private final Consumer<I> consumer;

		private String name;

		private String description;

		private ParameterizedTypeReference<I> inputType;

		private SchemaType schemaType = SchemaType.JSON_SCHEMA;

		// By default the response is converted to a JSON string.
		private Function<O, String> responseConverter = ModelOptionsUtils::toJsonString;

		private String inputTypeSchema;

		private ObjectMapper objectMapper;

		public Builder(BiFunction<I, ToolContext, O> biFunction) {
			Assert.notNull(biFunction, "Function must not be null");
			this.biFunction = biFunction;
			this.function = null;
			this.consumer = null;
		}

		public Builder(Function<I, O> function) {
			Assert.notNull(function, "Function must not be null");
			this.biFunction = null;
			this.function = function;
			this.consumer = null;
		}

		public Builder(Consumer<I> consumer) {
			Assert.notNull(consumer, "Consumer must not be null");
			this.biFunction = null;
			this.function = null;
			this.consumer = consumer;
		}

		private static <I, O> ParameterizedTypeReference<I> resolveInputType(BiFunction<I, ToolContext, O> biFunction) {

			ResolvableType rt = TypeResolverHelper.getFunctionArgumentType(ResolvableType.forInstance(biFunction), 0);
			return ParameterizedTypeReference.forType(rt.getType());
		}

		private static <I, O> ParameterizedTypeReference<I> resolveInputType(Function<I, O> function) {
			ResolvableType rt = TypeResolverHelper.getFunctionArgumentType(ResolvableType.forInstance(function), 0);
			return ParameterizedTypeReference.forType(rt.getType());
		}

		private static <I> ParameterizedTypeReference<I> resolveInputType(Consumer<I> consumer) {
			ResolvableType rt = TypeResolverHelper.getFunctionArgumentType(ResolvableType.forInstance(consumer), 0);
			return ParameterizedTypeReference.forType(rt.getType());
		}

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
			this.inputType = ParameterizedTypeReference.forType((Class<I>) inputType);
			return this;
		}

		public Builder<I, O> withInputType(ResolvableType inputType) {
			this.inputType = ParameterizedTypeReference.forType(inputType.getType());
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
			Assert.notNull(this.responseConverter, "ResponseConverter must not be null");

			if (this.objectMapper == null) {
				this.objectMapper = JsonMapper.builder()
					.addModules(JacksonUtils.instantiateAvailableModules())
					.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
					.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
					.build();
			}

			if (this.inputType == null) {
				if (this.function != null) {
					this.inputType = resolveInputType(this.function);
				}
				else if (this.biFunction != null) {
					this.inputType = resolveInputType(this.biFunction);
				}
				else {
					this.inputType = resolveInputType(this.consumer);
				}
			}

			if (this.inputTypeSchema == null) {
				boolean upperCaseTypeValues = this.schemaType == SchemaType.OPEN_API_SCHEMA;
				this.inputTypeSchema = ModelOptionsUtils.getJsonSchema(this.inputType, upperCaseTypeValues);
			}

			BiFunction<I, ToolContext, O> finalBiFunction = null;
			if (this.biFunction != null) {
				finalBiFunction = this.biFunction;
			}
			else if (this.function != null) {
				finalBiFunction = (request, context) -> this.function.apply(request);
			}
			else {
				finalBiFunction = (request, context) -> {
					this.consumer.accept(request);
					return null;
				};
			}

			// BiFunction<I, ToolContext, O> finalBiFunction = (this.biFunction != null) ?
			// this.biFunction
			// : (request, context) -> this.function.apply(request);

			return new FunctionCallbackWrapper<>(this.name, this.description, this.inputTypeSchema, this.inputType,
					this.responseConverter, this.objectMapper, finalBiFunction);
		}

	}

}
