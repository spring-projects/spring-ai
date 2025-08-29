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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.aop.framework.ProxyFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit Tests for {@link AbstractAnnotatedMethodBeanPostProcessor}.
 *
 * @author Sun Yuhan
 */
@ExtendWith(MockitoExtension.class)
class AbstractAnnotatedMethodBeanPostProcessorTests {

	@Mock
	private AbstractMcpAnnotatedBeans registry;

	private Set<Class<? extends Annotation>> targetAnnotations;

	private AbstractAnnotatedMethodBeanPostProcessor processor;

	@BeforeEach
	void setUp() {
		this.targetAnnotations = new HashSet<>();
		this.targetAnnotations.add(TestAnnotation.class);

		this.processor = new AbstractAnnotatedMethodBeanPostProcessor(this.registry, this.targetAnnotations) {
		};
	}

	@Test
	void testConstructorWithNullRegistry() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			new AbstractAnnotatedMethodBeanPostProcessor(null, this.targetAnnotations) {
			};
		});
		assertEquals("AnnotatedBeanRegistry must not be null", exception.getMessage());
	}

	@Test
	void testConstructorWithEmptyTargetAnnotations() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			new AbstractAnnotatedMethodBeanPostProcessor(this.registry, Collections.emptySet()) {
			};
		});
		assertEquals("Target annotations must not be empty", exception.getMessage());
	}

	@Test
	void testPostProcessAfterInitializationWithoutAnnotations() {
		NoAnnotationBean bean = new NoAnnotationBean();

		Object result = this.processor.postProcessAfterInitialization(bean, "testBean");

		assertSame(bean, result);
		verify(this.registry, never()).addMcpAnnotatedBean(any(), any());
	}

	@Test
	void testPostProcessAfterInitializationWithAnnotations() {
		AnnotatedBean bean = new AnnotatedBean();

		Object result = this.processor.postProcessAfterInitialization(bean, "testBean");

		assertSame(bean, result);
		verify(this.registry, times(1)).addMcpAnnotatedBean(any(), any());
	}

	@Test
	void testPostProcessAfterInitializationWithMultipleMethods() {
		MultipleAnnotationBean bean = new MultipleAnnotationBean();

		Object result = this.processor.postProcessAfterInitialization(bean, "testBean");

		assertSame(bean, result);
		verify(this.registry, times(1)).addMcpAnnotatedBean(any(), any());
	}

	@Test
	void testPostProcessAfterInitializationWithProxy() {
		AnnotatedBean target = new AnnotatedBean();
		ProxyFactory proxyFactory = new ProxyFactory(target);
		proxyFactory.setProxyTargetClass(true);
		Object proxy = proxyFactory.getProxy();

		Object result = this.processor.postProcessAfterInitialization(proxy, "testBean");

		assertSame(proxy, result);
		verify(this.registry, times(1)).addMcpAnnotatedBean(any(), any());
	}

	@Test
	void testCorrectAnnotationsAreCaptured() {
		AnnotatedBean bean = new AnnotatedBean();

		this.processor.postProcessAfterInitialization(bean, "testBean");

		ArgumentCaptor<Set<Class<? extends Annotation>>> annotationsCaptor = ArgumentCaptor.forClass(Set.class);
		verify(this.registry).addMcpAnnotatedBean(same(bean), annotationsCaptor.capture());

		Set<Class<? extends java.lang.annotation.Annotation>> capturedAnnotations = annotationsCaptor.getValue();
		assertEquals(1, capturedAnnotations.size());
		assertTrue(capturedAnnotations.contains(TestAnnotation.class));
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	@interface TestAnnotation {

	}

	static class NoAnnotationBean {

		void methodWithoutAnnotation() {
		}

	}

	static class AnnotatedBean {

		@TestAnnotation
		void methodWithAnnotation() {
		}

	}

	static class MultipleAnnotationBean {

		@TestAnnotation
		void methodWithAnnotation() {
		}

		void methodWithoutAnnotation() {
		}

	}

}
