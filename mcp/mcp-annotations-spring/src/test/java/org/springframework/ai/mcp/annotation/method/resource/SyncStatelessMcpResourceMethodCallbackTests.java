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
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.adapter.ResourceAdapter;
import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SyncStatelessMcpResourceMethodCallback}.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 */
public class SyncStatelessMcpResourceMethodCallbackTests {

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
		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content for test/resource");
	}

	@Test
	public void testCallbackWithContextAndRequestParameters() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithContext", McpTransportContext.class,
				ReadResourceRequest.class);
		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with context for test/resource");
	}

	@Test
	public void testCallbackWithUriParameter() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithUri", String.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("users/123/posts/456");

		ReadResourceResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("User: 123, Post: 456");
	}

	@Test
	public void testCallbackWithContextAndUriVariable() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithContextAndUriVariable",
				McpTransportContext.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("users/789/profile");

		ReadResourceResult result = callback.apply(context, request);

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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Single string for test/resource");
	}

	@Test
	public void testCallbackWithStringAndTextContentType() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getStringWithTextContentType", ReadResourceRequest.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test/resource");

		ReadResourceResult result = callback.apply(context, request);

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
	public void testInvalidReturnType() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("invalidReturnType", ReadResourceRequest.class);

		assertThatThrownBy(() -> SyncStatelessMcpResourceMethodCallback.builder().method(method).bean(provider).build())
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

		assertThatThrownBy(() -> SyncStatelessMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(mockResourceAnnotation))
			.build()).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Method must have parameters for all URI variables");
	}

	@Test
	public void testNullRequest() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getSingleString", ReadResourceRequest.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);

		assertThatThrownBy(() -> callback.apply(context, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Request must not be null");
	}

	@Test
	public void testIsExchangeOrContextType() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getSingleString", ReadResourceRequest.class);
		SyncStatelessMcpResourceMethodCallback callback = SyncStatelessMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		// Test that McpTransportContext is recognized as exchange type
		// Note: We need to use reflection to access the protected method for testing
		java.lang.reflect.Method isExchangeOrContextTypeMethod = SyncStatelessMcpResourceMethodCallback.class
			.getDeclaredMethod("isExchangeOrContextType", Class.class);
		isExchangeOrContextTypeMethod.setAccessible(true);

		assertThat((Boolean) isExchangeOrContextTypeMethod.invoke(callback, McpTransportContext.class)).isTrue();

		// Test that other types are not recognized as exchange type
		assertThat((Boolean) isExchangeOrContextTypeMethod.invoke(callback, String.class)).isFalse();
		assertThat((Boolean) isExchangeOrContextTypeMethod.invoke(callback, Integer.class)).isFalse();
		assertThat((Boolean) isExchangeOrContextTypeMethod.invoke(callback, Object.class)).isFalse();
	}

	@Test
	public void testMethodWithoutMcpResourceAnnotation() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		// Use a method that doesn't have the McpResource annotation
		Method method = TestResourceProvider.class.getMethod("getResourceWithRequest", ReadResourceRequest.class);

		// Create a callback without explicitly providing the annotation
		// This should now throw an exception since the method doesn't have the annotation
		assertThatThrownBy(() -> SyncStatelessMcpResourceMethodCallback.builder().method(method).bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("URI must not be null or empty");
	}

	@Test
	public void testBuilderValidation() {
		TestResourceProvider provider = new TestResourceProvider();

		// Test null method
		assertThatThrownBy(() -> SyncStatelessMcpResourceMethodCallback.builder().bean(provider).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("Method must not be null");

		// Test null bean
		assertThatThrownBy(() -> SyncStatelessMcpResourceMethodCallback.builder()
			.method(TestResourceProvider.class.getMethod("getSingleString", ReadResourceRequest.class))
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessage("Bean must not be null");
	}

	@Test
	public void testUriVariableExtraction() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithUriVariables", String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);

		// Test with mismatched URI that doesn't contain expected variables
		ReadResourceRequest invalidRequest = new ReadResourceRequest("invalid/uri/format");

		assertThatThrownBy(() -> callback.apply(context, invalidRequest)).isInstanceOf(McpError.class)
			.hasMessageContaining("Failed to extract all URI variables from request URI: invalid/uri/format.");
	}

	// Tests for @McpMeta functionality
	@Test
	public void testCallbackWithMeta() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMeta", McpMeta.class,
				ReadResourceRequest.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(Map.of("testKey", "testValue"));

		ReadResourceResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with meta: testValue for test/resource");
	}

	@Test
	public void testCallbackWithMetaNull() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMeta", McpMeta.class,
				ReadResourceRequest.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(null);

		ReadResourceResult result = callback.apply(context, request);

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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(Map.of("testKey", "metaOnlyValue"));

		ReadResourceResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with only meta: metaOnlyValue");
	}

	@Test
	public void testCallbackWithMetaAndUriVariables() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMetaAndUriVariables", McpMeta.class,
				String.class, String.class);
		McpResource resourceAnnotation = method.getAnnotation(McpResource.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("users/123/posts/456");
		when(request.meta()).thenReturn(Map.of("testKey", "uriMetaValue"));

		ReadResourceResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("User: 123, Post: 456, Meta: uriMetaValue");
	}

	@Test
	public void testCallbackWithContextAndMeta() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithContextAndMeta", McpTransportContext.class,
				McpMeta.class, ReadResourceRequest.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(Map.of("testKey", "contextMetaValue"));

		ReadResourceResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text()).isEqualTo("Content with context and meta: contextMetaValue for test/resource");
	}

	@Test
	public void testCallbackWithMetaAndMixedParams() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMetaAndMixedParams", McpMeta.class,
				String.class, ReadResourceRequest.class);

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = mock(ReadResourceRequest.class);
		when(request.uri()).thenReturn("test/resource");
		when(request.meta()).thenReturn(Map.of("testKey", "mixedMetaValue"));
		when(request.progressToken()).thenReturn("mixedProgress");

		ReadResourceResult result = callback.apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		TextResourceContents textContent = (TextResourceContents) result.contents().get(0);
		assertThat(textContent.text())
			.isEqualTo("Content with meta: mixedMetaValue and progress: mixedProgress for test/resource");
	}

	@Test
	public void testCallbackWithMultipleMetas() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("getResourceWithMultipleMetas", McpMeta.class,
				McpMeta.class, ReadResourceRequest.class);

		// This should throw an exception during callback creation due to multiple
		// McpMeta parameters
		assertThatThrownBy(() -> SyncStatelessMcpResourceMethodCallback.builder()
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

		BiFunction<McpTransportContext, ReadResourceRequest, ReadResourceResult> callback = SyncStatelessMcpResourceMethodCallback
			.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(resourceAnnotation))
			.build();

		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("failing-resource://resource");

		// The new error handling should throw McpError instead of custom exceptions
		assertThatThrownBy(() -> callback.apply(context, request)).isInstanceOf(McpError.class)
			.hasMessageContaining("Error invoking resource method");
	}

	@Test
	public void testInvalidSyncExchangeParameter() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("invalidSyncExchangeParameter",
				McpSyncServerExchange.class, ReadResourceRequest.class);

		// Should fail during callback creation due to parameter validation
		assertThatThrownBy(() -> SyncStatelessMcpResourceMethodCallback.builder()
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
	public void testInvalidAsyncExchangeParameter() throws Exception {
		TestResourceProvider provider = new TestResourceProvider();
		Method method = TestResourceProvider.class.getMethod("invalidAsyncExchangeParameter",
				McpAsyncServerExchange.class, ReadResourceRequest.class);

		// Should fail during callback creation due to parameter validation
		assertThatThrownBy(() -> SyncStatelessMcpResourceMethodCallback.builder()
			.method(method)
			.bean(provider)
			.resource(ResourceAdapter.asResource(createMockMcpResource()))
			.build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(
					"Method parameters must be exchange, ReadResourceRequest, String, McpMeta, or @McpProgressToken")
			.hasMessageContaining("McpAsyncServerExchange");
	}

	private static class TestResourceProvider {

		public ReadResourceResult getResourceWithRequest(ReadResourceRequest request) {
			return new ReadResourceResult(
					List.of(new TextResourceContents(request.uri(), "text/plain", "Content for " + request.uri())));
		}

		public ReadResourceResult getResourceWithContext(McpTransportContext context, ReadResourceRequest request) {
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain",
					"Content with context for " + request.uri())));
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
		public ReadResourceResult getResourceWithContextAndUriVariable(McpTransportContext context, String userId) {
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

		public ReadResourceResult tooManyParameters(McpTransportContext context, ReadResourceRequest request,
				String extraParam) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult invalidParameterType(Object invalidParam) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult duplicateContextParameters(McpTransportContext context1,
				McpTransportContext context2) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult duplicateRequestParameters(ReadResourceRequest request1,
				ReadResourceRequest request2) {
			return new ReadResourceResult(List.of());
		}

		// Methods for testing @McpMeta
		public ReadResourceResult getResourceWithMeta(McpMeta meta, ReadResourceRequest request) {
			String metaValue = (String) meta.get("testKey");
			String content = "Content with meta: " + metaValue + " for " + request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
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

		public ReadResourceResult getResourceWithContextAndMeta(McpTransportContext context, McpMeta meta,
				ReadResourceRequest request) {
			String metaValue = (String) meta.get("testKey");
			String content = "Content with context and meta: " + metaValue + " for " + request.uri();
			return new ReadResourceResult(List.of(new TextResourceContents(request.uri(), "text/plain", content)));
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
		public ReadResourceResult getFailingResource(ReadResourceRequest request) {
			throw new RuntimeException("Test exception");
		}

		// Invalid parameter types for stateless methods
		public ReadResourceResult invalidSyncExchangeParameter(McpSyncServerExchange exchange,
				ReadResourceRequest request) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult invalidAsyncExchangeParameter(McpAsyncServerExchange exchange,
				ReadResourceRequest request) {
			return new ReadResourceResult(List.of());
		}

	}

}
