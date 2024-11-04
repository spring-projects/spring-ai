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

import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;

/**
 * A utility class that provides methods for resolving types and classes related to
 * functions.
 *
 * @author Christian Tzolov
 * @author Sebastien Dekeuze
 */
public abstract class TypeResolverHelper {

	/**
	 * Returns the input class of a given function class.
	 * @param biFunctionClass The function class.
	 * @return The input class of the function.
	 */
	public static Class<?> getBiFunctionInputClass(Class<? extends BiFunction<?, ?, ?>> biFunctionClass) {
		return getBiFunctionArgumentClass(biFunctionClass, 0);
	}

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
		ResolvableType resolvableType = ResolvableType.forClass(functionClass).as(Function.class);
		return (resolvableType == ResolvableType.NONE ? Object.class
				: resolvableType.getGeneric(argumentIndex).toClass());
	}

	/**
	 * Retrieves the class of a specific argument in a given function class.
	 * @param biFunctionClass The function class.
	 * @param argumentIndex The index of the argument whose class should be retrieved.
	 * @return The class of the specified function argument.
	 */
	public static Class<?> getBiFunctionArgumentClass(Class<? extends BiFunction<?, ?, ?>> biFunctionClass,
			int argumentIndex) {
		ResolvableType resolvableType = ResolvableType.forClass(biFunctionClass).as(BiFunction.class);
		return (resolvableType == ResolvableType.NONE ? Object.class
				: resolvableType.getGeneric(argumentIndex).toClass());
	}

	/**
	 * Retrieves the type of a specific argument in a given function class.
	 * @param functionType The function type.
	 * @param argumentIndex The index of the argument whose type should be retrieved.
	 * @return The type of the specified function argument.
	 * @throws IllegalArgumentException if functionType is not a supported type
	 */
	public static ResolvableType getFunctionArgumentType(Type functionType, int argumentIndex) {

		ResolvableType resolvableType = ResolvableType.forType(functionType);
		Class<?> resolvableClass = resolvableType.toClass();
		ResolvableType functionArgumentResolvableType = ResolvableType.NONE;

		if (Function.class.isAssignableFrom(resolvableClass)) {
			functionArgumentResolvableType = resolvableType.as(Function.class);
		}
		else if (BiFunction.class.isAssignableFrom(resolvableClass)) {
			functionArgumentResolvableType = resolvableType.as(BiFunction.class);
		}
		else if (KotlinDetector.isKotlinPresent()) {
			if (KotlinDelegate.isKotlinFunction(resolvableClass)) {
				functionArgumentResolvableType = KotlinDelegate.adaptToKotlinFunctionType(resolvableType);
			}
			else if (KotlinDelegate.isKotlinBiFunction(resolvableClass)) {
				functionArgumentResolvableType = KotlinDelegate.adaptToKotlinBiFunctionType(resolvableType);
			}
		}

		if (functionArgumentResolvableType == ResolvableType.NONE) {
			throw new IllegalArgumentException(
					"Type must be a Function, BiFunction, Function1 or Function2. Found: " + resolvableType);
		}

		return functionArgumentResolvableType.getGeneric(argumentIndex);
	}

	private static class KotlinDelegate {

		public static boolean isKotlinFunction(Class<?> clazz) {
			return Function1.class.isAssignableFrom(clazz);
		}

		public static ResolvableType adaptToKotlinFunctionType(ResolvableType resolvableType) {
			return resolvableType.as(Function1.class);
		}

		public static boolean isKotlinBiFunction(Class<?> clazz) {
			return Function2.class.isAssignableFrom(clazz);
		}

		public static ResolvableType adaptToKotlinBiFunctionType(ResolvableType resolvableType) {
			return resolvableType.as(Function2.class);
		}

	}

}
