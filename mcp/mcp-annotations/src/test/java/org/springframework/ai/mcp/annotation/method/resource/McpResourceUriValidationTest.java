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

import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.Role;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.adapter.ResourceAdapter;
import org.springframework.ai.mcp.annotation.context.DefaultMetaProvider;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

/**
 * Simple test to verify that McpResourceMethodCallback requires a non-empty URI in the
 * McpResource annotation.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 */
public final class McpResourceUriValidationTest {

	private McpResourceUriValidationTest() {
	}

	// Mock McpResource annotation with empty URI
	private static McpResource createMockResourceWithEmptyUri() {
		return new McpResource() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return McpResource.class;
			}

			@Override
			public String uri() {
				return "";
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
	}

	// Mock McpResource annotation with non-empty URI
	private static McpResource createMockResourceWithValidUri() {
		return new McpResource() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return McpResource.class;
			}

			@Override
			public String uri() {
				return "valid://uri";
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
	}

	public static void main(String[] args) {
		TestResourceProvider provider = new TestResourceProvider();

		try {
			// Test 1: Method with valid annotation from the class
			Method validMethod = TestResourceProvider.class.getMethod("validMethod", ReadResourceRequest.class);
			McpResource validAnnotation = validMethod.getAnnotation(McpResource.class);

			System.out.println("Test 1: Method with valid annotation from the class");
			try {
				SyncMcpResourceMethodCallback.builder()
					.method(validMethod)
					.bean(provider)
					.resource(ResourceAdapter.asResource(validAnnotation))
					.build();
				System.out.println("  PASS: Successfully created callback with valid URI");
			}
			catch (IllegalArgumentException e) {
				System.out.println("  FAIL: " + e.getMessage());
			}

			// Test 2: Method with mock annotation with empty URI
			System.out.println("\nTest 2: Method with mock annotation with empty URI");
			try {
				SyncMcpResourceMethodCallback.builder()
					.method(validMethod)
					.bean(provider)
					.resource(ResourceAdapter.asResource(createMockResourceWithEmptyUri()))
					.build();
				System.out.println("  FAIL: Should have thrown exception for empty URI");
			}
			catch (IllegalArgumentException e) {
				System.out.println("  PASS: Correctly rejected empty URI: " + e.getMessage());
			}

			// Test 3: Method with mock annotation with valid URI
			System.out.println("\nTest 3: Method with mock annotation with valid URI");
			try {
				SyncMcpResourceMethodCallback.builder()
					.method(validMethod)
					.bean(provider)
					.resource(ResourceAdapter.asResource(createMockResourceWithValidUri()))
					.build();
				System.out.println("  PASS: Successfully created callback with valid URI");
			}
			catch (IllegalArgumentException e) {
				System.out.println("  FAIL: " + e.getMessage());
			}

			// Test 4: Method without annotation using createCallback
			Method methodWithoutAnnotation = TestResourceProvider.class.getMethod("methodWithoutAnnotation",
					ReadResourceRequest.class);
			System.out.println("\nTest 4: Method without annotation using createCallback");
			try {
				SyncMcpResourceMethodCallback.builder().method(methodWithoutAnnotation).bean(provider).build();
				System.out.println("  FAIL: Should have thrown exception for missing annotation");
			}
			catch (IllegalArgumentException e) {
				System.out.println("  PASS: Correctly rejected method without annotation: " + e.getMessage());
			}

			System.out.println("\nAll tests completed.");

		}
		catch (Exception e) {
			System.out.println("Unexpected error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// Test class with resource methods
	private static class TestResourceProvider {

		@McpResource(uri = "valid://uri")
		public ReadResourceResult validMethod(ReadResourceRequest request) {
			return new ReadResourceResult(List.of());
		}

		public ReadResourceResult methodWithoutAnnotation(ReadResourceRequest request) {
			return new ReadResourceResult(List.of());
		}

	}

}
