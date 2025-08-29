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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Unit Tests for {@link AnnotationProviderUtil}.
 *
 * @author Sun Yuhan
 */
@ExtendWith(MockitoExtension.class)
class AnnotationProviderUtilTests {

	@Test
	void beanMethodsWithNormalClassReturnsSortedMethods() {
		TestClass testBean = new TestClass();

		Method[] methods = AnnotationProviderUtil.beanMethods(testBean);

		assertThat(methods).isNotNull();
		assertThat(methods.length).isEqualTo(3);

		assertThat(methods[0].getName()).isEqualTo("aaaMethod");
		assertThat(methods[1].getName()).isEqualTo("bbbMethod");
		assertThat(methods[2].getName()).isEqualTo("cccMethod");

		Arrays.stream(methods).forEach(method -> assertThat(method.getDeclaringClass()).isEqualTo(TestClass.class));
	}

	@Test
	void beanMethodsWithAopProxyReturnsTargetClassMethods() {
		TestClass target = new TestClass();
		ProxyFactory proxyFactory = new ProxyFactory(target);
		Object proxy = proxyFactory.getProxy();

		Method[] methods = AnnotationProviderUtil.beanMethods(proxy);

		assertThat(methods).isNotNull();
		assertThat(methods.length).isEqualTo(3);

		Arrays.stream(methods).forEach(method -> assertThat(method.getDeclaringClass()).isEqualTo(TestClass.class));
	}

	@Test
	void beanMethodsWithMockedAopProxyReturnsTargetClassMethods() {
		Object proxy = mock(Object.class);

		try (MockedStatic<AopUtils> mockedAopUtils = mockStatic(AopUtils.class)) {
			mockedAopUtils.when(() -> AopUtils.isAopProxy(proxy)).thenReturn(true);
			mockedAopUtils.when(() -> AopUtils.getTargetClass(proxy)).thenReturn(TestClass.class);

			Method[] methods = AnnotationProviderUtil.beanMethods(proxy);

			assertThat(methods).isNotNull();
			assertThat(methods.length).isEqualTo(3);

			mockedAopUtils.verify(() -> AopUtils.isAopProxy(proxy));
			mockedAopUtils.verify(() -> AopUtils.getTargetClass(proxy));
		}
	}

	@Test
	void beanMethodsWithNoDeclaredMethodsReturnsEmptyArray() {
		NoMethodClass testBean = new NoMethodClass();

		Method[] methods = AnnotationProviderUtil.beanMethods(testBean);

		assertThat(methods).isNotNull();
		assertThat(methods).isEmpty();
	}

	@Test
	void beanMethodsWithOverloadedMethodsReturnsCorrectlySortedMethods() {
		OverloadedMethodClass testBean = new OverloadedMethodClass();

		Method[] methods = AnnotationProviderUtil.beanMethods(testBean);

		assertThat(methods).isNotNull();
		assertThat(methods.length).isEqualTo(3);

		assertThat(methods[0].getName()).isEqualTo("overloadedMethod");
		assertThat(methods[0].getParameterCount()).isEqualTo(0);

		assertThat(methods[1].getName()).isEqualTo("overloadedMethod");
		assertThat(methods[1].getParameterCount()).isEqualTo(1);

		assertThat(methods[2].getName()).isEqualTo("simpleMethod");
	}

	static class TestClass {

		public void cccMethod() {
		}

		public void aaaMethod() {
		}

		public void bbbMethod() {
		}

	}

	static class NoMethodClass {

	}

	static class OverloadedMethodClass {

		public void simpleMethod() {
		}

		public void overloadedMethod(String param) {
		}

		public void overloadedMethod() {
		}

	}

}
