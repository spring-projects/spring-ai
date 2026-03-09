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

package org.springframework.ai.mcp.annotation.method.resource;

import java.lang.reflect.Method;
import java.util.List;
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
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

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
 * Tests for {@link SyncMcpResourceMethodCallback}.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 */
public class SyncMcpResourceMethodCallbackTests {

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
				return "testResource";
			}

			@Override
			public String title() {
				return "";
			}

			@Override
			public String description() {
				return "Test resource description";
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
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithRequest", ReadResourceRequest.class);

		// Provide a mock McpResource annotation since the method doesn't have one
		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content for test/resource");
	}

	@Test
	public void testCallbackWithExchangeAndRequestParameters() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithExchange", McpSyncServerExchange.class,
				ReadResourceRequest.class);

		// Use the builder to provide a mock McpResource annotation
		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with exchange for test/resource");
	}

	@Test
	public void testCallbackWithUriParameter() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithUri", String.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content from URI: test/resource");
	}

	@Test
	public void testCallbackWithUriVariables() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithUriVariables", String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("users/123/posts/456");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("User: 123, Post: 456");
	}

	@Test
	public void testCallbackWithExchangeAndUriVariable() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithExchangeAndUriVariable",
				McpSyncServerExchange.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("users/789/profile");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Profile for user: 789");
	}

	@Test
	public void testCallbackWithResourceContentsList() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceContentsList", ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content list for test/resource");
	}

	@Test
	public void testCallbackWithStringList() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getStringList", ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(2);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent1 = (TextResourceContents) result.contents().get(0);
		TextResourceContents textContent2 = (TextResourceContents) result.contents().get(1);
		assertThat(textContent1.text()).isEqualTo("String 1 for test/resource");
		assertThat(textContent2.text()).isEqualTo("String 2 for test/resource");
	}

	@Test
	public void testCallbackWithSingleResourceContents() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getSingleResourceContents", ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Single resource content for test/resource");
	}

	@Test
	public void testCallbackWithSingleString() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getSingleString", ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Single string for test/resource");
	}

	@Test
	public void testInvalidReturnType() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("invalidReturnType", ReadResourceRequest.class);

		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	@Test
	public void testInvalidUriVariableParameters() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithUriVariables", String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		// Create a mock annotation with a different URI template that has more variables
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
				return "testResourceWithExtraVariables";
			}

			@Override
			public String title() {
				return "";
			}

			@Override
			public String description() {
				return "Test resource with extra URI variables";
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

		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(mockResourceAnnotation))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have parameters for all URI variables");
	}

	@Test
	public void testCallbackWithStringAndTextContentType() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getStringWithTextContentType", ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Text content type for test/resource");
		assertThat(textContent.mimeType()).isEqualTo("text/plain");
	}

	@Test
	public void testCallbackWithStringAndBlobContentType() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getStringWithBlobContentType", ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(BlobResourceContents.class);
		BlobResourceContents blobContent = (BlobResourceContents) result.contents().get(0);
		assertThat(blobContent.blob()).isEqualTo("Blob content type for test/resource");
		assertThat(blobContent.mimeType()).isEqualTo("application/octet-stream");
	}

	@Test
	public void testCallbackWithStringListAndTextContentType() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getStringListWithTextContentType",
				ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(2);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent1 = (TextResourceContents) result.contents().get(0);
		TextResourceContents textContent2 = (TextResourceContents) result.contents().get(1);
		assertThat(textContent1.text()).isEqualTo("HTML text 1 for test/resource");
		assertThat(textContent2.text()).isEqualTo("HTML text 2 for test/resource");
		assertThat(textContent1.mimeType()).isEqualTo("text/html");
		assertThat(textContent2.mimeType()).isEqualTo("text/html");
	}

	@Test
	public void testCallbackWithStringListAndBlobContentType() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getStringListWithBlobContentType",
				ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(2);
		assertThat(result.contents().get(0)).isInstanceOf(BlobResourceContents.class);
		BlobResourceContents blobContent1 = (BlobResourceContents) result.contents().get(0);
		BlobResourceContents blobContent2 = (BlobResourceContents) result.contents().get(1);
		assertThat(blobContent1.blob()).isEqualTo("PNG blob 1 for test/resource");
		assertThat(blobContent2.blob()).isEqualTo("PNG blob 2 for test/resource");
		assertThat(blobContent1.mimeType()).isEqualTo("image/png");
		assertThat(blobContent2.mimeType()).isEqualTo("image/png");
	}

	@Test
	public void testInvalidParameters() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("invalidParameters", int.class);

		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	@Test
	public void testTooManyParameters() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("tooManyParameters", McpSyncServerExchange.class,
				ReadResourceRequest.class, String.class);

		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	@Test
	public void testInvalidParameterType() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("invalidParameterType", Object.class);

		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	@Test
	public void testDuplicateExchangeParameters() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("duplicateExchangeParameters", McpSyncServerExchange.class,
				McpSyncServerExchange.class);

		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	@Test
	public void testDuplicateRequestParameters() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("duplicateRequestParameters", ReadResourceRequest.class,
				ReadResourceRequest.class);

		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	@Test
	public void testMethodWithoutMcpResourceAnnotation() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		// Use a method that doesn't have the McpResource annotation
		Method method = TestResourceProvider.class.getMethod("getResourceWithRequest", ReadResourceRequest.class);

		// Create a callback without explicitly providing the annotation
		// This should now throw an exception since the method doesn't have the annotation
		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	// Tests for @McpProgressToken functionality
	@Test
	public void testCallbackWithProgressToken() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithProgressToken", String.class,
				ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn("progress-123");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with progress token: progress-123 for test/resource");
	}

	@Test
	public void testCallbackWithProgressTokenNull() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithProgressToken", String.class,
				ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn(null);

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with progress token: null for test/resource");
	}

	@Test
	public void testCallbackWithProgressTokenOnly() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithProgressTokenOnly", String.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn("progress-456");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with only progress token: progress-456");
	}

	@Test
	public void testCallbackWithProgressTokenAndUriVariables() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithProgressTokenAndUriVariables",
				String.class, String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("users/123/posts/456");
		when(request.progressToken()).thenReturn("progress-789");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("User: 123, Post: 456, Progress: progress-789");
	}

	@Test
	public void testCallbackWithExchangeAndProgressToken() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithExchangeAndProgressToken",
				McpSyncServerExchange.class, String.class, ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn("progress-abc");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text())
			.isEqualTo("Content with exchange and progress token: progress-abc for test/resource");
	}

	@Test
	public void testCallbackWithMultipleProgressTokens() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMultipleProgressTokens", String.class,
				String.class, ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.progressToken()).thenReturn("progress-first");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		// Both progress tokens should receive the same value from the request
		assertThat(textContent.text())
			.isEqualTo("Content with progress tokens: progress-first and progress-first for test/resource");
	}

	@Test
	public void testCallbackWithProgressTokenAndMixedParams() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithProgressTokenAndMixedParams", String.class,
				String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("users/john");
		when(request.progressToken()).thenReturn("progress-xyz");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("User: john, Progress: progress-xyz");
	}

	// Tests for McpMeta functionality
	@Test
	public void testCallbackWithMeta() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMeta", McpMeta.class,
				ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(java.util.Map.of("key", "meta-value-123"));

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with meta: meta-value-123 for test/resource");
	}

	@Test
	public void testCallbackWithMetaNull() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMeta", McpMeta.class,
				ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(null);

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with meta: null for test/resource");
	}

	@Test
	public void testCallbackWithMetaOnly() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMetaOnly", McpMeta.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(java.util.Map.of("key", "meta-value-456"));

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with only meta: meta-value-456");
	}

	@Test
	public void testCallbackWithMetaAndUriVariables() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMetaAndUriVariables", McpMeta.class,
				String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("users/123/posts/456");
		when(request.meta()).thenReturn(java.util.Map.of("key", "meta-value-789"));

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("User: 123, Post: 456, Meta: meta-value-789");
	}

	@Test
	public void testCallbackWithExchangeAndMeta() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithExchangeAndMeta",
				McpSyncServerExchange.class, McpMeta.class, ReadResourceRequest.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(java.util.Map.of("key", "meta-value-abc"));

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with exchange and meta: meta-value-abc for test/resource");
	}

	@Test
	public void testCallbackWithMetaAndMixedParams() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMetaAndMixedParams", McpMeta.class,
				String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("users/john");
		when(request.meta()).thenReturn(java.util.Map.of("key", "meta-value-xyz"));

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("User: john, Meta: meta-value-xyz");
	}

	@Test
	public void testCallbackWithMultipleMetas() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMultipleMetas", McpMeta.class,
				McpMeta.class, ReadResourceRequest.class);

		// This should throw an exception during callback creation due to multiple McpMeta
		// parameters
		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one McpMeta parameter");
	}

	@Test
	public void testMethodInvocationError() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getFailingResource", ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("failing-resource://resource");

		// The new error handling should throw McpError instead of custom exceptions
		assertThatThrownBy(() -> callback.apply(exchange, request)).isInstanceOf(McpError.class)
			.hasMessageContaining("Error invoking resource method");
	}

	@Test
	public void testInvalidAsyncExchangeParameter() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("invalidAsyncExchangeParameter",
				McpAsyncServerExchange.class, ReadResourceRequest.class);

		// Should fail during callback creation due to parameter validation
		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Method parameters must be exchange, ReadResourceRequest, String, McpMeta, or @McpProgressToken")
			.hasMessageContaining("McpAsyncServerExchange");
	}

	@Test
	public void testCallbackWithTransportContext() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithTransportContext",
				McpTransportContext.class, ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext transportContext = mock(McpTransportContext.class);
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		when(exchange.transportContext()).thenReturn(transportContext);
		ReadResourceRequest request = new ReadResourceRequest("transport-context://resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with transport context for transport-context://resource");
	}

	@Test
	public void testCallbackWithSyncRequestContext() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithSyncRequestContext",
				McpSyncRequestContext.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with sync context for test/resource");
	}

	@Test
	public void testCallbackWithSyncRequestContextAndUriVariables() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithSyncRequestContextAndUriVariables",
				McpSyncRequestContext.class, String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpSyncServerExchange, ReadResourceRequest, ReadResourceResult> callback = SyncMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("users/123/posts/456");

		ReadResourceResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("User: 123, Post: 456 with sync context");
	}

	@Test
	public void testDuplicateSyncRequestContextParameters() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("duplicateSyncRequestContextParameters",
				McpSyncRequestContext.class, McpSyncRequestContext.class);

		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method cannot have more than one request context parameter");
	}

	@Test
	public void testInvalidAsyncRequestContextInSyncMethod() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("invalidAsyncRequestContextInSyncMethod",
				McpAsyncRequestContext.class);

		assertThatThrownBy(() -> SyncMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Async complete methods should use McpAsyncRequestContext instead of McpSyncRequestContext parameter");
	}

	private static class TestResourceProvider {

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

		public ReadResourceResult getResourceWithExchangeAndProgressToken(McpSyncServerExchange exchange,
				@McpProgressToken String progressToken, ReadResourceRequest request) {
			String content = "Content with exchange and progress token: " + progressToken + " for " + request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		public ReadResourceResult getResourceWithMultipleProgressTokens(@McpProgressToken String progressToken1,
				@McpProgressToken String progressToken2, ReadResourceRequest request) {
			// This should only use the first progress token
			String content = "Content with progress tokens: " + progressToken1 + " and " + progressToken2 + " for "
					+ request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		@McpResource(uri = "users/{userId}")
		public ReadResourceResult getResourceWithProgressTokenAndMixedParams(@McpProgressToken String progressToken,
				String userId) {
			String content = "User: " + userId + ", Progress: " + progressToken;
			return new ReadResourceResult(List.of(new TextResourceContents("users/" + userId, "text/plain", content)));
		}

		// Methods for testing McpMeta
		public ReadResourceResult getResourceWithMeta(McpMeta meta, ReadResourceRequest request) {
			String content = "Content with meta: " + meta.get("key") + " for " + request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		public ReadResourceResult getResourceWithMetaOnly(McpMeta meta) {
			String content = "Content with only meta: " + meta.get("key");
			return new ReadResourceResult(List.of(new TextResourceContents("test://resource", "text/plain", content)));
		}

		@McpResource(uri = "users/{userId}/posts/{postId}")
		public ReadResourceResult getResourceWithMetaAndUriVariables(McpMeta meta, String userId, String postId) {
			String content = "User: " + userId + ", Post: " + postId + ", Meta: " + meta.get("key");
			return new ReadResourceResult(
					List.of(new TextResourceContents("users/" + userId + "/posts/" + postId, "text/plain", content)));
		}

		public ReadResourceResult getResourceWithExchangeAndMeta(McpSyncServerExchange exchange, McpMeta meta,
				ReadResourceRequest request) {
			String content = "Content with exchange and meta: " + meta.get("key") + " for " + request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		@McpResource(uri = "users/{userId}")
		public ReadResourceResult getResourceWithMetaAndMixedParams(McpMeta meta, String userId) {
			String content = "User: " + userId + ", Meta: " + meta.get("key");
			return new ReadResourceResult(List.of(new TextResourceContents("users/" + userId, "text/plain", content)));
		}

		public ReadResourceResult getResourceWithMultipleMetas(McpMeta meta1, McpMeta meta2,
				ReadResourceRequest request) {
			// This should cause a validation error
			String content = "Content with multiple metas";
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
		}

		public ReadResourceResult getResourceWithExchange(McpSyncServerExchange exchange, ReadResourceRequest request) {
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
		public ReadResourceResult getResourceWithExchangeAndUriVariable(McpSyncServerExchange exchange, String userId) {
			return new ReadResourceResult(List.of(new TextResourceContents("users/" + userId + "/profile", "text/plain",
					"Profile for user: " + userId)));
		}

		public List<ResourceContents> getResourceContentsList(ReadResourceRequest request) {
			return List.of(new TextResourceContents(request.uri(), "text/plain", "Content list for " + request.uri()));
		}

		public List<String> getStringList(ReadResourceRequest request) {
			return List.of("String 1 for " + request.uri(), "String 2 for " + request.uri());
		}

		public ResourceContents getSingleResourceContents(ReadResourceRequest request) {
			return new TextResourceContents(request.uri(), "text/plain",
					"Single resource content for " + request.uri());
		}

		public String getSingleString(ReadResourceRequest request) {
			return "Single string for " + request.uri();
		}

		@McpResource(uri = "text-content://resource", mimeType = "text/plain")
		public String getStringWithTextContentType(ReadResourceRequest request) {
			return "Text content type for " + request.uri();
		}

		@McpResource(uri = "blob-content://resource", mimeType = "application/octet-stream")
		public String getStringWithBlobContentType(ReadResourceRequest request) {
			return "Blob content type for " + request.uri();
		}

		@McpResource(uri = "text-list://resource", mimeType = "text/html")
		public List<String> getStringListWithTextContentType(ReadResourceRequest request) {
			return List.of("HTML text 1 for " + request.uri(), "HTML text 2 for " + request.uri());
		}

		@McpResource(uri = "blob-list://resource", mimeType = "image/png")
		public List<String> getStringListWithBlobContentType(ReadResourceRequest request) {
			return List.of("PNG blob 1 for " + request.uri(), "PNG blob 2 for " + request.uri());
		}

		public void invalidReturnType(ReadResourceRequest request) {
			// Invalid return type
		}

		public ReadResourceResult invalidParameters(int value) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult tooManyParameters(McpSyncServerExchange exchange, ReadResourceRequest request,
				String extraParam) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult invalidParameterType(Object invalidParam) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult duplicateExchangeParameters(McpSyncServerExchange exchange1,
				McpSyncServerExchange exchange2) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult duplicateRequestParameters(ReadResourceRequest request1,
				ReadResourceRequest request2) {
			return new ReadResourceResult(List.of());
		}

		@McpResource(uri = "failing-resource://resource", description = "A resource that throws an exception")
		public ReadResourceResult getFailingResource(ReadResourceRequest request) {
			throw new RuntimeException("Test exception");
		}

		// Invalid parameter types for sync methods
		public ReadResourceResult invalidAsyncExchangeParameter(McpAsyncServerExchange exchange,
				ReadResourceRequest request) {
			return new ReadResourceResult(List.of());
		}

		@McpResource(uri = "transport-context://resource", description = "A resource with transport context")
		public ReadResourceResult getResourceWithTransportContext(McpTransportContext context,
				ReadResourceRequest request) {
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain",
					"Content with transport context for " + request.uri())));
		}

		public ReadResourceResult getResourceWithSyncRequestContext(McpSyncRequestContext context) {
			ReadResourceRequest request = (ReadResourceRequest) context.request();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain",
					"Content with sync context for " + request.uri())));
		}

		@McpResource(uri = "users/{userId}/posts/{postId}")
		public ReadResourceResult getResourceWithSyncRequestContextAndUriVariables(McpSyncRequestContext context,
				String userId, String postId) {
			ReadResourceRequest request = (ReadResourceRequest) context.request();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain",
					"User: " + userId + ", Post: " + postId + " with sync context")));
		}

		public ReadResourceResult duplicateSyncRequestContextParameters(McpSyncRequestContext context1,
				McpSyncRequestContext context2) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult invalidAsyncRequestContextInSyncMethod(McpAsyncRequestContext context) {
			return new ReadResourceResult(List.of());
		}

		public Mono<ReadResourceResult> invalidSyncRequestContextInAsyncMethod(McpSyncRequestContext context) {
			return Mono.just(new ReadResourceResult(List.of()));
		}

	}

}
