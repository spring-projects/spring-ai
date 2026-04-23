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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link BatchRequestCustomId}.
 *
 * @author Yasin Akbas
 */
class BatchRequestCustomIdTests {

	@Test
	void shouldCreateCustomIdFromParts() {
		BatchRequestCustomId customId = new BatchRequestCustomId("entity-123", "chat-handler");
		assertThat(customId.entityId()).isEqualTo("entity-123");
		assertThat(customId.handlerId()).isEqualTo("chat-handler");
		assertThat(customId.toString()).isEqualTo("entity-123::chat-handler");
	}

	@Test
	void shouldParseCustomIdString() {
		BatchRequestCustomId customId = BatchRequestCustomId.parse("entity-456::embed-handler");
		assertThat(customId.entityId()).isEqualTo("entity-456");
		assertThat(customId.handlerId()).isEqualTo("embed-handler");
	}

	@Test
	void shouldParseCustomIdWithHyphensAndNumbers() {
		BatchRequestCustomId customId = BatchRequestCustomId.parse("my-entity-789::my-handler-v2");
		assertThat(customId.entityId()).isEqualTo("my-entity-789");
		assertThat(customId.handlerId()).isEqualTo("my-handler-v2");
	}

	@Test
	void shouldHandleEntityIdWithUnderscores() {
		BatchRequestCustomId customId = new BatchRequestCustomId("entity_with_underscores", "handler");
		assertThat(customId.toString()).isEqualTo("entity_with_underscores::handler");

		BatchRequestCustomId parsed = BatchRequestCustomId.parse("entity_with_underscores::handler");
		assertThat(parsed.entityId()).isEqualTo("entity_with_underscores");
	}

	@Test
	void shouldRejectBlankEntityId() {
		assertThatThrownBy(() -> new BatchRequestCustomId("", "handler")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectBlankHandlerId() {
		assertThatThrownBy(() -> new BatchRequestCustomId("entity", "")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectEntityIdContainingDelimiter() {
		assertThatThrownBy(() -> new BatchRequestCustomId("entity::bad", "handler"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectHandlerIdContainingDelimiter() {
		assertThatThrownBy(() -> new BatchRequestCustomId("entity", "handler::bad"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRejectInvalidFormatWhenParsing() {
		assertThatThrownBy(() -> BatchRequestCustomId.parse("no-delimiter-here"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Invalid custom ID format");
	}

	@Test
	void shouldRejectTooManyPartsWhenParsing() {
		assertThatThrownBy(() -> BatchRequestCustomId.parse("a::b::c")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Invalid custom ID format");
	}

	@Test
	void shouldRejectBlankStringWhenParsing() {
		assertThatThrownBy(() -> BatchRequestCustomId.parse("")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void shouldRoundTrip() {
		BatchRequestCustomId original = new BatchRequestCustomId("comp-12345", "metadata-gen");
		BatchRequestCustomId parsed = BatchRequestCustomId.parse(original.toString());
		assertThat(parsed).isEqualTo(original);
	}

}
