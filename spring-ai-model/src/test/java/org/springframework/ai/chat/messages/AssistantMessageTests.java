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

package org.springframework.ai.chat.messages;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AssistantMessage}.
 *
 * @author Thomas Vitale
 * @author guan xu
 */
class AssistantMessageTests {

	@Test
	void whenMediaIsNullThenThrow() {
		assertThatThrownBy(() -> AssistantMessage.builder().media(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Media must not be null");
	}

	@Test
	void whenMetadataIsNullThenThrow() {
		assertThatThrownBy(() -> AssistantMessage.builder().properties(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Metadata must not be null");
	}

	@Test
	void whenToolCallsIsNullThenThrow() {
		assertThatThrownBy(() -> AssistantMessage.builder().toolCalls(null).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Tool calls must not be null");
	}

	@Test
	void copyPreservesContentPropertiesAndToolCalls() {
		AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall("id", "function", "name", "{}");
		AssistantMessage original = AssistantMessage.builder()
			.content("hello")
			.properties(Map.of("k", "v"))
			.toolCalls(List.of(toolCall))
			.build();

		AssistantMessage copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getText()).isEqualTo("hello");
		assertThat(copy.getMetadata()).containsEntry("k", "v");
		assertThat(copy.getToolCalls()).containsExactly(toolCall);
	}

	@Test
	void promptCopyPreservesAssistantMessageSubclassViaPolymorphicMutate() {
		TestAssistantMessage original = TestAssistantMessage.builder().content("hello").marker("m").build();

		Prompt copy = new Prompt(List.of(original)).copy();

		Message copied = copy.getInstructions().get(0);
		assertThat(copied).isInstanceOf(TestAssistantMessage.class);
		assertThat(((TestAssistantMessage) copied).getMarker()).isEqualTo("m");
	}

	/**
	 * A minimal {@link AssistantMessage} subclass that carries an extra field and
	 * overrides {@code mutate()} to preserve it.
	 */
	static final class TestAssistantMessage extends AssistantMessage {

		private final String marker;

		private TestAssistantMessage(String content, String marker, Map<String, Object> properties,
				List<ToolCall> toolCalls, List<Media> media) {
			super(content, properties, toolCalls, media);
			this.marker = marker;
		}

		String getMarker() {
			return this.marker;
		}

		@Override
		public TestBuilder mutate() {
			return builder().content(getText())
				.properties(getMetadata())
				.toolCalls(getToolCalls())
				.media(getMedia())
				.marker(getMarker());
		}

		public static TestBuilder builder() {
			return new TestBuilder();
		}

		static final class TestBuilder extends AssistantMessage.Builder<TestBuilder> {

			private String marker;

			private TestBuilder() {
			}

			TestBuilder marker(String marker) {
				this.marker = marker;
				return self();
			}

			@Override
			public TestAssistantMessage build() {
				return new TestAssistantMessage(this.content, this.marker, this.properties, this.toolCalls, this.media);
			}

		}

	}

}
