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

import java.util.List;

import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.common.MetaUtils;

/**
 * Utility class that converts {@link McpResource} annotations into MCP schema objects.
 * Provides factory methods to build {@link McpSchema.Resource} and
 * {@link McpSchema.ResourceTemplate} instances from annotation metadata, including URI,
 * name, description, MIME type, annotations, and optional {@code _meta} fields.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public final class ResourceAdapter {

	private ResourceAdapter() {
	}

	public static McpSchema.Resource asResource(McpResource mcpResourceAnnotation) {
		String name = mcpResourceAnnotation.name();
		if (name == null || name.isEmpty()) {
			name = "resource"; // Default name when not specified
		}
		var meta = MetaUtils.getMeta(mcpResourceAnnotation.metaProvider());

		var resourceBuilder = McpSchema.Resource.builder(mcpResourceAnnotation.uri(), name)
			.title(mcpResourceAnnotation.title())
			.description(mcpResourceAnnotation.description())
			.mimeType(mcpResourceAnnotation.mimeType())
			.meta(meta);

		// Only set annotations if not default value is provided
		// This is a workaround since Java annotations do not support null default values
		// and we want to avoid setting empty annotations.
		// The default annotations value is ignored.
		// The user must explicitly set the annotations to get them included.
		var annotations = mcpResourceAnnotation.annotations();
		if (annotations != null && annotations.lastModified() != null && !annotations.lastModified().isEmpty()) {
			resourceBuilder
				.annotations(new McpSchema.Annotations(List.of(annotations.audience()), annotations.priority()));
		}

		return resourceBuilder.build();
	}

	public static McpSchema.ResourceTemplate asResourceTemplate(McpResource mcpResource) {
		String name = mcpResource.name();
		if (name == null || name.isEmpty()) {
			name = "resource"; // Default name when not specified
		}
		var meta = MetaUtils.getMeta(mcpResource.metaProvider());

		return McpSchema.ResourceTemplate.builder(mcpResource.uri(), name)
			.description(mcpResource.description())
			.mimeType(mcpResource.mimeType())
			.meta(meta)
			.build();
	}

}
