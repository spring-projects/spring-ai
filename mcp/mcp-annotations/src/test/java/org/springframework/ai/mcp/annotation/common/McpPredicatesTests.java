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

package org.springframework.ai.mcp.annotation.common;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link McpPredicates}.
 *
 * @author Christian Tzolov
 */
public class McpPredicatesTests {

	// URI Template Tests

	@Test
	public void testIsUriTemplateWithSimpleVariable() {
		assertThat(McpPredicates.isUriTemplate("/api/{id}")).isTrue();
	}

	@Test
	public void testIsUriTemplateWithMultipleVariables() {
		assertThat(McpPredicates.isUriTemplate("/api/{userId}/posts/{postId}")).isTrue();
	}

	@Test
	public void testIsUriTemplateWithVariableAtStart() {
		assertThat(McpPredicates.isUriTemplate("{id}/details")).isTrue();
	}

	@Test
	public void testIsUriTemplateWithVariableAtEnd() {
		assertThat(McpPredicates.isUriTemplate("/api/users/{id}")).isTrue();
	}

	@Test
	public void testIsUriTemplateWithComplexVariableName() {
		assertThat(McpPredicates.isUriTemplate("/api/{user_id}")).isTrue();
		assertThat(McpPredicates.isUriTemplate("/api/{userId123}")).isTrue();
	}

	@Test
	public void testIsUriTemplateWithNoVariables() {
		assertThat(McpPredicates.isUriTemplate("/api/users")).isFalse();
	}

	@Test
	public void testIsUriTemplateWithEmptyString() {
		assertThat(McpPredicates.isUriTemplate("")).isFalse();
	}

	@Test
	public void testIsUriTemplateWithOnlySlashes() {
		assertThat(McpPredicates.isUriTemplate("/")).isFalse();
		assertThat(McpPredicates.isUriTemplate("//")).isFalse();
	}

	@Test
	public void testIsUriTemplateWithIncompleteBraces() {
		assertThat(McpPredicates.isUriTemplate("/api/{id")).isFalse();
		assertThat(McpPredicates.isUriTemplate("/api/id}")).isFalse();
	}

	@Test
	public void testIsUriTemplateWithEmptyBraces() {
		assertThat(McpPredicates.isUriTemplate("/api/{}")).isFalse();
	}

	@Test
	public void testIsUriTemplateWithNestedPath() {
		assertThat(McpPredicates.isUriTemplate("/api/v1/users/{userId}/posts/{postId}/comments")).isTrue();
	}

	// Reactive Return Type Predicate Tests

	@Test
	public void testIsReactiveReturnTypeWithMono() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("monoMethod");
		assertThat(McpPredicates.isReactiveReturnType.test(method)).isTrue();
	}

	@Test
	public void testIsReactiveReturnTypeWithFlux() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("fluxMethod");
		assertThat(McpPredicates.isReactiveReturnType.test(method)).isTrue();
	}

	@Test
	public void testIsReactiveReturnTypeWithPublisher() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("publisherMethod");
		assertThat(McpPredicates.isReactiveReturnType.test(method)).isTrue();
	}

	@Test
	public void testIsReactiveReturnTypeWithNonReactive() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("nonReactiveMethod");
		assertThat(McpPredicates.isReactiveReturnType.test(method)).isFalse();
	}

	@Test
	public void testIsReactiveReturnTypeWithVoid() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("voidMethod");
		assertThat(McpPredicates.isReactiveReturnType.test(method)).isFalse();
	}

	@Test
	public void testIsReactiveReturnTypeWithList() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("listMethod");
		assertThat(McpPredicates.isReactiveReturnType.test(method)).isFalse();
	}

	// Non-Reactive Return Type Predicate Tests

	@Test
	public void testIsNotReactiveReturnTypeWithMono() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("monoMethod");
		assertThat(McpPredicates.isNotReactiveReturnType.test(method)).isFalse();
	}

	@Test
	public void testIsNotReactiveReturnTypeWithFlux() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("fluxMethod");
		assertThat(McpPredicates.isNotReactiveReturnType.test(method)).isFalse();
	}

	@Test
	public void testIsNotReactiveReturnTypeWithPublisher() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("publisherMethod");
		assertThat(McpPredicates.isNotReactiveReturnType.test(method)).isFalse();
	}

	@Test
	public void testIsNotReactiveReturnTypeWithNonReactive() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("nonReactiveMethod");
		assertThat(McpPredicates.isNotReactiveReturnType.test(method)).isTrue();
	}

	@Test
	public void testIsNotReactiveReturnTypeWithVoid() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("voidMethod");
		assertThat(McpPredicates.isNotReactiveReturnType.test(method)).isTrue();
	}

	@Test
	public void testIsNotReactiveReturnTypeWithList() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("listMethod");
		assertThat(McpPredicates.isNotReactiveReturnType.test(method)).isTrue();
	}

	// Filter Non-Reactive Return Type Method Tests

	@Test
	public void testFilterNonReactiveReturnTypeMethodWithReactiveType() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("monoMethod");
		Predicate<Method> filter = McpPredicates.filterNonReactiveReturnTypeMethod();
		assertThat(filter.test(method)).isTrue();
	}

	@Test
	public void testFilterNonReactiveReturnTypeMethodWithNonReactiveType() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("nonReactiveMethod");
		Predicate<Method> filter = McpPredicates.filterNonReactiveReturnTypeMethod();
		// This should return false and log a warning
		assertThat(filter.test(method)).isFalse();
	}

	@Test
	public void testFilterNonReactiveReturnTypeMethodWithFlux() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("fluxMethod");
		Predicate<Method> filter = McpPredicates.filterNonReactiveReturnTypeMethod();
		assertThat(filter.test(method)).isTrue();
	}

	@Test
	public void testFilterNonReactiveReturnTypeMethodWithPublisher() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("publisherMethod");
		Predicate<Method> filter = McpPredicates.filterNonReactiveReturnTypeMethod();
		assertThat(filter.test(method)).isTrue();
	}

	// Filter Reactive Return Type Method Tests

	@Test
	public void testFilterReactiveReturnTypeMethodWithReactiveType() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("monoMethod");
		Predicate<Method> filter = McpPredicates.filterReactiveReturnTypeMethod();
		// This should return false and log a warning
		assertThat(filter.test(method)).isFalse();
	}

	@Test
	public void testFilterReactiveReturnTypeMethodWithNonReactiveType() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("nonReactiveMethod");
		Predicate<Method> filter = McpPredicates.filterReactiveReturnTypeMethod();
		assertThat(filter.test(method)).isTrue();
	}

	@Test
	public void testFilterReactiveReturnTypeMethodWithFlux() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("fluxMethod");
		Predicate<Method> filter = McpPredicates.filterReactiveReturnTypeMethod();
		// This should return false and log a warning
		assertThat(filter.test(method)).isFalse();
	}

	@Test
	public void testFilterReactiveReturnTypeMethodWithPublisher() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("publisherMethod");
		Predicate<Method> filter = McpPredicates.filterReactiveReturnTypeMethod();
		// This should return false and log a warning
		assertThat(filter.test(method)).isFalse();
	}

	@Test
	public void testFilterReactiveReturnTypeMethodWithVoid() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("voidMethod");
		Predicate<Method> filter = McpPredicates.filterReactiveReturnTypeMethod();
		assertThat(filter.test(method)).isTrue();
	}

	// Filter Method With Bidirectional Parameters Tests

	@Test
	public void testFilterMethodWithBidirectionalParametersWithSyncContext() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("methodWithSyncContext", McpSyncRequestContext.class);
		Predicate<Method> filter = McpPredicates.filterMethodWithBidirectionalParameters();
		// This should return false and log a warning
		assertThat(filter.test(method)).isFalse();
	}

	@Test
	public void testFilterMethodWithBidirectionalParametersWithAsyncContext() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("methodWithAsyncContext", McpAsyncRequestContext.class);
		Predicate<Method> filter = McpPredicates.filterMethodWithBidirectionalParameters();
		// This should return false and log a warning
		assertThat(filter.test(method)).isFalse();
	}

	@Test
	public void testFilterMethodWithBidirectionalParametersWithSyncExchange() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("methodWithSyncExchange", McpSyncServerExchange.class);
		Predicate<Method> filter = McpPredicates.filterMethodWithBidirectionalParameters();
		// This should return false and log a warning
		assertThat(filter.test(method)).isFalse();
	}

	@Test
	public void testFilterMethodWithBidirectionalParametersWithAsyncExchange() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("methodWithAsyncExchange", McpAsyncServerExchange.class);
		Predicate<Method> filter = McpPredicates.filterMethodWithBidirectionalParameters();
		// This should return false and log a warning
		assertThat(filter.test(method)).isFalse();
	}

	@Test
	public void testFilterMethodWithBidirectionalParametersWithMultipleParams() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("methodWithMultipleParams", String.class,
				McpSyncRequestContext.class, int.class);
		Predicate<Method> filter = McpPredicates.filterMethodWithBidirectionalParameters();
		// This should return false because it has a bidirectional parameter
		assertThat(filter.test(method)).isFalse();
	}

	@Test
	public void testFilterMethodWithBidirectionalParametersWithoutBidirectionalParams() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("methodWithoutBidirectionalParams", String.class, int.class);
		Predicate<Method> filter = McpPredicates.filterMethodWithBidirectionalParameters();
		assertThat(filter.test(method)).isTrue();
	}

	@Test
	public void testFilterMethodWithBidirectionalParametersWithNoParams() throws NoSuchMethodException {
		Method method = TestMethods.class.getMethod("nonReactiveMethod");
		Predicate<Method> filter = McpPredicates.filterMethodWithBidirectionalParameters();
		assertThat(filter.test(method)).isTrue();
	}

	// Combined Filter Tests

	@Test
	public void testCombinedFiltersForStatelessSyncProvider() throws NoSuchMethodException {
		// Stateless sync providers should filter out:
		// 1. Methods with reactive return types
		// 2. Methods with bidirectional parameters

		Method validMethod = TestMethods.class.getMethod("methodWithoutBidirectionalParams", String.class, int.class);
		Method reactiveMethod = TestMethods.class.getMethod("monoMethod");
		Method bidirectionalMethod = TestMethods.class.getMethod("methodWithSyncContext", McpSyncRequestContext.class);

		Predicate<Method> reactiveFilter = McpPredicates.filterReactiveReturnTypeMethod();
		Predicate<Method> bidirectionalFilter = McpPredicates.filterMethodWithBidirectionalParameters();
		Predicate<Method> combinedFilter = reactiveFilter.and(bidirectionalFilter);

		assertThat(combinedFilter.test(validMethod)).isTrue();
		assertThat(combinedFilter.test(reactiveMethod)).isFalse();
		assertThat(combinedFilter.test(bidirectionalMethod)).isFalse();
	}

	@Test
	public void testCombinedFiltersForStatelessAsyncProvider() throws NoSuchMethodException {
		// Stateless async providers should filter out:
		// 1. Methods with non-reactive return types
		// 2. Methods with bidirectional parameters

		Method validMethod = TestMethods.class.getMethod("monoMethod");
		Method nonReactiveMethod = TestMethods.class.getMethod("nonReactiveMethod");
		Method bidirectionalMethod = TestMethods.class.getMethod("methodWithAsyncContext",
				McpAsyncRequestContext.class);

		Predicate<Method> nonReactiveFilter = McpPredicates.filterNonReactiveReturnTypeMethod();
		Predicate<Method> bidirectionalFilter = McpPredicates.filterMethodWithBidirectionalParameters();
		Predicate<Method> combinedFilter = nonReactiveFilter.and(bidirectionalFilter);

		assertThat(combinedFilter.test(validMethod)).isTrue();
		assertThat(combinedFilter.test(nonReactiveMethod)).isFalse();
		assertThat(combinedFilter.test(bidirectionalMethod)).isFalse();
	}

	// Edge Case Tests

	@Test
	public void testIsUriTemplateWithSpecialCharacters() {
		assertThat(McpPredicates.isUriTemplate("/api/{user-id}")).isTrue();
		assertThat(McpPredicates.isUriTemplate("/api/{user.id}")).isTrue();
	}

	@Test
	public void testIsUriTemplateWithQueryParameters() {
		// Query parameters are not URI template variables
		assertThat(McpPredicates.isUriTemplate("/api/users?id={id}")).isTrue();
	}

	@Test
	public void testIsUriTemplateWithFragment() {
		assertThat(McpPredicates.isUriTemplate("/api/users#{id}")).isTrue();
	}

	@Test
	public void testIsUriTemplateWithMultipleConsecutiveVariables() {
		assertThat(McpPredicates.isUriTemplate("/{id}{name}")).isTrue();
	}

	@Test
	public void testPredicatesAreReusable() throws NoSuchMethodException {
		// Test that predicates can be reused multiple times
		Predicate<Method> filter = McpPredicates.filterReactiveReturnTypeMethod();

		Method method1 = TestMethods.class.getMethod("nonReactiveMethod");
		Method method2 = TestMethods.class.getMethod("monoMethod");
		Method method3 = TestMethods.class.getMethod("listMethod");

		assertThat(filter.test(method1)).isTrue();
		assertThat(filter.test(method2)).isFalse();
		assertThat(filter.test(method3)).isTrue();
	}

	// Test classes for method reflection tests
	static class TestMethods {

		public String nonReactiveMethod() {
			return "test";
		}

		public Mono<String> monoMethod() {
			return Mono.just("test");
		}

		public Flux<String> fluxMethod() {
			return Flux.just("test");
		}

		public Publisher<String> publisherMethod() {
			return Mono.just("test");
		}

		public void voidMethod() {
		}

		public List<String> listMethod() {
			return List.of("test");
		}

		public String methodWithSyncContext(McpSyncRequestContext context) {
			return "test";
		}

		public String methodWithAsyncContext(McpAsyncRequestContext context) {
			return "test";
		}

		public String methodWithSyncExchange(McpSyncServerExchange exchange) {
			return "test";
		}

		public String methodWithAsyncExchange(McpAsyncServerExchange exchange) {
			return "test";
		}

		public String methodWithMultipleParams(String param1, McpSyncRequestContext context, int param2) {
			return "test";
		}

		public String methodWithoutBidirectionalParams(String param1, int param2) {
			return "test";
		}

	}

}
