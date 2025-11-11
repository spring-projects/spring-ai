/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.annotation.spring;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

import org.springframework.aop.support.AopUtils;
import org.springframework.util.ReflectionUtils;

/**
 * @author Christian Tzolov
 */
public final class AnnotationProviderUtil {

	private AnnotationProviderUtil() {
	}

	/**
	 * Returns the declared methods of the given bean, sorted by method name and parameter
	 * types. This is useful for consistent method ordering in annotation processing.
	 * @param bean The bean instance to inspect
	 * @return An array of sorted methods
	 */
	public static Method[] beanMethods(Object bean) {

		Method[] methods = ReflectionUtils
			.getUniqueDeclaredMethods(AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) : bean.getClass());

		methods = Stream.of(methods).filter(ReflectionUtils.USER_DECLARED_METHODS::matches).toArray(Method[]::new);

		// Method[] methods = ReflectionUtils
		// .getDeclaredMethods(AopUtils.isAopProxy(bean) ? AopUtils.getTargetClass(bean) :
		// bean.getClass());

		// Sort methods by name and parameter types for consistent ordering
		Arrays.sort(methods, Comparator.comparing(Method::getName)
			.thenComparing(method -> Arrays.toString(method.getParameterTypes())));

		return methods;
	}

}
