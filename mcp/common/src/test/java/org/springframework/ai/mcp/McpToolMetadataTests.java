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

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolMetadataTests {

	@Test
	void fromAnnotationsShouldExposeAllHints() {
		var annotations = McpSchema.ToolAnnotations.builder()
			.readOnlyHint(true)
			.destructiveHint(false)
			.idempotentHint(true)
			.openWorldHint(false)
			.returnDirect(true)
			.build();

		McpToolMetadata metadata = McpToolMetadata.from(annotations);

		assertThat(metadata.returnDirect()).isTrue();
		assertThat(metadata.readOnlyHint()).isTrue();
		assertThat(metadata.destructiveHint()).isFalse();
		assertThat(metadata.idempotentHint()).isTrue();
		assertThat(metadata.openWorldHint()).isFalse();
		assertThat(metadata.getAnnotations()).isSameAs(annotations);
	}

	@Test
	void fromNullAnnotationsShouldReturnSafeDefaults() {
		McpToolMetadata metadata = McpToolMetadata.from(null);

		assertThat(metadata.returnDirect()).isFalse();
		assertThat(metadata.readOnlyHint()).isNull();
		assertThat(metadata.destructiveHint()).isNull();
		assertThat(metadata.idempotentHint()).isNull();
		assertThat(metadata.openWorldHint()).isNull();
		assertThat(metadata.getAnnotations()).isNull();
	}

	@Test
	void fromAnnotationsWithNullReturnDirectShouldDefaultToFalse() {
		var annotations = McpSchema.ToolAnnotations.builder().readOnlyHint(true).build();

		McpToolMetadata metadata = McpToolMetadata.from(annotations);

		assertThat(metadata.returnDirect()).isFalse();
		assertThat(metadata.readOnlyHint()).isTrue();
	}

	@Test
	void fromAnnotationsWithPartialHintsShouldHandleNulls() {
		var annotations = McpSchema.ToolAnnotations.builder().destructiveHint(true).build();

		McpToolMetadata metadata = McpToolMetadata.from(annotations);

		assertThat(metadata.readOnlyHint()).isNull();
		assertThat(metadata.destructiveHint()).isTrue();
		assertThat(metadata.idempotentHint()).isNull();
		assertThat(metadata.openWorldHint()).isNull();
	}

}
