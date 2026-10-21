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

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.ResourceContents;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.ai.mcp.annotation.McpCsp;
import org.springframework.ai.mcp.annotation.McpPermission;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.PrefersBorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link AsyncMcpResourceProvider}.
 *
 * @author Christian Tzolov
 */
public class AsyncMcpResourceProviderTests {

	@Test
	void testConstructorWithNullResourceObjects() {
		assertThatThrownBy(() -> new AsyncMcpResourceProvider(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("resourceObjects cannot be null");
	}

	@Test
	void testGetResourceSpecificationsWithSingleValidResource() {
		// Create a class with only one valid async resource method
		class SingleValidResource {

			@McpResource(uri = "test://resource/{id}", name = "test-resource", description = "A test resource")
			public Mono<String> testResource(String id) {
				return Mono.just("Resource content for: " + id);
			}

		}

		SingleValidResource resourceObject = new SingleValidResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).isNotNull();
		assertThat(resourceSpecs).hasSize(0);

		var resourceTemplateSpecs = provider.getResourceTemplateSpecifications();

		AsyncResourceTemplateSpecification resourceSpec = resourceTemplateSpecs.get(0);
		assertThat(resourceSpec.resourceTemplate().uriTemplate()).isEqualTo("test://resource/{id}");
		assertThat(resourceSpec.resourceTemplate().name()).isEqualTo("test-resource");
		assertThat(resourceSpec.resourceTemplate().description()).isEqualTo("A test resource");
		assertThat(resourceSpec.readHandler()).isNotNull();

		// Test that the handler works
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = ReadResourceRequest.builder("test://resource/123").build();
		Mono<ReadResourceResult> result = resourceSpec.readHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(readResult -> {
			assertThat(readResult.contents()).hasSize(1);
			ResourceContents content = readResult.contents().get(0);
			assertThat(content).isInstanceOf(TextResourceContents.class);
			assertThat(((TextResourceContents) content).text()).isEqualTo("Resource content for: 123");
		}).verifyComplete();
	}

	@Test
	void testGetResourceSpecificationsWithCustomResourceName() {
		class CustomNameResource {

			@McpResource(uri = "custom://resource", name = "custom-name", description = "Custom named resource")
			public Mono<String> methodWithDifferentName() {
				return Mono.just("Custom resource content");
			}

		}

		CustomNameResource resourceObject = new CustomNameResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("custom-name");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Custom named resource");
	}

	@Test
	void testGetResourceSpecificationsWithDefaultResourceName() {
		class DefaultNameResource {

			@McpResource(uri = "default://resource", description = "Resource with default name")
			public Mono<String> defaultNameMethod() {
				return Mono.just("Default resource content");
			}

		}

		DefaultNameResource resourceObject = new DefaultNameResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("defaultNameMethod");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Resource with default name");
	}

	@Test
	void testGetResourceSpecificationsWithEmptyResourceName() {
		class EmptyNameResource {

			@McpResource(uri = "empty://resource", name = "", description = "Resource with empty name")
			public Mono<String> emptyNameMethod() {
				return Mono.just("Empty name resource content");
			}

		}

		EmptyNameResource resourceObject = new EmptyNameResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("emptyNameMethod");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Resource with empty name");
	}

	@Test
	void testGetResourceSpecificationsFiltersOutNonReactiveReturnTypes() {
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
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("async-resource");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Asynchronous resource");
	}

	@Test
	void testGetResourceSpecificationsWithMultipleResourceMethods() {
		class MultipleResourceMethods {

			@McpResource(uri = "first://resource", name = "resource1", description = "First resource")
			public Mono<String> firstResource() {
				return Mono.just("First resource content");
			}

			@McpResource(uri = "second://resource", name = "resource2", description = "Second resource")
			public Mono<String> secondResource() {
				return Mono.just("Second resource content");
			}

		}

		MultipleResourceMethods resourceObject = new MultipleResourceMethods();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(2);
		assertThat(resourceSpecs.get(0).resource().name()).isIn("resource1", "resource2");
		assertThat(resourceSpecs.get(1).resource().name()).isIn("resource1", "resource2");
		assertThat(resourceSpecs.get(0).resource().name()).isNotEqualTo(resourceSpecs.get(1).resource().name());
	}

	@Test
	void testGetResourceSpecificationsWithMultipleResourceObjects() {
		class FirstResourceObject {

			@McpResource(uri = "first://resource", name = "first-resource", description = "First resource")
			public Mono<String> firstResource() {
				return Mono.just("First resource content");
			}

		}

		class SecondResourceObject {

			@McpResource(uri = "second://resource", name = "second-resource", description = "Second resource")
			public Mono<String> secondResource() {
				return Mono.just("Second resource content");
			}

		}

		FirstResourceObject firstObject = new FirstResourceObject();
		SecondResourceObject secondObject = new SecondResourceObject();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(firstObject, secondObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(2);
		assertThat(resourceSpecs.get(0).resource().name()).isIn("first-resource", "second-resource");
		assertThat(resourceSpecs.get(1).resource().name()).isIn("first-resource", "second-resource");
		assertThat(resourceSpecs.get(0).resource().name()).isNotEqualTo(resourceSpecs.get(1).resource().name());
	}

	@Test
	void testGetResourceSpecificationsWithMixedMethods() {
		class MixedMethods {

			@McpResource(uri = "valid://resource", name = "valid-resource", description = "Valid resource")
			public Mono<String> validResource() {
				return Mono.just("Valid resource content");
			}

			public String nonAnnotatedMethod() {
				return "Non-annotated resource content";
			}

			@McpResource(uri = "sync://resource", name = "sync-resource", description = "Sync resource")
			public String syncResource() {
				return "Sync resource content";
			}

		}

		MixedMethods resourceObject = new MixedMethods();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("valid-resource");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Valid resource");
	}

	@Test
	void testGetResourceSpecificationsWithUriVariables() {
		class UriVariableResource {

			@McpResource(uri = "variable://resource/{id}/{type}", name = "variable-resource",
					description = "Resource with URI variables")
			public Mono<String> variableResource(String id, String type) {
				return Mono.just(String.format("Resource content for id: %s, type: %s", id, type));
			}

		}

		UriVariableResource resourceObject = new UriVariableResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();
		assertThat(resourceSpecs).hasSize(0);

		var resourceTemplateSpecs = provider.getResourceTemplateSpecifications();

		assertThat(resourceTemplateSpecs).hasSize(1);
		assertThat(resourceTemplateSpecs.get(0).resourceTemplate().uriTemplate())
			.isEqualTo("variable://resource/{id}/{type}");
		assertThat(resourceTemplateSpecs.get(0).resourceTemplate().name()).isEqualTo("variable-resource");

		// Test that the handler works with URI variables
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = ReadResourceRequest.builder("variable://resource/123/document").build();
		Mono<ReadResourceResult> result = resourceTemplateSpecs.get(0).readHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(readResult -> {
			assertThat(readResult.contents()).hasSize(1);
			ResourceContents content = readResult.contents().get(0);
			assertThat(content).isInstanceOf(TextResourceContents.class);
			assertThat(((TextResourceContents) content).text())
				.isEqualTo("Resource content for id: 123, type: document");
		}).verifyComplete();
	}

	@Test
	void testGetResourceSpecificationsWithMimeType() {
		class MimeTypeResource {

			@McpResource(uri = "mime://resource", name = "mime-resource", description = "Resource with MIME type",
					mimeType = "application/json")
			public Mono<String> mimeTypeResource() {
				return Mono.just("{\"message\": \"JSON resource content\"}");
			}

		}

		MimeTypeResource resourceObject = new MimeTypeResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().mimeType()).isEqualTo("application/json");
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("mime-resource");
	}

	@Test
	void testGetResourceSpecificationsWithPrivateMethod() {
		class PrivateMethodResource {

			@McpResource(uri = "private://resource", name = "private-resource", description = "Private resource method")
			private Mono<String> privateResource() {
				return Mono.just("Private resource content");
			}

		}

		PrivateMethodResource resourceObject = new PrivateMethodResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("private-resource");
		assertThat(resourceSpecs.get(0).resource().description()).isEqualTo("Private resource method");

		// Test that the handler works with private methods
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = ReadResourceRequest.builder("private://resource").build();
		Mono<ReadResourceResult> result = resourceSpecs.get(0).readHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(readResult -> {
			assertThat(readResult.contents()).hasSize(1);
			ResourceContents content = readResult.contents().get(0);
			assertThat(content).isInstanceOf(TextResourceContents.class);
			assertThat(((TextResourceContents) content).text()).isEqualTo("Private resource content");
		}).verifyComplete();
	}

	@Test
	void testGetResourceSpecificationsWithResourceContentsList() {
		class ResourceContentsListResource {

			@McpResource(uri = "list://resource", name = "list-resource", description = "Resource returning list")
			public Mono<List<String>> listResource() {
				return Mono.just(List.of("First content", "Second content"));
			}

		}

		ResourceContentsListResource resourceObject = new ResourceContentsListResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("list-resource");

		// Test that the handler works with list return type
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = ReadResourceRequest.builder("list://resource").build();
		Mono<ReadResourceResult> result = resourceSpecs.get(0).readHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(readResult -> {
			assertThat(readResult.contents()).hasSize(2);
			assertThat(readResult.contents().get(0)).isInstanceOf(TextResourceContents.class);
			assertThat(readResult.contents().get(1)).isInstanceOf(TextResourceContents.class);
			assertThat(((TextResourceContents) readResult.contents().get(0)).text()).isEqualTo("First content");
			assertThat(((TextResourceContents) readResult.contents().get(1)).text()).isEqualTo("Second content");
		}).verifyComplete();
	}

	@Test
	void testGetResourceSpecificationsWithExchangeParameter() {
		class ExchangeParameterResource {

			@McpResource(uri = "exchange://resource", name = "exchange-resource",
					description = "Resource with exchange parameter")
			public Mono<String> exchangeResource(McpAsyncServerExchange exchange, ReadResourceRequest request) {
				return Mono.just("Resource with exchange: " + (exchange != null ? "present" : "null") + ", URI: "
						+ request.uri());
			}

		}

		ExchangeParameterResource resourceObject = new ExchangeParameterResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("exchange-resource");

		// Test that the handler works with exchange parameter
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = ReadResourceRequest.builder("exchange://resource").build();
		Mono<ReadResourceResult> result = resourceSpecs.get(0).readHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(readResult -> {
			assertThat(readResult.contents()).hasSize(1);
			ResourceContents content = readResult.contents().get(0);
			assertThat(content).isInstanceOf(TextResourceContents.class);
			assertThat(((TextResourceContents) content).text())
				.isEqualTo("Resource with exchange: present, URI: exchange://resource");
		}).verifyComplete();
	}

	@Test
	void testGetResourceSpecificationsWithRequestParameter() {
		class RequestParameterResource {

			@McpResource(uri = "request://resource", name = "request-resource",
					description = "Resource with request parameter")
			public Mono<String> requestResource(ReadResourceRequest request) {
				return Mono.just("Resource for URI: " + request.uri());
			}

		}

		RequestParameterResource resourceObject = new RequestParameterResource();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("request-resource");

		// Test that the handler works with request parameter
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = ReadResourceRequest.builder("request://resource").build();
		Mono<ReadResourceResult> result = resourceSpecs.get(0).readHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(readResult -> {
			assertThat(readResult.contents()).hasSize(1);
			ResourceContents content = readResult.contents().get(0);
			assertThat(content).isInstanceOf(TextResourceContents.class);
			assertThat(((TextResourceContents) content).text()).isEqualTo("Resource for URI: request://resource");
		}).verifyComplete();
	}

	@Test
	void testGetResourceSpecificationsWithSyncMethodReturningMono() {
		class SyncMethodReturningMono {

			@McpResource(uri = "sync-mono://resource", name = "sync-mono-resource",
					description = "Sync method returning Mono")
			public Mono<String> syncMethodReturningMono() {
				return Mono.just("Sync method returning Mono content");
			}

		}

		SyncMethodReturningMono resourceObject = new SyncMethodReturningMono();
		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(resourceObject));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().name()).isEqualTo("sync-mono-resource");

		// Test that the handler works with sync method returning Mono
		McpAsyncServerExchange exchange = mock(McpAsyncServerExchange.class);
		ReadResourceRequest request = ReadResourceRequest.builder("sync-mono://resource").build();
		Mono<ReadResourceResult> result = resourceSpecs.get(0).readHandler().apply(exchange, request);

		StepVerifier.create(result).assertNext(readResult -> {
			assertThat(readResult.contents()).hasSize(1);
			ResourceContents content = readResult.contents().get(0);
			assertThat(content).isInstanceOf(TextResourceContents.class);
			assertThat(((TextResourceContents) content).text()).isEqualTo("Sync method returning Mono content");
		}).verifyComplete();
	}

	@Test
	void testResourceCspIsEmittedOnListedResourceAndReadContent() {
		class CspResource {

			@McpResource(uri = "ui://test/csp.html", name = "csp-view", mimeType = "text/html;profile=mcp-app",
					csp = @McpCsp(connectDomains = "https://api.example.com",
							resourceDomains = { "https://cdn.example.com", "https://*.example.org" },
							frameDomains = "https://embed.example.com", baseUriDomains = "https://static.example.com"))
			public Mono<String> cspView() {
				return Mono.just("<html></html>");
			}

		}

		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(new CspResource()));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		ResourceCspAssertions.assertFullCsp(resourceSpecs.get(0).resource().meta());

		ReadResourceRequest request = ReadResourceRequest.builder("ui://test/csp.html").build();
		ReadResourceResult result = resourceSpecs.get(0)
			.readHandler()
			.apply(mock(McpAsyncServerExchange.class), request)
			.block();
		assertThat(result.contents()).hasSize(1);
		ResourceCspAssertions.assertFullCsp(result.contents().get(0).meta());
	}

	@Test
	void testResourceCspIsEmittedOnUriTemplateResource() {
		class CspTemplateResource {

			@McpResource(uri = "ui://test/{id}/view.html", name = "csp-template-view",
					mimeType = "text/html;profile=mcp-app",
					csp = @McpCsp(connectDomains = "https://api.example.com",
							resourceDomains = { "https://cdn.example.com", "https://*.example.org" },
							frameDomains = "https://embed.example.com", baseUriDomains = "https://static.example.com"))
			public Mono<String> cspTemplateView(String id) {
				return Mono.just("<html>" + id + "</html>");
			}

		}

		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(new CspTemplateResource()));

		List<AsyncResourceTemplateSpecification> templateSpecs = provider.getResourceTemplateSpecifications();

		assertThat(templateSpecs).hasSize(1);
		ResourceCspAssertions.assertFullCsp(templateSpecs.get(0).resourceTemplate().meta());

		ReadResourceRequest request = ReadResourceRequest.builder("ui://test/42/view.html").build();
		ReadResourceResult result = templateSpecs.get(0)
			.readHandler()
			.apply(mock(McpAsyncServerExchange.class), request)
			.block();
		assertThat(result.contents()).hasSize(1);
		ResourceCspAssertions.assertFullCsp(result.contents().get(0).meta());
	}

	@Test
	void testEmptyResourceCspIsOmitted() {
		class EmptyCspResource {

			@McpResource(uri = "ui://test/empty.html", name = "empty-csp-view", mimeType = "text/html;profile=mcp-app",
					csp = @McpCsp)
			public Mono<String> emptyCspView() {
				return Mono.just("<html></html>");
			}

		}

		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(new EmptyCspResource()));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		assertThat(resourceSpecs.get(0).resource().meta()).isNull();

		ReadResourceRequest request = ReadResourceRequest.builder("ui://test/empty.html").build();
		ReadResourceResult result = resourceSpecs.get(0)
			.readHandler()
			.apply(mock(McpAsyncServerExchange.class), request)
			.block();
		assertThat(result.contents()).hasSize(1);
		assertThat(result.contents().get(0).meta()).isNull();
	}

	@Test
	void testResourceCspMergesWithMetaProvider() {
		class MergedCspResource {

			@McpResource(uri = "ui://test/merged.html", name = "merged-csp-view",
					mimeType = "text/html;profile=mcp-app", csp = @McpCsp(connectDomains = "https://api.example.com"),
					metaProvider = ResourceCspAssertions.UiPrefsMetaProvider.class)
			public Mono<String> mergedCspView() {
				return Mono.just("<html></html>");
			}

		}

		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(new MergedCspResource()));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		ResourceCspAssertions.assertMergedCsp(resourceSpecs.get(0).resource().meta());

		ReadResourceRequest request = ReadResourceRequest.builder("ui://test/merged.html").build();
		ReadResourceResult result = resourceSpecs.get(0)
			.readHandler()
			.apply(mock(McpAsyncServerExchange.class), request)
			.block();
		assertThat(result.contents()).hasSize(1);
		ResourceCspAssertions.assertMergedCsp(result.contents().get(0).meta());
	}

	@Test
	void testUiResourceFieldsOnListingAndReadContents() {
		class UiFieldsResource {

			@McpResource(uri = "ui://test/ui-fields.html", name = "ui-fields-view",
					mimeType = "text/html;profile=mcp-app", permissions = McpPermission.MICROPHONE,
					domain = "www-example-com.oaiusercontent.com", prefersBorder = PrefersBorder.FALSE)
			public Mono<String> uiFieldsView() {
				return Mono.just("<html></html>");
			}

		}

		AsyncMcpResourceProvider provider = new AsyncMcpResourceProvider(List.of(new UiFieldsResource()));

		List<AsyncResourceSpecification> resourceSpecs = provider.getResourceSpecifications();

		assertThat(resourceSpecs).hasSize(1);
		ResourceCspAssertions.assertUiResourceFields(resourceSpecs.get(0).resource().meta());

		ReadResourceRequest request = ReadResourceRequest.builder("ui://test/ui-fields.html").build();
		ReadResourceResult result = resourceSpecs.get(0)
			.readHandler()
			.apply(mock(McpAsyncServerExchange.class), request)
			.block();
		assertThat(result.contents()).hasSize(1);
		ResourceCspAssertions.assertUiResourceFields(result.contents().get(0).meta());
	}

}
