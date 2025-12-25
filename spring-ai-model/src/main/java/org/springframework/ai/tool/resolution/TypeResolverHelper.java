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

package org.springframework.ai.tool.resolution;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * A utility class that provides methods for resolving types and classes related to
 * functions.
 *
 * @author Christian Tzolov
 * @author Sebastien Dekeuze
 */
public final class TypeResolverHelper {

	private TypeResolverHelper() {
		// Avoids instantiation
	}

	/**
	 * Returns the input class of a given Consumer class.
	 * @param consumerClass The consumer class.
	 * @return The input class of the consumer.
	 */
	public static Class<?> getConsumerInputClass(Class<? extends Consumer<?>> consumerClass) {
		ResolvableType resolvableType = ResolvableType.forClass(consumerClass).as(Consumer.class);
		return (resolvableType == ResolvableType.NONE ? Object.class : resolvableType.getGeneric(0).toClass());
	}

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
	 * Resolve bean type, either directly with {@link BeanDefinition#getResolvableType()}
	 * or by resolving the factory method (duplicating
	 * {@code ConstructorResolver#resolveFactoryMethodIfPossible} logic as it is not
	 * public).
	 * @param applicationContext The application context.
	 * @param beanName The name of the bean to find a definition for.
	 * @return The resolved type.
	 * @throws IllegalArgumentException if the type of the bean definition is not
	 * resolvable.
	 */
	public static ResolvableType resolveBeanType(GenericApplicationContext applicationContext, String beanName) {
		BeanDefinition beanDefinition = getBeanDefinition(applicationContext, beanName);

		// Try to resolve directly
		ResolvableType functionType = beanDefinition.getResolvableType();
		if (functionType.resolve() != null) {
			return functionType;
		}

		// Handle root bean definitions with factory methods
		if (beanDefinition instanceof RootBeanDefinition rootBeanDefinition) {
			return resolveRootBeanDefinitionType(applicationContext, rootBeanDefinition);
		}

		// Handle @Component beans
		return resolveComponentBeanType(applicationContext, beanDefinition, beanName);
	}

	private static BeanDefinition getBeanDefinition(GenericApplicationContext applicationContext, String beanName) {
		try {
			return applicationContext.getBeanDefinition(beanName);
		}
		catch (NoSuchBeanDefinitionException ex) {
			throw new IllegalArgumentException(
					"Functional bean with name " + beanName + " does not exist in the context.");
		}
	}

	private static ResolvableType resolveRootBeanDefinitionType(GenericApplicationContext applicationContext,
			RootBeanDefinition rootBeanDefinition) {

		Class<?> factoryClass;
		boolean isStatic;

		if (rootBeanDefinition.getFactoryBeanName() != null) {
			factoryClass = applicationContext.getBeanFactory().getType(rootBeanDefinition.getFactoryBeanName());
			isStatic = false;
		}
		else {
			factoryClass = rootBeanDefinition.getBeanClass();
			isStatic = true;
		}

		Assert.state(factoryClass != null, "Unresolvable factory class");
		factoryClass = ClassUtils.getUserClass(factoryClass);

		Method uniqueCandidate = findUniqueFactoryMethod(factoryClass, isStatic, rootBeanDefinition);
		rootBeanDefinition.setResolvedFactoryMethod(uniqueCandidate);
		return rootBeanDefinition.getResolvableType();
	}

	private static @Nullable Method findUniqueFactoryMethod(Class<?> factoryClass, boolean isStatic,
			RootBeanDefinition rootBeanDefinition) {
		Method[] candidates = getCandidateMethods(factoryClass, rootBeanDefinition);
		Method uniqueCandidate = null;

		for (Method candidate : candidates) {
			if ((!isStatic || isStaticCandidate(candidate, factoryClass))
					&& rootBeanDefinition.isFactoryMethod(candidate)) {
				if (uniqueCandidate == null) {
					uniqueCandidate = candidate;
				}
				else if (isParamMismatch(uniqueCandidate, candidate)) {
					uniqueCandidate = null;
					break;
				}
			}
		}

		return uniqueCandidate;
	}

	private static ResolvableType resolveComponentBeanType(GenericApplicationContext applicationContext,
			BeanDefinition beanDefinition, String beanName) {
		if (beanDefinition.getFactoryMethodName() == null && beanDefinition.getBeanClassName() != null) {
			try {
				return ResolvableType.forClass(
						ClassUtils.forName(beanDefinition.getBeanClassName(), applicationContext.getClassLoader()));
			}
			catch (ClassNotFoundException ex) {
				throw new IllegalArgumentException("Impossible to resolve the type of bean " + beanName, ex);
			}
		}
		throw new IllegalArgumentException("Impossible to resolve the type of bean " + beanName);
	}

	static private Method[] getCandidateMethods(Class<?> factoryClass, RootBeanDefinition mbd) {
		return (mbd.isNonPublicAccessAllowed() ? ReflectionUtils.getUniqueDeclaredMethods(factoryClass)
				: factoryClass.getMethods());
	}

	static private boolean isStaticCandidate(Method method, Class<?> factoryClass) {
		return (Modifier.isStatic(method.getModifiers()) && method.getDeclaringClass() == factoryClass);
	}

	static private boolean isParamMismatch(Method uniqueCandidate, Method candidate) {
		int uniqueCandidateParameterCount = uniqueCandidate.getParameterCount();
		int candidateParameterCount = candidate.getParameterCount();
		return (uniqueCandidateParameterCount != candidateParameterCount
				|| !Arrays.equals(uniqueCandidate.getParameterTypes(), candidate.getParameterTypes()));
	}

	/**
	 * Retrieves the type of a specific argument in a given function class.
	 * @param functionType The function type.
	 * @param argumentIndex The index of the argument whose type should be retrieved.
	 * @return The type of the specified function argument.
	 * @throws IllegalArgumentException if functionType is not a supported type
	 */
	public static ResolvableType getFunctionArgumentType(ResolvableType functionType, int argumentIndex) {

		Class<?> resolvableClass = functionType.toClass();
		ResolvableType functionArgumentResolvableType = ResolvableType.NONE;

		if (Function.class.isAssignableFrom(resolvableClass)) {
			functionArgumentResolvableType = functionType.as(Function.class);
		}
		else if (BiFunction.class.isAssignableFrom(resolvableClass)) {
			functionArgumentResolvableType = functionType.as(BiFunction.class);
		}
		else if (Supplier.class.isAssignableFrom(resolvableClass)) {
			functionArgumentResolvableType = functionType.as(Supplier.class);
		}
		else if (Consumer.class.isAssignableFrom(resolvableClass)) {
			functionArgumentResolvableType = functionType.as(Consumer.class);
		}
		else if (KotlinDetector.isKotlinPresent()) {
			if (KotlinDelegate.isKotlinFunction(resolvableClass)) {
				functionArgumentResolvableType = KotlinDelegate.adaptToKotlinFunctionType(functionType);
			}
			else if (KotlinDelegate.isKotlinBiFunction(resolvableClass)) {
				functionArgumentResolvableType = KotlinDelegate.adaptToKotlinBiFunctionType(functionType);
			}
			else if (KotlinDelegate.isKotlinSupplier(resolvableClass)) {
				functionArgumentResolvableType = KotlinDelegate.adaptToKotlinSupplierType(functionType);
			}
		}

		if (functionArgumentResolvableType == ResolvableType.NONE) {
			throw new IllegalArgumentException(
					"Type must be a Function, BiFunction, Function1 or Function2. Found: " + functionType);
		}

		return functionArgumentResolvableType.getGeneric(argumentIndex);
	}

	private static final class KotlinDelegate {

		public static boolean isKotlinSupplier(Class<?> clazz) {
			return Function0.class.isAssignableFrom(clazz);
		}

		public static ResolvableType adaptToKotlinSupplierType(ResolvableType resolvableType) {
			return resolvableType.as(Function0.class);
		}

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
