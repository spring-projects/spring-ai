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

import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.annotation.method.resource.AbstractMcpResourceMethodCallback.ContentType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultMcpReadResourceResultConverter} verifying that resource-level
 * metadata (_meta) propagates to content items in {@code ReadResourceResult}.
 *
 * @author Alexandros Pappas
 */
public class DefaultMcpReadResourceResultConverterTests {

	private final DefaultMcpReadResourceResultConverter converter = new DefaultMcpReadResourceResultConverter();

	@Test
	void testMetaPropagatedToTextResourceContents() {
		Map<String, Object> meta = Map.of("ui", Map.of("csp", Map.of("connectDomains", List.of("api.example.com"))));

		ReadResourceResult result = this.converter.convertToReadResourceResult("<html>Hello</html>", "ui://test/view",
				"text/html;profile=mcp-app", ContentType.TEXT, meta);

		assertThat(result.contents()).hasSize(1);
		TextResourceContents content = (TextResourceContents) result.contents().get(0);
		assertThat(content.meta()).isNotNull();
		assertThat(content.meta()).containsKey("ui");
	}

	@Test
	void testMetaNullWhenNotSpecified() {
		ReadResourceResult result = this.converter.convertToReadResourceResult("content", "resource://test",
				"text/plain", ContentType.TEXT, null);

		assertThat(result.contents()).hasSize(1);
		TextResourceContents content = (TextResourceContents) result.contents().get(0);
		assertThat(content.meta()).isNull();
	}

	@Test
	void testMetaPropagatedToTextResourceContentsFromStringList() {
		Map<String, Object> meta = Map.of("ui", Map.of("theme", "dark"));

		ReadResourceResult result = this.converter.convertToReadResourceResult(List.of("item1", "item2"),
				"ui://test/list", "text/plain", ContentType.TEXT, meta);

		assertThat(result.contents()).hasSize(2);

		TextResourceContents content0 = (TextResourceContents) result.contents().get(0);
		assertThat(content0.text()).isEqualTo("item1");
		assertThat(content0.meta()).isNotNull();
		assertThat(content0.meta()).containsKey("ui");

		TextResourceContents content1 = (TextResourceContents) result.contents().get(1);
		assertThat(content1.text()).isEqualTo("item2");
		assertThat(content1.meta()).isNotNull();
		assertThat(content1.meta()).containsKey("ui");
	}

	@Test
	void testExistingResourceContentsPassthroughPreservesOriginalMeta() {
		Map<String, Object> userMeta = Map.of("custom", "user-provided-meta");
		TextResourceContents userContent = new TextResourceContents("resource://test", "text/plain", "user content",
				userMeta);

		Map<String, Object> annotationMeta = Map.of("annotation", "should-not-override");

		ReadResourceResult result = this.converter.convertToReadResourceResult(userContent, "resource://test",
				"text/plain", ContentType.TEXT, annotationMeta);

		assertThat(result.contents()).hasSize(1);
		TextResourceContents content = (TextResourceContents) result.contents().get(0);
		assertThat(content.meta()).isEqualTo(userMeta);
		assertThat(content.meta()).containsKey("custom");
		assertThat(content.meta()).doesNotContainKey("annotation");
	}

	@Test
	void testExistingReadResourceResultPassthroughIsUnmodified() {
		Map<String, Object> userMeta = Map.of("original", "from-user");
		TextResourceContents userContent = new TextResourceContents("resource://test", "text/plain", "user content",
				userMeta);
		ReadResourceResult userResult = new ReadResourceResult(List.of(userContent));

		Map<String, Object> annotationMeta = Map.of("annotation", "should-not-override");

		ReadResourceResult result = this.converter.convertToReadResourceResult(userResult, "resource://test",
				"text/plain", ContentType.TEXT, annotationMeta);

		assertThat(result.contents()).hasSize(1);
		TextResourceContents content = (TextResourceContents) result.contents().get(0);
		assertThat(content.meta()).isEqualTo(userMeta);
		assertThat(content.meta()).containsKey("original");
		assertThat(content.meta()).doesNotContainKey("annotation");
	}

	@Test
	void testExistingResourceContentsListPassthroughPreservesOriginalMeta() {
		Map<String, Object> userMeta = Map.of("custom", "list-meta");
		TextResourceContents userContent = new TextResourceContents("resource://test", "text/plain", "user content",
				userMeta);

		Map<String, Object> annotationMeta = Map.of("annotation", "should-not-override");

		ReadResourceResult result = this.converter.convertToReadResourceResult(List.of(userContent), "resource://test",
				"text/plain", ContentType.TEXT, annotationMeta);

		assertThat(result.contents()).hasSize(1);
		TextResourceContents content = (TextResourceContents) result.contents().get(0);
		assertThat(content.meta()).isEqualTo(userMeta);
		assertThat(content.meta()).containsKey("custom");
		assertThat(content.meta()).doesNotContainKey("annotation");
	}

	@Test
	void testNullResultReturnsEmptyContents() {
		ReadResourceResult result = this.converter.convertToReadResourceResult(null, "resource://test", "text/plain",
				ContentType.TEXT, Map.of("ui", "value"));

		assertThat(result.contents()).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void testMetaWithComplexNestedStructure() {
		Map<String, Object> meta = Map.of("ui",
				Map.of("csp", Map.of("connectDomains", List.of("api.example.com", "cdn.example.com"), "frameDomains",
						List.of("embed.example.com")), "theme", "dark"));

		ReadResourceResult result = this.converter.convertToReadResourceResult("<html>App</html>", "ui://myapp/view",
				"text/html;profile=mcp-app", ContentType.TEXT, meta);

		assertThat(result.contents()).hasSize(1);
		TextResourceContents content = (TextResourceContents) result.contents().get(0);
		assertThat(content.meta()).isNotNull();
		assertThat(content.meta()).containsKey("ui");

		Map<String, Object> uiMeta = (Map<String, Object>) content.meta().get("ui");
		assertThat(uiMeta).containsKey("csp");
		assertThat(uiMeta).containsKey("theme");
		assertThat(uiMeta.get("theme")).isEqualTo("dark");
	}

}
