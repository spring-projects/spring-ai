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

package org.springframework.ai.mcp.modern;

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.modern.McpException;
import io.modelcontextprotocol.modern.McpSchema;
import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
class McpSchemaConverterTests {

	@Test
	void convertsCallToolResultWithMixedContent() {
		var legacy = io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder()
			.addTextContent("hello")
			.addContent(io.modelcontextprotocol.spec.McpSchema.ImageContent.builder("aGk=", "image/png").build())
			.structuredContent(Map.of("k", "v"))
			.isError(false)
			.build();

		McpSchema.CallToolResult modern = McpSchemaConverter.toModern(legacy);

		assertThat(modern.resultType()).isEqualTo(McpSchema.ResultType.COMPLETE);
		assertThat(modern.content()).hasSize(2);
		assertThat(modern.content().get(0)).isInstanceOf(McpSchema.TextContent.class);
		assertThat(((McpSchema.TextContent) modern.content().get(0)).text()).isEqualTo("hello");
		assertThat(modern.content().get(1)).isInstanceOf(McpSchema.ImageContent.class);
		assertThat(modern.structuredContent()).isEqualTo(Map.of("k", "v"));
		assertThat(modern.isError()).isFalse();
	}

	@Test
	void convertsReadResourceResultWithCacheDefaults() {
		var legacy = new io.modelcontextprotocol.spec.McpSchema.ReadResourceResult(List
			.of(new io.modelcontextprotocol.spec.McpSchema.TextResourceContents("file:///a", "text/plain", "body")));

		McpSchema.ReadResourceResult modern = McpSchemaConverter.toModern(legacy);

		assertThat(modern.contents()).hasSize(1);
		assertThat(((McpSchema.TextResourceContents) modern.contents().get(0)).text()).isEqualTo("body");
		assertThat(modern.ttlMs()).isZero();
		assertThat(modern.cacheScope()).isEqualTo(CacheScope.PRIVATE);
	}

	@Test
	void convertsToolDefinition() {
		var legacy = io.modelcontextprotocol.spec.McpSchema.Tool.builder()
			.name("t")
			.description("d")
			.inputSchema(Map.of("type", "object", "properties", Map.of("a", Map.of("type", "string"))))
			.build();

		McpSchema.Tool modern = McpSchemaConverter.toModern(legacy);

		assertThat(modern.name()).isEqualTo("t");
		assertThat(modern.description()).isEqualTo("d");
		assertThat(modern.inputSchema()).containsKey("properties");
	}

	@Test
	void convertsRequestsToLegacy() {
		var call = McpSchemaConverter
			.toLegacy(McpSchema.CallToolRequest.builder("t").arguments(Map.of("a", 1)).requestState("s").build());
		assertThat(call.name()).isEqualTo("t");
		assertThat(call.arguments()).containsEntry("a", 1);

		var prompt = McpSchemaConverter
			.toLegacy(McpSchema.GetPromptRequest.builder("p").arguments(Map.of("x", "y")).build());
		assertThat(prompt.arguments()).containsEntry("x", "y");

		var read = McpSchemaConverter.toLegacy(McpSchema.ReadResourceRequest.builder("file:///a").build());
		assertThat(read.uri()).isEqualTo("file:///a");

		var complete = McpSchemaConverter.toLegacy(McpSchema.CompleteRequest
			.builder(new McpSchema.PromptReference("p"), new McpSchema.CompleteRequest.CompleteArgument("n", "v"))
			.build());
		assertThat(complete.ref()).isInstanceOf(io.modelcontextprotocol.spec.McpSchema.PromptReference.class);
		assertThat(complete.argument().value()).isEqualTo("v");
	}

	@Test
	void convertsMcpErrorToMcpException() {
		McpError error = McpError.builder(-32602).message("bad").data("why").build();

		McpException exception = McpSchemaConverter.toModern(error);

		assertThat(exception.error().code()).isEqualTo(-32602);
		assertThat(exception.error().message()).isEqualTo("bad");
		assertThat(exception.error().data()).isEqualTo("why");
	}

}
