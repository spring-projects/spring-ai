/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.chat.prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * Unit Tests for {@link ChatOptions} builder.
 *
 * @author youngmon
 * @author Mark Pollack
 * @since 1.0.0
 */
public class ChatOptionsBuilderTests {

	private ChatOptions.Builder builder;

	@BeforeEach
	void setUp() {
		this.builder = ChatOptions.builder();
	}

	@Test
	void shouldBuildWithAllOptions() {
		ChatOptions options = this.builder.model("gpt-4")
			.maxTokens(100)
			.temperature(0.7)
			.topP(1.0)
			.topK(40)
			.stopSequences(List.of("stop1", "stop2"))
			.build();

		assertThat(options.getModel()).isEqualTo("gpt-4");
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(1.0);
		assertThat(options.getTopK()).isEqualTo(40);
		assertThat(options.getStopSequences()).containsExactly("stop1", "stop2");
	}

	@Test
	void shouldBuildWithMinimalOptions() {
		ChatOptions options = this.builder.model("gpt-4").build();

		assertThat(options.getModel()).isEqualTo("gpt-4");
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getStopSequences()).isNull();
	}

	@Test
	void shouldCopyOptions() {
		ChatOptions original = this.builder.model("gpt-4")
			.maxTokens(100)
			.temperature(0.7)
			.topP(1.0)
			.topK(40)
			.stopSequences(List.of("stop1", "stop2"))
			.build();

		ChatOptions copy = original.copy();

		// Then
		assertThat(copy).usingRecursiveComparison().isEqualTo(original);
		// Verify collections are actually copied
		assertThat(copy.getStopSequences()).isNotSameAs(original.getStopSequences());
	}

	@Test
	void shouldUpcastToChatOptions() {
		// Given
		FunctionToolCallback callback = FunctionToolCallback.builder("function1", x -> "result")
			.description("Test function")
			.inputType(String.class)
			.build();

		ToolCallingChatOptions toolCallingChatOptions = ToolCallingChatOptions.builder()
			.model("gpt-4")
			.maxTokens(100)
			.temperature(0.7)
			.topP(1.0)
			.topK(40)
			.stopSequences(List.of("stop1", "stop2"))
			.toolNames(Set.of("function1", "function2"))
			.toolCallbacks(List.of(callback))
			.build();

		// When
		ChatOptions chatOptions = toolCallingChatOptions;

		// Then
		assertThat(chatOptions.getModel()).isEqualTo("gpt-4");
		assertThat(chatOptions.getMaxTokens()).isEqualTo(100);
		assertThat(chatOptions.getTemperature()).isEqualTo(0.7);
		assertThat(chatOptions.getTopP()).isEqualTo(1.0);
		assertThat(chatOptions.getTopK()).isEqualTo(40);
		assertThat(chatOptions.getStopSequences()).containsExactly("stop1", "stop2");
	}

	@Test
	void shouldAllowBuilderReuse() {
		// When
		ChatOptions options1 = this.builder.model("model1").temperature(0.7).build();
		ChatOptions options2 = this.builder.model("model2").build();

		// Then
		assertThat(options1.getModel()).isEqualTo("model1");
		assertThat(options1.getTemperature()).isEqualTo(0.7);
		assertThat(options2.getModel()).isEqualTo("model2");
		assertThat(options2.getTemperature()).isEqualTo(0.7); // Retains previous value
	}

	@Test
	void shouldReturnSameBuilderInstanceOnEachMethod() {
		// When
		ChatOptions.Builder returnedBuilder = this.builder.model("test");

		// Then
		assertThat(returnedBuilder).isSameAs(this.builder);
	}

	@Test
	void shouldHaveExpectedDefaultValues() {
		// When
		ChatOptions options = this.builder.build();

		// Then
		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getStopSequences()).isNull();
	}

	@Test
	void shouldBeImmutableAfterBuild() {
		// Given
		List<String> stopSequences = new ArrayList<>(List.of("stop1", "stop2"));
		ChatOptions options = this.builder.stopSequences(stopSequences).build();

		// Then
		assertThatThrownBy(() -> options.getStopSequences().add("stop3"))
			.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void shouldHandleNullStopSequences() {
		ChatOptions options = this.builder.model("test-model").stopSequences(null).build();

		assertThat(options.getStopSequences()).isNull();
	}

	@Test
	void shouldHandleEmptyStopSequences() {
		ChatOptions options = this.builder.model("test-model").stopSequences(List.of()).build();

		assertThat(options.getStopSequences()).isEmpty();
	}

	@Test
	void shouldHandleFrequencyAndPresencePenalties() {
		ChatOptions options = this.builder.model("test-model").frequencyPenalty(0.5).presencePenalty(0.3).build();

		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getPresencePenalty()).isEqualTo(0.3);
	}

	@Test
	void shouldMaintainStopSequencesOrder() {
		List<String> orderedSequences = List.of("first", "second", "third", "fourth");

		ChatOptions options = this.builder.model("test-model").stopSequences(orderedSequences).build();

		assertThat(options.getStopSequences()).containsExactly("first", "second", "third", "fourth");
	}

	@Test
	void shouldCreateIndependentCopies() {
		ChatOptions original = this.builder.model("test-model")
			.stopSequences(new ArrayList<>(List.of("stop1")))
			.build();

		ChatOptions copy1 = original.copy();
		ChatOptions copy2 = original.copy();

		assertThat(copy1).isNotSameAs(copy2);
		assertThat(copy1.getStopSequences()).isNotSameAs(copy2.getStopSequences());
		assertThat(copy1).usingRecursiveComparison().isEqualTo(copy2);
	}

	@Test
	void shouldHandleSpecialStringValues() {
		ChatOptions options = this.builder.model("") // Empty string
			.stopSequences(List.of("", "  ", "\n", "\t"))
			.build();

		assertThat(options.getModel()).isEmpty();
		assertThat(options.getStopSequences()).containsExactly("", "  ", "\n", "\t");
	}

	@Test
	void shouldPreserveCopyIntegrity() {
		List<String> mutableList = new ArrayList<>(List.of("original"));
		ChatOptions original = this.builder.model("test-model").stopSequences(mutableList).build();

		// Modify the original list after building
		mutableList.add("modified");

		ChatOptions copy = original.copy();

		assertThat(original.getStopSequences()).containsExactly("original");
		assertThat(copy.getStopSequences()).containsExactly("original");
	}

}
