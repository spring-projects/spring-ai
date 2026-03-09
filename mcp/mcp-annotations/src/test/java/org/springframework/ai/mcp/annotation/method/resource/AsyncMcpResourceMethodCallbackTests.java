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

package org.springframework.ai.mcp.annotation.method.resource;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.BlobResourceContents;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.util.McpUriTemplateManager;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.adapter.ResourceAdapter;
import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.ai.mcp.annotation.context.McpAsyncRequestContext;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AsyncMcpResourceMethodCallback}.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 */
public class AsyncMcpResourceMethodCallbackTests {

	// Helper method to create a mock McpResource annotation
	private McpResource createMockMcpResource() {
		return new McpResource() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return McpResource.class;
			}

			@Override
			public String uri() {
				return "test://resource";
			}

			@Override
			public String name() {
				return "";
			}

			@Override
			public String title() {
				return "";
			}

			@Override
			public String description() {
				return "";
			}

			@Override
			public String mimeType() {
				return "text/plain";
			}

			@Override
			public McpAnnotations annotations() {
				return new McpAnnotations() {
					@Override
					public Class<? extends java.lang.annotation.Annotation> annotationType() {
						return McpAnnotations.class;
					}

					@Override
					public Role[] audience() {
						return new Role[] { Role.USER };
					}

					@Override
					public String lastModified() {
						return "";
					}

					@Override
					public double priority() {
						return 0.5;
					}
				};
			}

			@Override
			public Class<? extends MetaProvider> metaProvider() {
				return DefaultMetaProvider.class;
			}
		};
	}

	@Test
	public void testCallbackWithRequestParameter() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithRequest", ReadResourceRequest.class);

		// Provide a mock McpResource annotation since the method doesn't have one
		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Content for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithExchangeAndRequestParameters() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithExchange",
				McpAsyncServerExchange.class, ReadResourceRequest.class);

		// Use the builder to provide a mock McpResource annotation
		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Content with exchange for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithUriVariables() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithUriVariables", String.class,
				String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("users/123/posts/456");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("User: 123, Post: 456");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithRequestParameterAsync() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithRequestAsync",
				ReadResourceRequest.class);

		// Provide a mock McpResource annotation since the method doesn't have one
		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Async content for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithExchangeAndRequestParametersAsync() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithExchangeAsync",
				McpAsyncServerExchange.class, ReadResourceRequest.class);

		// Use the builder to provide a mock McpResource annotation
		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Async content with exchange for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithUriVariablesAsync() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithUriVariablesAsync", String.class,
				String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("async/users/123/posts/456");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Async User: 123, Post: 456");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithStringAsync() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getSingleStringAsync", ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Async single string for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithTextContentTypeAsync() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getStringWithTextContentTypeAsync",
				ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Async text content type for test/resource");
			assertThat(textContent.mimeType()).isEqualTo("text/plain");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithBlobContentTypeAsync() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getStringWithBlobContentTypeAsync",
				ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(BlobResourceContents.class);
			BlobResourceContents blobContent = (BlobResourceContents) result.contents().get(0);
			assertThat(blobContent.blob()).isEqualTo("Async blob content type for test/resource");
			assertThat(blobContent.mimeType()).isEqualTo("application/octet-stream");
		}).verifyComplete();
	}

	@Test
	public void testInvalidReturnType() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("invalidReturnType", ReadResourceRequest.class);

		assertThatThrownBy(() -> AsyncMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	@Test
	public void testInvalidMonoReturnType() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("invalidMonoReturnType", ReadResourceRequest.class);

		assertThatThrownBy(() -> AsyncMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	@Test
	public void testInvalidUriVariableParameters() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithUriVariables", String.class,
				String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		// Create a mock annotation with a different URI template that has more
		// variables
		// than the method has parameters
		McpResource mockResourceAnnotation = new McpResource() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return McpResource.class;
			}

			@Override
			public String uri() {
				return "users/{userId}/posts/{postId}/comments/{commentId}";
			}

			@Override
			public String name() {
				return "";
			}

			@Override
			public String title() {
				return "";
			}

			@Override
			public String description() {
				return "";
			}

			@Override
			public String mimeType() {
				return "";
			}

			@Override
			public McpAnnotations annotations() {
				return new McpAnnotations() {
					@Override
					public Class<? extends java.lang.annotation.Annotation> annotationType() {
						return McpAnnotations.class;
					}

					@Override
					public Role[] audience() {
						return new Role[] { Role.USER };
					}

					@Override
					public String lastModified() {
						return "";
					}

					@Override
					public double priority() {
						return 0.5;
					}
				};
			}

			@Override
			public Class<? extends MetaProvider> metaProvider() {
				return DefaultMetaProvider.class;
			}
		};

		assertThatThrownBy(() -> AsyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(mockResourceAnnotation))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have parameters for all URI variables");
	}

	@Test
	public void testNullRequest() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithRequest", ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, null);

		StepVerifier.create(resultMono)
			.expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
					&& throwable.getMessage().contains("Request must not be null"))
			.verify();
	}

	@Test
	public void testMethodInvocationError() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithRequest", ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		// Create a request with a URI that will cause the URI template extraction to
		// fail
		ReadResourceRequest request = new ReadResourceRequest("invalid:uri");

		// Mock the URI template manager to throw an exception when extracting variables
		McpUriTemplateManager mockUriTemplateManager = new McpUriTemplateManager() {
			@Override
			public List<String> getVariableNames() {
				return List.of();
			}

			@Override
			public Map<String, String> extractVariableValues(String uri) {
				throw new RuntimeException("Simulated extraction error");
			}

			@Override
			public boolean matches(String uri) {
				return false;
			}

			@Override
			public boolean isUriTemplate(String uri) {
				return uri != null && uri.contains("{");
			}
		};

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callbackWithMockTemplate = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.uriTemplateManagerFactory(new McpUriTemplateManagerFactory() {
				public McpUriTemplateManager create(String uriTemplate) {
					return mockUriTemplateManager;
				};
			})
			.build();

		Mono<ReadResourceResult> resultMono = callbackWithMockTemplate.apply(exchange, request);

		StepVerifier.create(resultMono)
			.expectErrorMatches(throwable -> throwable instanceof McpError
					&& throwable.getMessage().contains("Error invoking resource method"))
			.verify();
	}

	// Tests for @McpProgressToken functionality
	@Test
	public void testCallbackWithProgressToken() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithProgressToken", String.class,
				ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn("progress-123");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Content with progress token: progress-123 for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithProgressTokenAsync() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithProgressTokenAsync", String.class,
				ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn("progress-456");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text())
				.isEqualTo("Async content with progress token: progress-456 for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithProgressTokenNull() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithProgressToken", String.class,
				ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn(null);

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Content with progress token: null for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithProgressTokenOnly() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithProgressTokenOnly", String.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn("progress-789");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Content with only progress token: progress-789");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithProgressTokenAndUriVariables() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithProgressTokenAndUriVariables",
				String.class, String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("users/123/posts/456");
		when(request.progressToken()).thenReturn("progress-abc");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("User: 123, Post: 456, Progress: progress-abc");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithExchangeAndProgressToken() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithExchangeAndProgressToken",
				McpAsyncServerExchange.class, String.class, ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn("progress-def");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text())
				.isEqualTo("Async content with exchange and progress token: progress-def for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMultipleProgressTokens() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithMultipleProgressTokens", String.class,
				String.class, ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn("progress-first");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			// Both progress tokens should receive the same value from the request
			assertThat(textContent.text())
				.isEqualTo("Content with progress tokens: progress-first and progress-first for test/resource");
		}).verifyComplete();
	}

	// Tests for @McpMeta functionality
	@Test
	public void testCallbackWithMeta() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithMeta", McpMeta.class,
				ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(Map.of("testKey", "testValue"));

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Content with meta: testValue for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMetaAsync() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithMetaAsync", McpMeta.class,
				ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(Map.of("testKey", "asyncValue"));

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Async content with meta: asyncValue for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMetaNull() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithMeta", McpMeta.class,
				ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(null);

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Content with meta: null for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMetaOnly() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithMetaOnly", McpMeta.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(Map.of("testKey", "metaOnlyValue"));

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Content with only meta: metaOnlyValue");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMetaAndUriVariables() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithMetaAndUriVariables", McpMeta.class,
				String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("users/123/posts/456");
		when(request.meta()).thenReturn(Map.of("testKey", "uriMetaValue"));

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("User: 123, Post: 456, Meta: uriMetaValue");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithExchangeAndMeta() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithExchangeAndMeta",
				McpAsyncServerExchange.class, McpMeta.class, ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(Map.of("testKey", "exchangeMetaValue"));

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text())
				.isEqualTo("Async content with exchange and meta: exchangeMetaValue for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMetaAndMixedParams() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithMetaAndMixedParams", McpMeta.class,
				String.class, ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(Map.of("testKey", "mixedMetaValue"));
		when(request.progressToken()).thenReturn("mixedProgress");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text())
				.isEqualTo("Content with meta: mixedMetaValue and progress: mixedProgress for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithMultipleMetas() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithMultipleMetas", McpMeta.class,
				McpMeta.class, ReadResourceRequest.class);

		// This should throw an exception during callback creation due to multiple
		// McpMeta parameters
		assertThatThrownBy(() -> AsyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one McpMeta parameter");
	}

	@Test
	public void testNewMethodInvocationError() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getFailingResource", ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("failing-resource://resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		// The new error handling should throw McpError instead of custom exceptions
		StepVerifier.create(resultMono)
			.expectErrorMatches(throwable -> throwable instanceof McpError
					&& throwable.getMessage().contains("Error invoking resource method"))
			.verify();
	}

	@Test
	public void testInvalidSyncExchangeParameter() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("invalidSyncExchangeParameter",
				McpSyncServerExchange.class, ReadResourceRequest.class);

		// Should fail during callback creation due to parameter validation
		assertThatThrownBy(() -> AsyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Method parameters must be exchange, ReadResourceRequest, String, McpMeta, or @McpProgressToken")
			.hasMessageContaining("McpSyncServerExchange");
	}

	@Test
	public void testCallbackWithAsyncRequestContext() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithAsyncRequestContext",
				McpAsyncRequestContext.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Async content with async context for test/resource");
		}).verifyComplete();
	}

	@Test
	public void testCallbackWithAsyncRequestContextAndUriVariables() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithAsyncRequestContextAndUriVariables",
				McpAsyncRequestContext.class, String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("users/123/posts/456");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Async User: 123, Post: 456 with async context");
		}).verifyComplete();
	}

	@Test
	public void testDuplicateAsyncRequestContextParameters() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("duplicateAsyncRequestContextParameters",
				McpAsyncRequestContext.class, McpAsyncRequestContext.class);

		assertThatThrownBy(() -> AsyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one request context parameter");
	}

	@Test
	public void testInvalidSyncRequestContextInAsyncMethod() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("invalidSyncRequestContextInAsyncMethod",
				McpSyncRequestContext.class);

		assertThatThrownBy(() -> AsyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Sync complete methods should use McpSyncRequestContext instead of McpAsyncRequestContext parameter");
	}

	@Test
	public void testCallbackWithTransportContextParameter() throws Exception {
		TestAsyncResourceProvider provider = new TestAsyncResourceProvider();
		Method method = TestAsyncResourceProvider.class.getMethod("getResourceWithTransportContext",
				McpTransportContext.class, ReadResourceRequest.class);

		BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> callback = AsyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext transportContext = mock(McpTransportContext.class);
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		when(exchange.transportContext()).thenReturn(transportContext);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		Mono<ReadResourceResult> resultMono = callback.apply(exchange, request);

		StepVerifier.create(resultMono).assertNext(result -> {
			assertThat(result).isNotNull();
			assertThat(result.contents()).hasSize(1);
			assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
			TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
			assertThat(textContent.text()).isEqualTo("Content with transport context for test/resource");
		}).verifyComplete();
	}

	private static class TestAsyncResourceProvider {

		// Regular return types (will be wrapped in Mono by the callback)
		public ReadResourceResult getResourceWithRequest(ReadResourceRequest request) {
			return new ReadResourceResult(
					List.of(new TextResourceContents(request.uri(), "text/plain", "Content for " + request.uri())));
		}

		// Methods for testing @McpProgressToken
		public ReadResourceResult getResourceWithProgressToken(@McpProgressToken String progressToken,
				ReadResourceRequest request) {
			String content = "Content with progress token: " + progressToken + " for " + request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		public Mono<ReadResourceResult> getResourceWithProgressTokenAsync(@McpProgressToken String progressToken,
				ReadResourceRequest request) {
			String content = "Async content with progress token: " + progressToken + " for " + request.uri();
			return Mono
				.just(new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content))));
		}

		public ReadResourceResult getResourceWithProgressTokenOnly(@McpProgressToken String progressToken) {
			String content = "Content with only progress token: " + progressToken;
			return new ReadResourceResult(List.of(new TextResourceContents("test://resource", "text/plain", content)));
		}

		@McpResource(uri = "users/{userId}/posts/{postId}")
		public ReadResourceResult getResourceWithProgressTokenAndUriVariables(@McpProgressToken String progressToken,
				String userId, String postId) {
			String content = "User: " + userId + ", Post: " + postId + ", Progress: " + progressToken;
			return new ReadResourceResult(
					List.of(new TextResourceContents("users/" + userId + "/posts/" + postId, "text/plain", content)));
		}

		public Mono<ReadResourceResult> getResourceWithExchangeAndProgressToken(McpAsyncServerExchange exchange,
				@McpProgressToken String progressToken, ReadResourceRequest request) {
			String content = "Async content with exchange and progress token: " + progressToken + " for "
					+ request.uri();
			return Mono
				.just(new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content))));
		}

		public ReadResourceResult getResourceWithMultipleProgressTokens(@McpProgressToken String progressToken1,
				@McpProgressToken String progressToken2, ReadResourceRequest request) {
			// This should only use the first progress token
			String content = "Content with progress tokens: " + progressToken1 + " and " + progressToken2 + " for "
					+ request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		public ReadResourceResult getResourceWithExchange(McpAsyncServerExchange exchange,
				ReadResourceRequest request) {
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain",
					"Content with exchange for " + request.uri())));
		}

		public ReadResourceResult getResourceWithUri(String uri) {
			return new ReadResourceResult(
					List.of(new TextResourceContents(uri, "text/plain", "Content from URI: " + uri)));
		}

		@McpResource(uri = "users/{userId}/posts/{postId}")
		public ReadResourceResult getResourceWithUriVariables(String userId, String postId) {
			return new ReadResourceResult(List.of(new TextResourceContents("users/" + userId + "/posts/" + postId,
					"text/plain", "User: " + userId + ", Post: " + postId)));
		}

		@McpResource(uri = "users/{userId}/profile")
		public ReadResourceResult getResourceWithExchangeAndUriVariable(McpAsyncServerExchange exchange,
				String userId) {
			return new ReadResourceResult(List.of(new TextResourceContents("users/" + userId + "/profile", "text/plain",
					"Profile for user: " + userId)));
		}

		// Mono return types
		public Mono<ReadResourceResult> getResourceWithRequestAsync(ReadResourceRequest request) {
			return Mono.just(new ReadResourceResult(List
				.of(new TextResourceContents(request.uri(), "text/plain", "Async content for " + request.uri()))));
		}

		public Mono<ReadResourceResult> getResourceWithExchangeAsync(McpAsyncServerExchange exchange,
				ReadResourceRequest request) {
			return Mono.just(new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain",
					"Async content with exchange for " + request.uri()))));
		}

		@McpResource(uri = "async/users/{userId}/posts/{postId}")
		public Mono<ReadResourceResult> getResourceWithUriVariablesAsync(String userId, String postId) {
			return Mono.just(new ReadResourceResult(
					List.of(new TextResourceContents("async/users/" + userId + "/posts/" + postId, "text/plain",
							"Async User: " + userId + ", Post: " + postId))));
		}

		public Mono<List<ResourceContents>> getResourceContentsListAsync(ReadResourceRequest request) {
			return Mono.just(List
				.of(new TextResourceContents(request.uri(), "text/plain", "Async content list for " + request.uri())));
		}

		public Mono<String> getSingleStringAsync(ReadResourceRequest request) {
			return Mono.just("Async single string for " + request.uri());
		}

		@McpResource(uri = "text-content://async-resource", mimeType = "text/plain")
		public Mono<String> getStringWithTextContentTypeAsync(ReadResourceRequest request) {
			return Mono.just("Async text content type for " + request.uri());
		}

		@McpResource(uri = "blob-content://async-resource", mimeType = "application/octet-stream")
		public Mono<String> getStringWithBlobContentTypeAsync(ReadResourceRequest request) {
			return Mono.just("Async blob content type for " + request.uri());
		}

		public void invalidReturnType(ReadResourceRequest request) {
			// Invalid return type
		}

		public Mono<Void> invalidMonoReturnType(ReadResourceRequest request) {
			return Mono.empty();
		}

		public Mono<ReadResourceResult> invalidParameters(int value) {
			return Mono.just(new ReadResourceResult(List.of()));
		}

		public Mono<ReadResourceResult> tooManyParameters(McpAsyncServerExchange exchange, ReadResourceRequest request,
				String extraParam) {
			return Mono.just(new ReadResourceResult(List.of()));
		}

		public Mono<ReadResourceResult> invalidParameterType(Object invalidParam) {
			return Mono.just(new ReadResourceResult(List.of()));
		}

		public Mono<ReadResourceResult> duplicateExchangeParameters(McpAsyncServerExchange exchange1,
				McpAsyncServerExchange exchange2) {
			return Mono.just(new ReadResourceResult(List.of()));
		}

		public Mono<ReadResourceResult> duplicateRequestParameters(ReadResourceRequest request1,
				ReadResourceRequest request2) {
			return Mono.just(new ReadResourceResult(List.of()));
		}

		// Methods for testing @McpMeta
		public ReadResourceResult getResourceWithMeta(McpMeta meta, ReadResourceRequest request) {
			String metaValue = (String) meta.get("testKey");
			String content = "Content with meta: " + metaValue + " for " + request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		public Mono<ReadResourceResult> getResourceWithMetaAsync(McpMeta meta, ReadResourceRequest request) {
			String metaValue = (String) meta.get("testKey");
			String content = "Async content with meta: " + metaValue + " for " + request.uri();
			return Mono
				.just(new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content))));
		}

		public ReadResourceResult getResourceWithMetaOnly(McpMeta meta) {
			String metaValue = (String) meta.get("testKey");
			String content = "Content with only meta: " + metaValue;
			return new ReadResourceResult(List.of(new TextResourceContents("test://resource", "text/plain", content)));
		}

		@McpResource(uri = "users/{userId}/posts/{postId}")
		public ReadResourceResult getResourceWithMetaAndUriVariables(McpMeta meta, String userId, String postId) {
			String metaValue = (String) meta.get("testKey");
			String content = "User: " + userId + ", Post: " + postId + ", Meta: " + metaValue;
			return new ReadResourceResult(
					List.of(new TextResourceContents("users/" + userId + "/posts/" + postId, "text/plain", content)));
		}

		public Mono<ReadResourceResult> getResourceWithExchangeAndMeta(McpAsyncServerExchange exchange, McpMeta meta,
				ReadResourceRequest request) {
			String metaValue = (String) meta.get("testKey");
			String content = "Async content with exchange and meta: " + metaValue + " for " + request.uri();
			return Mono
				.just(new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content))));
		}

		public ReadResourceResult getResourceWithMetaAndMixedParams(McpMeta meta,
				@McpProgressToken String progressToken, ReadResourceRequest request) {
			String metaValue = (String) meta.get("testKey");
			String content = "Content with meta: " + metaValue + " and progress: " + progressToken + " for "
					+ request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		public ReadResourceResult getResourceWithMultipleMetas(McpMeta meta1, McpMeta meta2,
				ReadResourceRequest request) {
			// This should cause a validation error during callback creation
			String content = "Content with multiple metas for " + request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		@McpResource(uri = "failing-resource://resource", description = "A resource that throws an exception")
		public Mono<ReadResourceResult> getFailingResource(ReadResourceRequest request) {
			throw new RuntimeException("Test exception");
		}

		// Invalid parameter types for async methods
		public Mono<ReadResourceResult> invalidSyncExchangeParameter(McpSyncServerExchange exchange,
				ReadResourceRequest request) {
			return Mono.just(new ReadResourceResult(List.of()));
		}

		public Mono<ReadResourceResult> getResourceWithTransportContext(McpTransportContext context,
				ReadResourceRequest request) {
			return Mono.just(new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain",
					"Content with transport context for " + request.uri()))));
		}

		public Mono<ReadResourceResult> getResourceWithAsyncRequestContext(McpAsyncRequestContext context) {
			ReadResourceRequest request = (ReadResourceRequest) context.request();
			return Mono.just(new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain",
					"Async content with async context for " + request.uri()))));
		}

		@McpResource(uri = "users/{userId}/posts/{postId}")
		public Mono<ReadResourceResult> getResourceWithAsyncRequestContextAndUriVariables(
				McpAsyncRequestContext context, String userId, String postId) {
			ReadResourceRequest request = (ReadResourceRequest) context.request();
			return Mono.just(new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain",
					"Async User: " + userId + ", Post: " + postId + " with async context"))));
		}

		public Mono<ReadResourceResult> duplicateAsyncRequestContextParameters(McpAsyncRequestContext context1,
				McpAsyncRequestContext context2) {
			return Mono.just(new ReadResourceResult(List.of()));
		}

		public Mono<ReadResourceResult> invalidSyncRequestContextInAsyncMethod(McpSyncRequestContext context) {
			return Mono.just(new ReadResourceResult(List.of()));
		}

	}

}
