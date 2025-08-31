/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.google.genai;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for GoogleGenAiChatOptions
 *
 * @author Dan Dobrin
 */
public class GoogleGenAiChatOptionsTest {

	@Test
	public void testThinkingBudgetGetterSetter() {
		GoogleGenAiChatOptions options = new GoogleGenAiChatOptions();

		assertThat(options.getThinkingBudget()).isNull();

		options.setThinkingBudget(12853);
		assertThat(options.getThinkingBudget()).isEqualTo(12853);

		options.setThinkingBudget(null);
		assertThat(options.getThinkingBudget()).isNull();
	}

	@Test
	public void testThinkingBudgetWithBuilder() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingBudget(15000)
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getThinkingBudget()).isEqualTo(15000);
	}

	@Test
	public void testFromOptionsWithThinkingBudget() {
		GoogleGenAiChatOptions original = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.temperature(0.8)
			.thinkingBudget(20000)
			.build();

		GoogleGenAiChatOptions copy = GoogleGenAiChatOptions.fromOptions(original);

		assertThat(copy.getModel()).isEqualTo("test-model");
		assertThat(copy.getTemperature()).isEqualTo(0.8);
		assertThat(copy.getThinkingBudget()).isEqualTo(20000);
		assertThat(copy).isNotSameAs(original);
	}

	@Test
	public void testCopyWithThinkingBudget() {
		GoogleGenAiChatOptions original = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingBudget(30000)
			.build();

		GoogleGenAiChatOptions copy = original.copy();

		assertThat(copy.getModel()).isEqualTo("test-model");
		assertThat(copy.getThinkingBudget()).isEqualTo(30000);
		assertThat(copy).isNotSameAs(original);
	}

	@Test
	public void testEqualsAndHashCodeWithThinkingBudget() {
		GoogleGenAiChatOptions options1 = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingBudget(12853)
			.build();

		GoogleGenAiChatOptions options2 = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingBudget(12853)
			.build();

		GoogleGenAiChatOptions options3 = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingBudget(25000)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
		assertThat(options1).isNotEqualTo(options3);
		assertThat(options1.hashCode()).isNotEqualTo(options3.hashCode());
	}

	@Test
	public void testEqualsAndHashCodeWithLabels() {
		GoogleGenAiChatOptions options1 = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.labels(Map.of("org", "my-org"))
			.build();

		GoogleGenAiChatOptions options2 = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.labels(Map.of("org", "my-org"))
			.build();

		GoogleGenAiChatOptions options3 = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.labels(Map.of("org", "other-org"))
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
		assertThat(options1).isNotEqualTo(options3);
		assertThat(options1.hashCode()).isNotEqualTo(options3.hashCode());
	}

	@Test
	public void testToStringWithThinkingBudget() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingBudget(12853)
			.build();

		String toString = options.toString();
		assertThat(toString).contains("thinkingBudget=12853");
		assertThat(toString).contains("test-model");
	}

	@Test
	public void testToStringWithLabels() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.labels(Map.of("org", "my-org"))
			.build();

		String toString = options.toString();
		assertThat(toString).contains("labels={org=my-org}");
		assertThat(toString).contains("test-model");
	}

}
