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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.function.Function;

import net.jodah.typetools.TypeResolver;

import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;

/**
 * A utility class that provides methods for resolving types and classes related to
 * functions.
 *
 * @author Christian Tzolov
 */
public abstract class TypeResolverHelper {

	/**
	 * Returns the input class of a given function class.
	 * @param functionClass The function class.
	 * @return The input class of the function.
	 */
	public static Class<?> getFunctionInputClass(Class<? extends Function<?, ?>> functionClass) {
		return getFunctionArgumentClass(functionClass, 0);
	}

	/**
	 * Returns the output class of a given function class.
	 * @param functionClass The function class.
	 * @return The output class of the function.
	 */
	public static Class<?> getFunctionOutputClass(Class<? extends Function<?, ?>> functionClass) {
		return getFunctionArgumentClass(functionClass, 1);
	}

	/**
	 * Retrieves the class of a specific argument in a given function class.
	 * @param functionClass The function class.
	 * @param argumentIndex The index of the argument whose class should be retrieved.
	 * @return The class of the specified function argument.
	 */
	public static Class<?> getFunctionArgumentClass(Class<? extends Function<?, ?>> functionClass, int argumentIndex) {
		Type type = TypeResolver.reify(Function.class, functionClass);

		var argumentType = type instanceof ParameterizedType
				? ((ParameterizedType) type).getActualTypeArguments()[argumentIndex] : Object.class;

		return toRawClass(argumentType);
	}

	/**
	 * Returns the input type of a given function class.
	 * @param functionClass The class of the function.
	 * @return The input type of the function.
	 */
	public static Type getFunctionInputType(Class<? extends Function<?, ?>> functionClass) {
		return getFunctionArgumentType(functionClass, 0);
	}

	/**
	 * Retrieves the output type of a given function class.
	 * @param functionClass The function class.
	 * @return The output type of the function.
	 */
	public static Type getFunctionOutputType(Class<? extends Function<?, ?>> functionClass) {
		return getFunctionArgumentType(functionClass, 1);
	}

	/**
	 * Retrieves the type of a specific argument in a given function class.
	 * @param functionClass The function class.
	 * @param argumentIndex The index of the argument whose type should be retrieved.
	 * @return The type of the specified function argument.
	 */
	public static Type getFunctionArgumentType(Class<? extends Function<?, ?>> functionClass, int argumentIndex) {
		Type functionType = TypeResolver.reify(Function.class, functionClass);
		return getFunctionArgumentType(functionType, argumentIndex);
	}

	/**
	 * Retrieves the type of a specific argument in a given function type.
	 * @param functionType The function type.
	 * @param argumentIndex The index of the argument whose type should be retrieved.
	 * @return The type of the specified function argument.
	 */
	public static Type getFunctionArgumentType(Type functionType, int argumentIndex) {

		// Resolves: https://github.com/spring-projects/spring-ai/issues/726
		if (!(functionType instanceof ParameterizedType)) {
			functionType = FunctionTypeUtils.discoverFunctionTypeFromClass(FunctionTypeUtils.getRawType(functionType));
		}

		var argumentType = functionType instanceof ParameterizedType
				? ((ParameterizedType) functionType).getActualTypeArguments()[argumentIndex] : Object.class;

		return argumentType;
	}

	/**
	 * Effectively converts {@link Type} which could be {@link ParameterizedType} to raw
	 * Class (no generics).
	 * @param type actual {@link Type} instance
	 * @return instance of {@link Class} as raw representation of the provided
	 * {@link Type}
	 */
	public static Class<?> toRawClass(Type type) {
		return type != null
				? TypeResolver.resolveRawClass(type instanceof GenericArrayType ? type : TypeResolver.reify(type), null)
				: null;
	}

}
