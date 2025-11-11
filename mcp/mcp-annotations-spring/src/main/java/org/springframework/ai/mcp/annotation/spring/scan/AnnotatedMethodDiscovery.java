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

package org.springframework.ai.mcp.annotation.spring.scan;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

class AnnotatedMethodDiscovery {

	protected final Set<Class<? extends Annotation>> targetAnnotations;

	AnnotatedMethodDiscovery(Set<Class<? extends Annotation>> targetAnnotations) {
		this.targetAnnotations = targetAnnotations;
	}

	protected Set<Class<? extends Annotation>> scan(Class<?> beanClass) {
		Set<Class<? extends Annotation>> foundAnnotations = new HashSet<>();

		// Scan all methods in the bean class
		ReflectionUtils.doWithMethods(beanClass, method -> {
			this.targetAnnotations.forEach(annotationType -> {
				if (AnnotationUtils.findAnnotation(method, annotationType) != null) {
					foundAnnotations.add(annotationType);
				}
			});
		});
		return foundAnnotations;
	}

}
