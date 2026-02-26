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

import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;

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

	@Test
	public void testThinkingBudgetWithZeroValue() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().thinkingBudget(0).build();

		assertThat(options.getThinkingBudget()).isEqualTo(0);
	}

	@Test
	public void testLabelsWithEmptyMap() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().labels(Map.of()).build();

		assertThat(options.getLabels()).isEmpty();
	}

  @Test
	public void testUrlContextEnabledCopyAndEquality() {
		GoogleGenAiChatOptions original = GoogleGenAiChatOptions.builder()
				.model("test-model")
				.urlContextEnabled(true)
				.build();

		GoogleGenAiChatOptions copy = original.copy();

		assertThat(original.getUrlContextEnabled()).isTrue();
		assertThat(copy.getUrlContextEnabled()).isTrue();
		assertThat(copy).isEqualTo(original);
		assertThat(copy).isNotSameAs(original);
		assertThat(copy.toString()).contains("urlContextEnabled=true");

		GoogleGenAiChatOptions different = GoogleGenAiChatOptions.builder()
				.model("test-model")
				.urlContextEnabled(false)
				.build();

		assertThat(original).isNotEqualTo(different);
		assertThat(original.hashCode()).isNotEqualTo(different.hashCode());
	}

	@Test
	public void testThinkingLevelGetterSetter() {
		GoogleGenAiChatOptions options = new GoogleGenAiChatOptions();

		assertThat(options.getThinkingLevel()).isNull();

		options.setThinkingLevel(GoogleGenAiThinkingLevel.HIGH);
		assertThat(options.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.HIGH);

		options.setThinkingLevel(GoogleGenAiThinkingLevel.LOW);
		assertThat(options.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.LOW);

		options.setThinkingLevel(null);
		assertThat(options.getThinkingLevel()).isNull();
	}

	@Test
	public void testThinkingLevelWithBuilder() {
		GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.HIGH);
	}

	@Test
	public void testFromOptionsWithThinkingLevel() {
		GoogleGenAiChatOptions original = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
			.build();

		GoogleGenAiChatOptions copy = GoogleGenAiChatOptions.fromOptions(original);

		assertThat(copy.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.LOW);
		assertThat(copy).isNotSameAs(original);
	}

	@Test
	public void testCopyWithThinkingLevel() {
		GoogleGenAiChatOptions original = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
			.build();

		GoogleGenAiChatOptions copy = original.copy();

		assertThat(copy.getThinkingLevel()).isEqualTo(GoogleGenAiThinkingLevel.HIGH);
		assertThat(copy).isNotSameAs(original);
	}

	@Test
	public void testEqualsAndHashCodeWithThinkingLevel() {
		GoogleGenAiChatOptions options1 = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
			.build();

		GoogleGenAiChatOptions options2 = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingLevel(GoogleGenAiThinkingLevel.HIGH)
			.build();

		GoogleGenAiChatOptions options3 = GoogleGenAiChatOptions.builder()
			.model("test-model")
			.thinkingLevel(GoogleGenAiThinkingLevel.LOW)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
		assertThat(options1).isNotEqualTo(options3);
	}

	@Test
	public void testToStringWithThinkingLevel() {
		GoogleGenAiChatOptions options = Google
}
