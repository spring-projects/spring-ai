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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit Tests for {@link AbstractMcpAnnotatedBeans}.
 *
 * @author Sun Yuhan
 */
class AbstractMcpAnnotatedBeansTests {

	private AbstractMcpAnnotatedBeans annotatedBeans;

	@BeforeEach
	void setUp() {
		this.annotatedBeans = new AbstractMcpAnnotatedBeans() {
		};
	}

	@Test
	void testAddMcpAnnotatedBean() {
		Object bean = new Object();
		Set<Class<? extends Annotation>> annotations = new HashSet<>();
		annotations.add(Deprecated.class);
		annotations.add(Override.class);

		this.annotatedBeans.addMcpAnnotatedBean(bean, annotations);

		assertEquals(1, this.annotatedBeans.getCount());
		assertTrue(this.annotatedBeans.getAllAnnotatedBeans().contains(bean));
		assertTrue(this.annotatedBeans.getBeansByAnnotation(Deprecated.class).contains(bean));
		assertTrue(this.annotatedBeans.getBeansByAnnotation(Override.class).contains(bean));
	}

	@Test
	void testGetAllAnnotatedBeans() {
		Object bean1 = new Object();
		Object bean2 = new Object();

		this.annotatedBeans.addMcpAnnotatedBean(bean1, Collections.singleton(Deprecated.class));
		this.annotatedBeans.addMcpAnnotatedBean(bean2, Collections.singleton(Override.class));

		List<Object> allBeans = this.annotatedBeans.getAllAnnotatedBeans();
		assertEquals(2, allBeans.size());
		assertTrue(allBeans.contains(bean1));
		assertTrue(allBeans.contains(bean2));

		allBeans.clear();
		assertEquals(2, this.annotatedBeans.getCount());
	}

	@Test
	void testGetBeansByAnnotation() {
		Object bean1 = new Object();
		Object bean2 = new Object();

		this.annotatedBeans.addMcpAnnotatedBean(bean1, Collections.singleton(Deprecated.class));
		this.annotatedBeans.addMcpAnnotatedBean(bean2, Set.of(Deprecated.class, Override.class));

		List<Object> deprecatedBeans = this.annotatedBeans.getBeansByAnnotation(Deprecated.class);
		assertEquals(2, deprecatedBeans.size());
		assertTrue(deprecatedBeans.contains(bean1));
		assertTrue(deprecatedBeans.contains(bean2));

		List<Object> overrideBeans = this.annotatedBeans.getBeansByAnnotation(Override.class);
		assertEquals(1, overrideBeans.size());
		assertTrue(overrideBeans.contains(bean2));

		List<Object> emptyList = this.annotatedBeans.getBeansByAnnotation(SuppressWarnings.class);
		assertTrue(emptyList.isEmpty());
	}

	@Test
	void testGetCount() {
		assertEquals(0, this.annotatedBeans.getCount());

		this.annotatedBeans.addMcpAnnotatedBean(new Object(), Collections.singleton(Deprecated.class));
		assertEquals(1, this.annotatedBeans.getCount());

		this.annotatedBeans.addMcpAnnotatedBean(new Object(), Collections.singleton(Override.class));
		assertEquals(2, this.annotatedBeans.getCount());
	}

}
