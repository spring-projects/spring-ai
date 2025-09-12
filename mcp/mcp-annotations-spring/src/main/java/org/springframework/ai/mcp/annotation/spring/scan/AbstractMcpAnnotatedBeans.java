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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Container for Beans that have method with MCP annotations
 *
 * @author Christian Tzolov
 */
public abstract class AbstractMcpAnnotatedBeans {

	private final List<Object> beansWithCustomAnnotations = new ArrayList<>();

	private final Map<Class<? extends Annotation>, List<Object>> beansByAnnotation = new HashMap<>();

	public void addMcpAnnotatedBean(Object bean, Set<Class<? extends Annotation>> annotations) {
		this.beansWithCustomAnnotations.add(bean);

		annotations
			.forEach(annotationType -> this.beansByAnnotation.computeIfAbsent(annotationType, k -> new ArrayList<>())
				.add(bean));
	}

	public List<Object> getAllAnnotatedBeans() {
		return new ArrayList<>(this.beansWithCustomAnnotations);
	}

	public List<Object> getBeansByAnnotation(Class<? extends Annotation> annotationType) {
		return this.beansByAnnotation.getOrDefault(annotationType, Collections.emptyList());
	}

	public int getCount() {
		return this.beansWithCustomAnnotations.size();
	}

}
