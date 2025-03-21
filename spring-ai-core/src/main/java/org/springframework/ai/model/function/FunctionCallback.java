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

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.core.ParameterizedTypeReference;

/**
 * Represents a model function call handler. Implementations are registered with the
 * Models and called on prompts that trigger the function call.
 *
 * @author Christian Tzolov
 * @deprecated in favor of {@link ToolCallback}.
 */
@Deprecated
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
	default String call(String functionInput, ToolContext toolContext) {
		if (toolContext != null && !toolContext.getContext().isEmpty()) {
			throw new UnsupportedOperationException("Function context is not supported!");
		}
		return call(functionInput);
	}

	/**
	 * Creates a new {@link FunctionCallback.Builder} instance used to build a default
	 * {@link FunctionCallback} instance.
	 * @return Returns a new {@link FunctionCallback.Builder} instance.
	 */
	static FunctionCallback.Builder builder() {
		return new DefaultFunctionCallbackBuilder();
	}

	/**
	 * Describes the type of the schema used to describe the input parameters of the
	 * function.
	 */
	enum SchemaType {

		/**
		 * JSON schema
		 */
		JSON_SCHEMA,
		/**
		 * Open API schema
		 */
		OPEN_API_SCHEMA

	}

	/**
	 * Builder for creating a {@link FunctionCallback} instance. This is a hierarchical
	 * builder with the following structure:
	 * <ul>
	 * <li>{@link Builder} - The root builder interface.
	 * <li>{@link FunctionInvokingSpec} - The function invoking builder interface.
	 * <li>{@link MethodInvokingSpec} - The method invoking builder interface.
	 * </ul>
	 *
	 * @deprecated Use specific builder for the type of tool you need, e.g.
	 * {@link FunctionToolCallback.Builder} and {@link MethodToolCallback.Builder}.
	 */
	@Deprecated
	interface Builder {

		/**
		 * Builds a {@link Function} invoking {@link FunctionCallback} instance.
		 */
		<I, O> FunctionInvokingSpec<I, O> function(String name, Function<I, O> function);

		/**
		 * Builds a {@link BiFunction} invoking {@link FunctionCallback} instance.
		 */
		<I, O> FunctionInvokingSpec<I, O> function(String name, BiFunction<I, ToolContext, O> biFunction);

		/**
		 * Builds a {@link Supplier} invoking {@link FunctionCallback} instance.
		 */
		<O> FunctionInvokingSpec<Void, O> function(String name, Supplier<O> supplier);

		/**
		 * Builds a {@link Consumer} invoking {@link FunctionCallback} instance.
		 */
		<I> FunctionInvokingSpec<I, Void> function(String name, Consumer<I> consumer);

		/**
		 * Builds a Method invoking {@link FunctionCallback} instance.
		 */
		MethodInvokingSpec method(String methodName, Class<?>... argumentTypes);

	}

	interface CommonCallbackInvokingSpec<B extends CommonCallbackInvokingSpec<B>> {

		/**
		 * Function description. This description is used by the model to decide if the
		 * function should be called or not.
		 */
		B description(String description);

		/**
		 * Specifies what {@link SchemaType} is used by the AI model to validate the
		 * function input arguments. Most models use JSON Schema, except Vertex AI that
		 * uses OpenAPI types.
		 */
		B schemaType(SchemaType schemaType);

		/**
		 * Function response converter. The default implementation converts the output
		 * into String before sending it to the Model. Provide a custom function
		 * responseConverter implementation to override this.
		 */
		B responseConverter(Function<Object, String> responseConverter);

		/**
		 * You can provide the Input Type Schema directly. In this case it won't be
		 * generated from the inputType.
		 */
		B inputTypeSchema(String inputTypeSchema);

		/**
		 * Custom object mapper for JSON operations.
		 */
		B objectMapper(ObjectMapper objectMapper);

	}

	/**
	 * {@link Function} invoking builder interface.
	 *
	 * @param <I> Function input type.
	 * @param <O> Function output type.
	 */
	interface FunctionInvokingSpec<I, O> extends CommonCallbackInvokingSpec<FunctionInvokingSpec<I, O>> {

		/**
		 * Function input type. The input type is used to validate the function input
		 * arguments.
		 * @see #inputType(ParameterizedTypeReference)
		 */
		FunctionInvokingSpec<I, O> inputType(Class<?> inputType);

		/**
		 * Function input type retaining generic types. The input type is used to validate
		 * the function input arguments.
		 */
		FunctionInvokingSpec<I, O> inputType(ParameterizedTypeReference<?> inputType);

		/**
		 * Builds the {@link FunctionCallback} instance.
		 */
		FunctionCallback build();

	}

	/**
	 * Method invoking builder interface.
	 */
	interface MethodInvokingSpec extends CommonCallbackInvokingSpec<MethodInvokingSpec> {

		/**
		 * Optional function name. If not provided the method name is used as the
		 * function.
		 * @param name Function name. Unique within the model.
		 */
		MethodInvokingSpec name(String name);

		/**
		 * For non-static objects the target object is used to invoke the method.
		 * @param methodObject target object where the method is defined.
		 */
		MethodInvokingSpec targetObject(Object methodObject);

		/**
		 * Target class where the method is defined. Used for static methods. For
		 * non-static methods the target object is used.
		 * @param targetClass method target class.
		 */
		MethodInvokingSpec targetClass(Class<?> targetClass);

		/**
		 * Builds the {@link FunctionCallback} instance.
		 */
		FunctionCallback build();

	}

}
