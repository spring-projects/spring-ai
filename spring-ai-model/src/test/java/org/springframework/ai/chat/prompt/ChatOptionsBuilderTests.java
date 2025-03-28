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

import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;

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
		FunctionCallback callback = FunctionCallback.builder()
			.function("function1", x -> "result")
			.description("Test function")
			.inputType(String.class)
			.build();

		FunctionCallingOptions functionOptions = FunctionCallingOptions.builder()
			.model("gpt-4")
			.maxTokens(100)
			.temperature(0.7)
			.topP(1.0)
			.topK(40)
			.stopSequences(List.of("stop1", "stop2"))
			.functions(Set.of("function1", "function2"))
			.functionCallbacks(List.of(callback))
			.build();

		// When
		ChatOptions chatOptions = functionOptions;

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

}
