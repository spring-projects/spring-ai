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

package org.springframework.ai.mcp.annotation;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class McpAppResultTests {

	@Test
	void testConstructor() {
		var structured = Map.<String, Object>of("key", "value");
		var result = new McpAppResult("hello", structured);
		assertThat(result.text()).isEqualTo("hello");
		assertThat(result.structuredContent()).isEqualTo(structured);
	}

	@Test
	void testStaticFactory() {
		var structured = Map.<String, Object>of("count", 42);
		var result = McpAppResult.of("done", structured);
		assertThat(result.text()).isEqualTo("done");
		assertThat(result.structuredContent()).containsEntry("count", 42);
	}

	@Test
	void testNullTextAllowed() {
		var result = McpAppResult.of(null, Map.of("key", "value"));
		assertThat(result.text()).isNull();
		assertThat(result.structuredContent()).containsEntry("key", "value");
	}

	@Test
	void testNullStructuredContentAllowed() {
		var result = McpAppResult.of("text only", null);
		assertThat(result.text()).isEqualTo("text only");
		assertThat(result.structuredContent()).isNull();
	}

	@Test
	void testBothNullThrows() {
		assertThatIllegalArgumentException().isThrownBy(() -> McpAppResult.of(null, null))
			.withMessageContaining("At least one of text or structuredContent");
	}

}
