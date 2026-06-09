/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.tool.support;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.util.StringUtils;

/**
 * Utility methods for handling tool method parameters.
 *
 * @author Michał Grandys
 * @since 2.0.0
 */
public final class ToolMethodParameterUtils {

	private ToolMethodParameterUtils() {
	}

	public static void validateUniqueParameterNames(Method method,
			Predicate<Parameter> infrastructureParameterPredicate) {
		validateUniqueParameterNames(method, infrastructureParameterPredicate,
				ToolMethodParameterUtils::getParameterName);
	}

	public static String getParameterName(Parameter parameter) {
		ToolParam toolParamAnnotation = parameter.getAnnotation(ToolParam.class);
		if (toolParamAnnotation != null && StringUtils.hasText(toolParamAnnotation.name())) {
			return toolParamAnnotation.name();
		}
		return parameter.getName();
	}

	public static void validateUniqueParameterNames(Method method,
			Predicate<Parameter> infrastructureParameterPredicate, Function<Parameter, String> parameterNameResolver) {
		Set<String> parameterNames = new HashSet<>();
		for (Parameter parameter : method.getParameters()) {
			if (infrastructureParameterPredicate.test(parameter)) {
				continue;
			}
			String parameterName = parameterNameResolver.apply(parameter);
			if (!parameterNames.add(parameterName)) {
				throw new IllegalArgumentException(
						"Duplicate tool parameter name '" + parameterName + "' in method " + method);
			}
		}
	}

}
