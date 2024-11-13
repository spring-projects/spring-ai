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
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.model.function.FunctionCallbackContext.SchemaType;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Represents a model function call handler. Implementations are registered with the
 * Models and called on prompts that trigger the function call.
 *
 * @author Christian Tzolov
 */
public interface FunctionCallback {

	/**
	 * @return Returns the Function name. Unique within the model.
	 */
	String getName();

	/**
	 * @return Returns the function description. This description is used by the model do
	 * decide if the function should be called or not.
	 */
	String getDescription();

	/**
	 * @return Returns the JSON schema of the function input type.
	 */
	String getInputTypeSchema();

	/**
	 * Called when a model detects and triggers a function call. The model is responsible
	 * to pass the function arguments in the pre-configured JSON schema format.
	 * @param functionInput JSON string with the function arguments to be passed to the
	 * function. The arguments are defined as JSON schema usually registered with the
	 * model.
	 * @return String containing the function call response.
	 */
	String call(String functionInput);

	/**
	 * Called when a model detects and triggers a function call. The model is responsible
	 * to pass the function arguments in the pre-configured JSON schema format.
	 * Additionally, the model can pass a context map to the function if available. The
	 * context is used to pass additional user provided state in addition to the arguments
	 * provided by the AI model.
	 * @param functionInput JSON string with the function arguments to be passed to the
	 * function. The arguments are defined as JSON schema usually registered with the
	 * model. Arguments are provided by the AI model.
	 * @param tooContext Map with the function context. The context is used to pass
	 * additional user provided state in addition to the arguments provided by the AI
	 * model.
	 * @return String containing the function call response.
	 */
	default String call(String functionInput, ToolContext tooContext) {
		if (tooContext != null && !tooContext.getContext().isEmpty()) {
			throw new UnsupportedOperationException("Function context is not supported!");
		}
		return call(functionInput);
	}

	/**
	 * Creates a new {@link FunctionCallback.Builder} instance used to build a default
	 * {@link FunctionCallback} instance.
	 * @param <I> Function Input type
	 * @param <O> Function Output type
	 * @param function Function to be called by the model.
	 * @return Returns a new {@link FunctionCallback.Builder} instance.
	 */
	static <I, O> FunctionCallback.Builder<I, O> builder(Function<I, O> function) {
		return new DefaultFunctionCallbackBuilder<>(function);
	}

	/**
	 * Creates a new {@link FunctionCallback.Builder} instance used to build a default
	 * {@link FunctionCallback} instance.
	 * @param <I> Function Input type
	 * @param <O> Function Output type
	 * @param biFunction The BiFunction to be called by the model.
	 * @return Returns a new {@link FunctionCallback.Builder} instance.
	 */
	static <I, O> FunctionCallback.Builder<I, O> builder(BiFunction<I, ToolContext, O> biFunction) {
		return new DefaultFunctionCallbackBuilder<>(biFunction);
	}

	interface Builder<I, O> {

		/**
		 * Function name. Unique within the model.
		 */
		Builder<I, O> name(String name);

		/**
		 * Function description. This description is used by the model do decide if the
		 * function should be called or not.
		 */
		Builder<I, O> description(String description);

		/**
		 * Function input type. The input type is used to validate the function input
		 * arguments.
		 * @see #inputType(ParameterizedTypeReference)
		 */
		Builder<I, O> inputType(Class<?> inputType);

		/**
		 * Function input type retaining generic types. The input type is used to validate
		 * the function input arguments.
		 */
		Builder<I, O> inputType(ParameterizedTypeReference<?> inputType);

		/**
		 * Specifies what {@link SchemaType} is used by the AI model to validate the
		 * function input arguments. Most models use JSON Schema, except Vertex AI that
		 * uses OpenAPI types.
		 */
		Builder<I, O> schemaType(SchemaType schemaType);

		/**
		 * Function response converter. The default implementation converts the output
		 * into String before sending it to the Model. Provide a custom function
		 * responseConverter implementation to override this.
		 */
		Builder<I, O> responseConverter(Function<O, String> responseConverter);

		/**
		 * You can provide the Input Type Schema directly. In this case it won't be
		 * generated from the inputType.
		 */
		Builder<I, O> inputTypeSchema(String inputTypeSchema);

		/**
		 * Custom object mapper for JSON operations.
		 */
		Builder<I, O> objectMapper(ObjectMapper objectMapper);

		/**
		 * Builds the {@link FunctionCallback} instance.
		 */
		FunctionCallback build();

	}

}
