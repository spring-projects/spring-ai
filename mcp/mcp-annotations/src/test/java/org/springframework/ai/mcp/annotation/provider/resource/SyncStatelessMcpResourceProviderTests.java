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

package org.springframework.ai.mcp.annotation.provider.resource;

import java.util.List;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SyncStatelessMcpResourceProvider}.
 *
 * @author Christian Tzolov
 */
public class SyncStatelessMcpResourceProviderTests {

	@Test
	void testConstructorWithNullResourceObjects() {
		assertThatThrownBy(() -> new SyncStatelessMcpResourceProvider(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("resourceObjects cannot be null");
	}

	@Test
	void testGetResourceSpecificationsWithSingleValidResource() {
		// Create a class with only one valid resource method
		class SingleValidResource {

			@McpResource(uri = "test://resource/{id}", name = "test-resource", description = "A test resource")
			public String testResource(String id) {
				return "Resource content for: " + id;
			}

		}

		SingleValidResource resourceObject = new SingleValidResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).isNotNull();
		assertThat(resourceSpecs).hasSize(0);

		List<SyncResourceTemplateSpecification> resourceTemplateSpecs = provider.getResourceTemplateSpecifications();

		assertThat(resourceTemplateSpecs).hasSize(1);

		SyncResourceTemplateSpecification resourceTemplateSpec = resourceTemplateSpecs.get(0);
		assertThat(resourceTemplateSpec.resourceTemplate().uriTemplate()).isEqualTo("test://resource/{id}");
		assertThat(resourceTemplateSpec.resourceTemplate().name()).isEqualTo("test-resource");
		assertThat(resourceTemplateSpec.resourceTemplate().description()).isEqualTo("A test resource");
		assertThat(resourceTemplateSpec.readHandler()).isNotNull();

		// Test that the handler works
		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("test://resource/123");
		ReadResourceResult result = resourceTemplateSpec.readHandler().apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		ResourceContents content = result.contents().get(0);
		assertThat(content).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) content).text()).isEqualTo("Resource content for: 123");
	}

	@Test
	void testGetResourceSpecificationsWithCustomResourceName() {
		class CustomNameResource {

			@McpResource(uri = "custom://resource", name = "custom-name", description = "Custom named resource")
			public String methodWithDifferentName() {
				return "Custom resource content";
			}

		}

		CustomNameResource resourceObject = new CustomNameResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("custom-name");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Custom named resource");
	}

	@Test
	void testGetResourceSpecificationsWithDefaultResourceName() {
		class DefaultNameResource {

			@McpResource(uri = "default://resource", description = "Resource with default name")
			public String defaultNameMethod() {
				return "Default resource content";
			}

		}

		DefaultNameResource resourceObject = new DefaultNameResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("defaultNameMethod");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Resource with default name");
	}

	@Test
	void testGetResourceSpecificationsWithEmptyResourceName() {
		class EmptyNameResource {

			@McpResource(uri = "empty://resource", name = "", description = "Resource with empty name")
			public String emptyNameMethod() {
				return "Empty name resource content";
			}

		}

		EmptyNameResource resourceObject = new EmptyNameResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("emptyNameMethod");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Resource with empty name");
	}

	@Test
	void testGetResourceSpecificationsFiltersOutMonoReturnTypes() {
		class MonoReturnResource {

			@McpResource(uri = "mono://resource", name = "mono-resource", description = "Resource returning Mono")
			public Mono<String> monoResource() {
				return Mono.just("Mono resource content");
			}

			@McpResource(uri = "sync://resource", name = "sync-resource", description = "Synchronous resource")
			public String syncResource() {
				return "Sync resource content";
			}

		}

		MonoReturnResource resourceObject = new MonoReturnResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("sync-resource");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Synchronous resource");
	}

	@Test
	void testGetResourceSpecificationsWithMultipleResourceMethods() {
		class MultipleResourceMethods {

			@McpResource(uri = "first://resource", name = "resource1", description = "First resource")
			public String firstResource() {
				return "First resource content";
			}

			@McpResource(uri = "second://resource", name = "resource2", description = "Second resource")
			public String secondResource() {
				return "Second resource content";
			}

		}

		MultipleResourceMethods resourceObject = new MultipleResourceMethods();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(2);
		assertThat(resourceSpecs.get(0).resource().name()).isIn("resource1", "resource2");
		assertThat(resourceSpecs.get(1).resource().name()).isIn("resource1", "resource2");
		assertThat(resourceSpecs.get(0).resource().name()).isNotEqualTo(resourceSpecs.get(1).resource().name());
	}

	@Test
	void testGetResourceSpecificationsWithMultipleResourceObjects() {
		class FirstResourceObject {

			@McpResource(uri = "first://resource", name = "first-resource", description = "First resource")
			public String firstResource() {
				return "First resource content";
			}

		}

		class SecondResourceObject {

			@McpResource(uri = "second://resource", name = "second-resource", description = "Second resource")
			public String secondResource() {
				return "Second resource content";
			}

		}

		FirstResourceObject firstObject = new FirstResourceObject();
		SecondResourceObject secondObject = new SecondResourceObject();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(
				List.of(firstObject, secondObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(2);
		assertThat(resourceSpecs.get(0).resource().name()).isIn("first-resource", "second-resource");
		assertThat(resourceSpecs.get(1).resource().name()).isIn("first-resource", "second-resource");
		assertThat(resourceSpecs.get(0).resource().name()).isNotEqualTo(resourceSpecs.get(1).resource().name());
	}

	@Test
	void testGetResourceSpecificationsWithMixedMethods() {
		class MixedMethods {

			@McpResource(uri = "valid://resource", name = "valid-resource", description = "Valid resource")
			public String validResource() {
				return "Valid resource content";
			}

			public String nonAnnotatedMethod() {
				return "Non-annotated resource content";
			}

			@McpResource(uri = "mono://resource", name = "mono-resource", description = "Mono resource")
			public Mono<String> monoResource() {
				return Mono.just("Mono resource content");
			}

		}

		MixedMethods resourceObject = new MixedMethods();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("valid-resource");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Valid resource");
	}

	@Test
	void testGetResourceSpecificationsWithUriVariables() {
		class UriVariableResource {

			@McpResource(uri = "variable://resource/{id}/{type}", name = "variable-resource",
					description = "Resource with URI variables")
			public String variableResource(String id, String type) {
				return String.format("Resource content for id: %s, type: %s", id, type);
			}

		}

		UriVariableResource resourceObject = new UriVariableResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(0);

		var resourceTemplateSpecs = provider.getResourceTemplateSpecifications();

		assertThat(resourceTemplateSpecs).hasSize(1);
		assertThat(resourceTemplateSpecs.get(0).resourceTemplate().uriTemplate())
			.isEqualTo("variable://resource/{id}/{type}");
		assertThat(resourceTemplateSpecs.get(0).resourceTemplate().name()).isEqualTo("variable-resource");

		// Test that the handler works with URI variables
		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("variable://resource/123/document");
		ReadResourceResult result = resourceTemplateSpecs.get(0).readHandler().apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		ResourceContents content = result.contents().get(0);
		assertThat(content).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) content).text()).isEqualTo("Resource content for id: 123, type: document");
	}

	@Test
	void testGetResourceSpecificationsWithMimeType() {
		class MimeTypeResource {

			@McpResource(uri = "mime://resource", name = "mime-resource", description = "Resource with MIME type",
					mimeType = "application/json")
			public String mimeTypeResource() {
				return "{\"message\": \"JSON resource content\"}";
			}

		}

		MimeTypeResource resourceObject = new MimeTypeResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().mimeType()).isEqualTo("application/json");
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("mime-resource");
	}

	@Test
	void testGetResourceSpecificationsWithPrivateMethod() {
		class PrivateMethodResource {

			@McpResource(uri = "private://resource", name = "private-resource", description = "Private resource method")
			private String privateResource() {
				return "Private resource content";
			}

		}

		PrivateMethodResource resourceObject = new PrivateMethodResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("private-resource");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Private resource method");

		// Test that the handler works with private methods
		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("private://resource");
		ReadResourceResult result = resourceSpecs.get(0).readHandler().apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		ResourceContents content = result.contents().get(0);
		assertThat(content).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) content).text()).isEqualTo("Private resource content");
	}

	@Test
	void testGetResourceSpecificationsWithResourceContentsList() {
		class ResourceContentsListResource {

			@McpResource(uri = "list://resource", name = "list-resource", description = "Resource returning list")
			public List<String> listResource() {
				return List.of("First content", "Second content");
			}

		}

		ResourceContentsListResource resourceObject = new ResourceContentsListResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("list-resource");

		// Test that the handler works with list return type
		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("list://resource");
		ReadResourceResult result = resourceSpecs.get(0).readHandler().apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(2);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		assertThat(result.contents().get(1)).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) result.contents().get(0)).text()).isEqualTo("First content");
		assertThat(((TextResourceContents) result.contents().get(1)).text()).isEqualTo("Second content");
	}

	@Test
	void testGetResourceSpecificationsWithContextParameter() {
		class ContextParameterResource {

			@McpResource(uri = "context://resource", name = "context-resource",
					description = "Resource with context parameter")
			public String contextResource(McpTransportContext context, ReadResourceRequest request) {
				return "Resource with context: " + (context != null ? "present" : "null") + ", URI: " + request.uri();
			}

		}

		ContextParameterResource resourceObject = new ContextParameterResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("context-resource");

		// Test that the handler works with context parameter
		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("context://resource");
		ReadResourceResult result = resourceSpecs.get(0).readHandler().apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		ResourceContents content = result.contents().get(0);
		assertThat(content).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) content).text())
			.isEqualTo("Resource with context: present, URI: context://resource");
	}

	@Test
	void testGetResourceSpecificationsWithRequestParameter() {
		class RequestParameterResource {

			@McpResource(uri = "request://resource", name = "request-resource",
					description = "Resource with request parameter")
			public String requestResource(ReadResourceRequest request) {
				return "Resource for URI: " + request.uri();
			}

		}

		RequestParameterResource resourceObject = new RequestParameterResource();
		SyncStatelessMcpResourceProvider provider = new SyncStatelessMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("request-resource");

		// Test that the handler works with request parameter
		McpTransportContext context = mock(McpTransportContext.class);
		ReadResourceRequest request = new ReadResourceRequest("request://resource");
		ReadResourceResult result = resourceSpecs.get(0).readHandler().apply(context, request);

		assertThat(result).isNotNull();
		assertThat(result.contents()).hasSize(1);
		ResourceContents content = result.contents().get(0);
		assertThat(content).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) content).text()).isEqualTo("Resource for URI: request://resource");
	}

}
