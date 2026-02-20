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

package org.springframework.ai.mcp;

import java.lang.reflect.Method;
import java.util.List;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParseContentListTests {

	private static Method parseContentList;

	@BeforeAll
	static void setUp() throws Exception {
		parseContentList = McpToolUtils.class.getDeclaredMethod("parseContentList", String.class);
		parseContentList.setAccessible(true);
	}

	@SuppressWarnings("unchecked")
	private List<McpSchema.Content> invoke(String input) throws Exception {
		return (List<McpSchema.Content>) parseContentList.invoke(null, input);
	}

	@Test
	void shouldParseTextContentWithoutTypeField() throws Exception {
		String json = "[{\"text\":\"Hello from proxied tool\"}]";
		List<McpSchema.Content> result = invoke(json);

		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isInstanceOf(McpSchema.TextContent.class);
		assertThat(((McpSchema.TextContent) result.get(0)).text()).isEqualTo("Hello from proxied tool");
	}

	@Test
	void shouldParseMultipleTextContents() throws Exception {
		String json = "[{\"text\":\"First\"},{\"text\":\"Second\"}]";
		List<McpSchema.Content> result = invoke(json);

		assertThat(result).hasSize(2);
		assertThat(((McpSchema.TextContent) result.get(0)).text()).isEqualTo("First");
		assertThat(((McpSchema.TextContent) result.get(1)).text()).isEqualTo("Second");
	}

	@Test
	void shouldParseImageContent() throws Exception {
		String json = "[{\"data\":\"base64data\",\"mimeType\":\"image/png\"}]";
		List<McpSchema.Content> result = invoke(json);

		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isInstanceOf(McpSchema.ImageContent.class);
		McpSchema.ImageContent img = (McpSchema.ImageContent) result.get(0);
		assertThat(img.data()).isEqualTo("base64data");
		assertThat(img.mimeType()).isEqualTo("image/png");
	}

	@Test
	void shouldParseAudioContent() throws Exception {
		String json = "[{\"data\":\"audiodata\",\"mimeType\":\"audio/mp3\"}]";
		List<McpSchema.Content> result = invoke(json);

		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isInstanceOf(McpSchema.AudioContent.class);
		McpSchema.AudioContent audio = (McpSchema.AudioContent) result.get(0);
		assertThat(audio.data()).isEqualTo("audiodata");
		assertThat(audio.mimeType()).isEqualTo("audio/mp3");
	}

	@Test
	void shouldParseMixedContent() throws Exception {
		String json = "[{\"text\":\"hello\"},{\"data\":\"img\",\"mimeType\":\"image/jpeg\"}]";
		List<McpSchema.Content> result = invoke(json);

		assertThat(result).hasSize(2);
		assertThat(result.get(0)).isInstanceOf(McpSchema.TextContent.class);
		assertThat(result.get(1)).isInstanceOf(McpSchema.ImageContent.class);
	}

	@Test
	void shouldWrapPlainStringAsTextContent() throws Exception {
		String plainText = "just a plain string";
		List<McpSchema.Content> result = invoke(plainText);

		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isInstanceOf(McpSchema.TextContent.class);
		assertThat(((McpSchema.TextContent) result.get(0)).text()).isEqualTo("just a plain string");
	}

	@Test
	void shouldWrapJsonObjectAsTextContent() throws Exception {
		// A single JSON object (not an array) should be wrapped as TextContent
		String jsonObject = "{\"key\":\"value\"}";
		List<McpSchema.Content> result = invoke(jsonObject);

		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isInstanceOf(McpSchema.TextContent.class);
		assertThat(((McpSchema.TextContent) result.get(0)).text()).isEqualTo(jsonObject);
	}

	@Test
	void shouldHandleEmptyArray() throws Exception {
		String emptyArray = "[]";
		List<McpSchema.Content> result = invoke(emptyArray);

		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isInstanceOf(McpSchema.TextContent.class);
		assertThat(((McpSchema.TextContent) result.get(0)).text()).isEqualTo("[]");
	}

}
