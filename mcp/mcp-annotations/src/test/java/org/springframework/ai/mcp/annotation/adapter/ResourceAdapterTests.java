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

package org.springframework.ai.mcp.annotation.adapter;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.McpResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for SEP-1865 URI / MIME type validation in {@link ResourceAdapter}.
 *
 * @author Alexandros Pappas
 */
class ResourceAdapterTests {

	private static final String MCP_APP_MIME_TYPE = "text/html;profile=mcp-app";

	// ---------------------------------------------------------------------------
	// asResource — valid cases
	// ---------------------------------------------------------------------------

	@Test
	void asResource_uiUriWithCorrectMimeType_succeeds() {
		McpResource annotation = mockResource("ui://my-server/app.html", MCP_APP_MIME_TYPE);
		McpSchema.Resource resource = ResourceAdapter.asResource(annotation);
		assertThat(resource.uri()).isEqualTo("ui://my-server/app.html");
		assertThat(resource.mimeType()).isEqualTo(MCP_APP_MIME_TYPE);
	}

	@Test
	void asResource_regularUriWithRegularMimeType_succeeds() {
		McpResource annotation = mockResource("file://data/doc.txt", "text/plain");
		McpSchema.Resource resource = ResourceAdapter.asResource(annotation);
		assertThat(resource.uri()).isEqualTo("file://data/doc.txt");
		assertThat(resource.mimeType()).isEqualTo("text/plain");
	}

	@Test
	void asResource_regularUriWithNullMimeType_succeeds() {
		McpResource annotation = mockResource("https://example.com/data", "");
		McpSchema.Resource resource = ResourceAdapter.asResource(annotation);
		assertThat(resource.uri()).isEqualTo("https://example.com/data");
	}

	@Test
	void asResource_uiUriWithMimeTypeContainingWhitespace_succeeds() {
		McpResource annotation = mockResource("ui://my-server/app.html", "text/html; profile=mcp-app");
		McpSchema.Resource resource = ResourceAdapter.asResource(annotation);
		assertThat(resource.mimeType()).isEqualTo("text/html; profile=mcp-app");
	}

	@Test
	void asResource_uiUriWithMixedCaseMimeType_succeeds() {
		McpResource annotation = mockResource("ui://my-server/app.html", "Text/HTML;profile=mcp-app");
		McpSchema.Resource resource = ResourceAdapter.asResource(annotation);
		assertThat(resource.mimeType()).isEqualTo("Text/HTML;profile=mcp-app");
	}

	@Test
	void asResource_upperCaseUiSchemeWithWrongMimeType_throwsIllegalArgumentException() {
		McpResource annotation = mockResource("UI://my-server/app.html", "text/plain");
		assertThatIllegalArgumentException().isThrownBy(() -> ResourceAdapter.asResource(annotation))
			.withMessageContaining("ui:// URI must use MIME type 'text/html;profile=mcp-app'");
	}

	// ---------------------------------------------------------------------------
	// asResource — invalid cases
	// ---------------------------------------------------------------------------

	@Test
	void asResource_uiUriWithWrongMimeType_throwsIllegalArgumentException() {
		McpResource annotation = mockResource("ui://my-server/app.html", "text/plain");
		assertThatIllegalArgumentException().isThrownBy(() -> ResourceAdapter.asResource(annotation))
			.withMessageContaining("ui:// URI must use MIME type 'text/html;profile=mcp-app'")
			.withMessageContaining("text/plain");
	}

	@Test
	void asResource_uiUriWithEmptyMimeType_throwsIllegalArgumentException() {
		McpResource annotation = mockResource("ui://my-server/app.html", "");
		assertThatIllegalArgumentException().isThrownBy(() -> ResourceAdapter.asResource(annotation))
			.withMessageContaining("ui:// URI must use MIME type 'text/html;profile=mcp-app'");
	}

	@Test
	void asResource_mcpAppMimeTypeWithNonUiUri_throwsIllegalArgumentException() {
		McpResource annotation = mockResource("https://example.com/app.html", MCP_APP_MIME_TYPE);
		assertThatIllegalArgumentException().isThrownBy(() -> ResourceAdapter.asResource(annotation))
			.withMessageContaining("MIME type 'text/html;profile=mcp-app' must use a ui:// URI")
			.withMessageContaining("https://example.com/app.html");
	}

	// ---------------------------------------------------------------------------
	// asResourceTemplate — valid cases
	// ---------------------------------------------------------------------------

	@Test
	void asResourceTemplate_uiUriWithCorrectMimeType_succeeds() {
		McpResource annotation = mockResource("ui://my-server/{id}.html", MCP_APP_MIME_TYPE);
		McpSchema.ResourceTemplate template = ResourceAdapter.asResourceTemplate(annotation);
		assertThat(template.uriTemplate()).isEqualTo("ui://my-server/{id}.html");
		assertThat(template.mimeType()).isEqualTo(MCP_APP_MIME_TYPE);
	}

	@Test
	void asResourceTemplate_regularUriWithRegularMimeType_succeeds() {
		McpResource annotation = mockResource("file://data/{name}.txt", "text/plain");
		McpSchema.ResourceTemplate template = ResourceAdapter.asResourceTemplate(annotation);
		assertThat(template.uriTemplate()).isEqualTo("file://data/{name}.txt");
	}

	// ---------------------------------------------------------------------------
	// asResourceTemplate — invalid cases
	// ---------------------------------------------------------------------------

	@Test
	void asResourceTemplate_uiUriWithWrongMimeType_throwsIllegalArgumentException() {
		McpResource annotation = mockResource("ui://my-server/{id}.html", "application/json");
		assertThatIllegalArgumentException().isThrownBy(() -> ResourceAdapter.asResourceTemplate(annotation))
			.withMessageContaining("ui:// URI must use MIME type 'text/html;profile=mcp-app'")
			.withMessageContaining("application/json");
	}

	@Test
	void asResourceTemplate_mcpAppMimeTypeWithNonUiUri_throwsIllegalArgumentException() {
		McpResource annotation = mockResource("https://example.com/{page}.html", MCP_APP_MIME_TYPE);
		assertThatIllegalArgumentException().isThrownBy(() -> ResourceAdapter.asResourceTemplate(annotation))
			.withMessageContaining("MIME type 'text/html;profile=mcp-app' must use a ui:// URI")
			.withMessageContaining("https://example.com/{page}.html");
	}

	// ---------------------------------------------------------------------------
	// Helper
	// ---------------------------------------------------------------------------

	private McpResource mockResource(String uri, String mimeType) {
		return new McpResource() {
			@Override
			public Class<? extends java.lang.annotation.Annotation> annotationType() {
				return McpResource.class;
			}

			@Override
			public String uri() {
				return uri;
			}

			@Override
			public String name() {
				return "test-resource";
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
				return mimeType;
			}

			@Override
			public McpAnnotations annotations() {
				return new McpAnnotations() {
					@Override
					public Class<? extends java.lang.annotation.Annotation> annotationType() {
						return McpAnnotations.class;
					}

					@Override
					public io.modelcontextprotocol.spec.McpSchema.Role[] audience() {
						return new io.modelcontextprotocol.spec.McpSchema.Role[] {
								io.modelcontextprotocol.spec.McpSchema.Role.USER };
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
			public Class<? extends org.springframework.ai.mcp.annotation.context.MetaProvider> metaProvider() {
				return org.springframework.ai.mcp.annotation.context.DefaultMetaProvider.class;
			}
		};
	}

}
