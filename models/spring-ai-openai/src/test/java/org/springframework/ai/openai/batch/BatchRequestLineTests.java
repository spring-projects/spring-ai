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

package org.springframework.ai.openai.batch;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BatchRequestLine}.
 *
 * @author Yasin Akbas
 */
class BatchRequestLineTests {

	@Test
	void shouldCreatePostRequestLine() {
		BatchRequestLine line = BatchRequestLine.post("entity-1::chat", "/v1/chat/completions",
				Map.of("model", "gpt-4o-mini", "messages", "hello"));

		assertThat(line.customId()).isEqualTo("entity-1::chat");
		assertThat(line.method()).isEqualTo("POST");
		assertThat(line.url()).isEqualTo("/v1/chat/completions");
		assertThat(line.body()).containsEntry("model", "gpt-4o-mini");
	}

	@Test
	void shouldCreateWithFullConstructor() {
		BatchRequestLine line = new BatchRequestLine("custom-id::handler", "POST", "/v1/embeddings",
				Map.of("model", "text-embedding-3-small", "input", "test"));

		assertThat(line.customId()).isEqualTo("custom-id::handler");
		assertThat(line.url()).isEqualTo("/v1/embeddings");
	}

	@Test
	void shouldRejectBlankCustomId() {
		assertThatThrownBy(() -> BatchRequestLine.post("", "/v1/chat/completions", Map.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectBlankUrl() {
		assertThatThrownBy(() -> BatchRequestLine.post("id::handler", "", Map.of()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectNullBody() {
		assertThatThrownBy(() -> BatchRequestLine.post("id::handler", "/v1/chat/completions", null))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
