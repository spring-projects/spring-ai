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

package org.springframework.ai.mcp.annotation.provider.resource;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceTemplateSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.context.MetaProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link SyncMcpResourceProvider}.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Craig Walls
 */
public class SyncMcpResourceProviderTests {

	@Test
	void testConstructorWithNullResourceObjects() {
		assertThatThrownBy(() -> new SyncMcpResourceProvider(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("resourceObjects cannot be null");
	}

	@Test
	void testGetResourceSpecificationsWithSingleValidResource() {
		// Create a class with only one valid sync resource method
		class SingleValidResource {

			@McpResource(uri = "test://resource/{id}", name = "test-resource", description = "A test resource")
			public String testResource(String id) {
				return "Resource content for: " + id;
			}

		}

		SingleValidResource resourceObject = new SingleValidResource();
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).isNotNull();
		assertThat(resourceSpecs).hasSize(0);

		List<SyncResourceTemplateSpecification> resourceTemplateSpecs = provider.getResourceTemplateSpecifications();

		SyncResourceTemplateSpecification resourceTemplateSpec = resourceTemplateSpecs.get(0);
		assertThat(resourceTemplateSpec.resourceTemplate().uriTemplate()).isEqualTo("test://resource/{id}");
		assertThat(resourceTemplateSpec.resourceTemplate().name()).isEqualTo("test-resource");
		assertThat(resourceTemplateSpec.resourceTemplate().description()).isEqualTo("A test resource");
		assertThat(resourceTemplateSpec.readHandler()).isNotNull();

		// Test that the handler works
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("test://resource/123");
		ReadResourceResult result = resourceTemplateSpec.readHandler().apply(exchange, request);

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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("emptyNameMethod");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Resource with empty name");
	}

	@Test
	void testGetResourceSpecificationsFiltersOutReactiveReturnTypes() {
		class MixedReturnResource {

			@McpResource(uri = "sync://resource", name = "sync-resource", description = "Synchronous resource")
			public String syncResource() {
				return "Sync resource content";
			}

			@McpResource(uri = "async://resource", name = "async-resource", description = "Asynchronous resource")
			public Mono<String> asyncResource() {
				return Mono.just("Async resource content");
			}

		}

		MixedReturnResource resourceObject = new MixedReturnResource();
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(firstObject, secondObject));

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

			@McpResource(uri = "async://resource", name = "async-resource", description = "Async resource")
			public Mono<String> asyncResource() {
				return Mono.just("Async resource content");
			}

		}

		MixedMethods resourceObject = new MixedMethods();
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(0);

		List<SyncResourceTemplateSpecification> resourceTemplateSpecs = provider.getResourceTemplateSpecifications();

		assertThat(resourceTemplateSpecs).hasSize(1);

		assertThat(resourceTemplateSpecs.get(0).resourceTemplate().uriTemplate())
			.isEqualTo("variable://resource/{id}/{type}");
		assertThat(resourceTemplateSpecs.get(0).resourceTemplate().name()).isEqualTo("variable-resource");

		// Test that the handler works with URI variables
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("variable://resource/123/document");
		ReadResourceResult result = resourceTemplateSpecs.get(0).readHandler().apply(exchange, request);

		assertThat(result.contents()).hasSize(1);
		ResourceContents content = result.contents().get(0);
		assertThat(content).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) content).text()).isEqualTo("Resource content for id: 123, type: document");
	}

	@Test
	void testGetResourceSpecificationsWithMeta() {
		class MetaResource {

			@McpResource(uri = "ui://test/view.html", name = "test-view", mimeType = "text/html;profile=mcp-app",
					metaProvider = ResourceMetaProvider.class)
			public String testView() {
				return "<html>test</html>";
			}

		}

		MetaResource resourceObject = new MetaResource();
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().mimeType()).isEqualTo("text/html;profile=mcp-app");
		assertThat(resourceSpecs.get(0).resource().meta()).isNotNull();
		assertThat(resourceSpecs.get(0).resource().meta()).containsKey("ui");

		@SuppressWarnings("unchecked")
		Map<String, Object> ui = (Map<String, Object>) resourceSpecs.get(0).resource().meta().get("ui");
		assertThat(ui).containsKey("csp");
	}

	@Test
	void testGetResourceSpecificationsWithEmptyMeta() {
		class NoMetaResource {

			@McpResource(uri = "no-meta://resource", name = "no-meta-resource", description = "Resource without meta")
			public String noMetaResource() {
				return "No meta content";
			}

		}

		NoMetaResource resourceObject = new NoMetaResource();
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().meta()).isNull();
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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("private-resource");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Private resource method");

		// Test that the handler works with private methods
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("private://resource");
		ReadResourceResult result = resourceSpecs.get(0).readHandler().apply(exchange, request);

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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("list-resource");

		// Test that the handler works with list return type
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("list://resource");
		ReadResourceResult result = resourceSpecs.get(0).readHandler().apply(exchange, request);

		assertThat(result.contents()).hasSize(2);
		assertThat(result.contents().get(0)).isInstanceOf(TextResourceContents.class);
		assertThat(result.contents().get(1)).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) result.contents().get(0)).text()).isEqualTo("First content");
		assertThat(((TextResourceContents) result.contents().get(1)).text()).isEqualTo("Second content");
	}

	@Test
	void testGetResourceSpecificationsWithExchangeParameter() {
		class ExchangeParameterResource {

			@McpResource(uri = "exchange://resource", name = "exchange-resource",
					description = "Resource with exchange parameter")
			public String exchangeResource(McpSyncServerExchange exchange, ReadResourceRequest request) {
				return "Resource with exchange: " + (exchange != null ? "present" : "null") + ", URI: " + request.uri();
			}

		}

		ExchangeParameterResource resourceObject = new ExchangeParameterResource();
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("exchange-resource");

		// Test that the handler works with exchange parameter
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("exchange://resource");
		ReadResourceResult result = resourceSpecs.get(0).readHandler().apply(exchange, request);

		assertThat(result.contents()).hasSize(1);
		ResourceContents content = result.contents().get(0);
		assertThat(content).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) content).text())
			.isEqualTo("Resource with exchange: present, URI: exchange://resource");
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
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("request-resource");

		// Test that the handler works with request parameter
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("request://resource");
		ReadResourceResult result = resourceSpecs.get(0).readHandler().apply(exchange, request);

		assertThat(result.contents()).hasSize(1);
		ResourceContents content = result.contents().get(0);
		assertThat(content).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) content).text()).isEqualTo("Resource for URI: request://resource");
	}

	@Test
	void testGetResourceSpecificationsWithNoParameters() {
		class NoParameterResource {

			@McpResource(uri = "no-param://resource", name = "no-param-resource",
					description = "Resource with no parameters")
			public String noParamResource() {
				return "No parameters needed";
			}

		}

		NoParameterResource resourceObject = new NoParameterResource();
		SyncMcpResourceProvider provider = new SyncMcpResourceProvider(List.of(resourceObject));

		List<SyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("no-param-resource");

		// Test that the handler works with no parameters
		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		ReadResourceRequest request = new ReadResourceRequest("no-param://resource");
		ReadResourceResult result = resourceSpecs.get(0).readHandler().apply(exchange, request);

		assertThat(result.contents()).hasSize(1);
		ResourceContents content = result.contents().get(0);
		assertThat(content).isInstanceOf(TextResourceContents.class);
		assertThat(((TextResourceContents) content).text()).isEqualTo("No parameters needed");
	}

	public static class ResourceMetaProvider implements MetaProvider {

		@Override
		public Map<String, Object> getMeta() {
			return Map.of("ui", Map.of("csp", Map.of("resourceDomains", List.of("https://unpkg.com"))));
		}

	}

}
