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
package org.springframework.ai.tool.method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

/**
 * Tests for {@link MethodToolCallbackProvider} with AOP proxies.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
class MethodToolCallbackProviderAopTests {

	/**
	 * Test annotation to simulate a Spring AOP aspect
	 */
	@java.lang.annotation.Target({ java.lang.annotation.ElementType.METHOD })
	@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
	@java.lang.annotation.Documented
	public @interface LogExecution {

	}

	/**
	 * Sample bean with methods annotated with both @Tool and @LogExecution
	 */
	@Component
	static class ToolsWithAopAnnotations {

		@Tool(description = "Method with AOP annotation")
		@LogExecution
		public String methodWithAopAnnotation(String input) {
			return "Processed: " + input;
		}

		@Tool(description = "Another method with AOP annotation")
		@LogExecution
		public List<String> anotherMethodWithAopAnnotation(String input) {
			return List.of("Item: " + input);
		}

		@Tool(description = "Method without AOP annotation")
		public String methodWithoutAopAnnotation(String input) {
			return "Regular: " + input;
		}

	}

	@Test
	void shouldHandleAopProxiedToolObject() {
		// Create the original tool object
		ToolsWithAopAnnotations originalToolObject = new ToolsWithAopAnnotations();

		// Create a proxy for the tool object with an aspect for @LogExecution annotation
		ProxyFactory proxyFactory = new ProxyFactory(originalToolObject);
		AnnotationMatchingPointcut pointcut = new AnnotationMatchingPointcut(null, LogExecution.class);

		// Create a method interceptor for logging
		MethodInterceptor loggingInterceptor = new MethodInterceptor() {
			@Override
			public Object invoke(MethodInvocation methodInvocation) throws Throwable {
				// Simple logging advice
				System.out.println("Before executing: " + methodInvocation.getMethod().getName());
				Object result = methodInvocation.proceed();
				System.out.println("After executing: " + methodInvocation.getMethod().getName());
				return result;
			}
		};

		proxyFactory.addAdvisor(new DefaultPointcutAdvisor(pointcut, loggingInterceptor));

		Object proxiedToolObject = proxyFactory.getProxy();

		// Verify that the object is indeed a proxy
		assertThat(AopUtils.isAopProxy(proxiedToolObject)).isTrue();
		assertThat(AopUtils.getTargetClass(proxiedToolObject)).isEqualTo(ToolsWithAopAnnotations.class);

		// Create the provider with the proxied object
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
			.toolObjects(proxiedToolObject)
			.build();

		// Get the tool callbacks
		ToolCallback[] callbacks = provider.getToolCallbacks();

		// Verify that all methods with @Tool annotation are found, including those with
		// @LogExecution
		assertThat(callbacks).hasSize(3);

		// Verify that the tool names match the expected method names
		assertThat(Stream.of(callbacks).map(ToolCallback::getName)).containsExactlyInAnyOrder("methodWithAopAnnotation",
				"anotherMethodWithAopAnnotation", "methodWithoutAopAnnotation");
	}

	/**
	 * This test specifically validates the AOP proxy handling logic in
	 * MethodToolCallbackProvider. It uses Mockito to verify that AopUtils.isAopProxy and
	 * AopUtils.getTargetClass are called correctly when processing a proxied object.
	 */
	@Test
	void shouldUseAopUtilsToHandleProxiedObjects() {
		// Create the original tool object
		ToolsWithAopAnnotations originalToolObject = new ToolsWithAopAnnotations();

		// Create a proxy for the tool object
		ProxyFactory proxyFactory = new ProxyFactory(originalToolObject);
		AnnotationMatchingPointcut pointcut = new AnnotationMatchingPointcut(null, LogExecution.class);

		MethodInterceptor loggingInterceptor = new MethodInterceptor() {
			@Override
			public Object invoke(MethodInvocation methodInvocation) throws Throwable {
				return methodInvocation.proceed();
			}
		};

		proxyFactory.addAdvisor(new DefaultPointcutAdvisor(pointcut, loggingInterceptor));
		Object proxiedToolObject = proxyFactory.getProxy();

		// Use MockedStatic to verify AopUtils static methods are called
		try (MockedStatic<AopUtils> mockedAopUtils = Mockito.mockStatic(AopUtils.class)) {
			// Set up the mocked behavior
			mockedAopUtils.when(() -> AopUtils.isAopProxy(any())).thenReturn(true);
			mockedAopUtils.when(() -> AopUtils.getTargetClass(any())).thenReturn(ToolsWithAopAnnotations.class);

			// Create the provider with the proxied object
			MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
				.toolObjects(proxiedToolObject)
				.build();

			// Get the tool callbacks - this should trigger the AopUtils methods
			provider.getToolCallbacks();

			// Verify that AopUtils.isAopProxy was called with the proxied object
			mockedAopUtils.verify(() -> AopUtils.isAopProxy(proxiedToolObject), times(1));

			// Verify that AopUtils.getTargetClass was called with the proxied object
			mockedAopUtils.verify(() -> AopUtils.getTargetClass(proxiedToolObject), times(1));
		}
	}

	@Test
	void shouldHandleMixOfProxiedAndNonProxiedToolObjects() {
		// Create the original tool objects
		ToolsWithAopAnnotations originalToolObject = new ToolsWithAopAnnotations();

		// Create a proxy for one of the tool objects
		ProxyFactory proxyFactory = new ProxyFactory(originalToolObject);
		AnnotationMatchingPointcut pointcut = new AnnotationMatchingPointcut(null, LogExecution.class);

		// Create a method interceptor for logging
		MethodInterceptor loggingInterceptor = new MethodInterceptor() {
			@Override
			public Object invoke(MethodInvocation methodInvocation) throws Throwable {
				// Simple logging advice
				System.out.println("Before executing: " + methodInvocation.getMethod().getName());
				Object result = methodInvocation.proceed();
				System.out.println("After executing: " + methodInvocation.getMethod().getName());
				return result;
			}
		};

		proxyFactory.addAdvisor(new DefaultPointcutAdvisor(pointcut, loggingInterceptor));

		Object proxiedToolObject = proxyFactory.getProxy();

		// Create a non-proxied tool object
		MethodToolCallbackProviderTests.ToolsExtra nonProxiedToolObject = new MethodToolCallbackProviderTests.ToolsExtra();

		// Create the provider with both proxied and non-proxied objects
		MethodToolCallbackProvider provider = MethodToolCallbackProvider.builder()
			.toolObjects(proxiedToolObject, nonProxiedToolObject)
			.build();

		// Get the tool callbacks
		ToolCallback[] callbacks = provider.getToolCallbacks();

		// Verify that all methods with @Tool annotation are found from both objects
		assertThat(callbacks).hasSize(5); // 3 from proxied + 2 from non-proxied

		// Verify that the tool names match the expected method names
		assertThat(Stream.of(callbacks).map(ToolCallback::getName)).containsExactlyInAnyOrder("methodWithAopAnnotation",
				"anotherMethodWithAopAnnotation", "methodWithoutAopAnnotation", "extraMethod1", "extraMethod2");
	}

}
