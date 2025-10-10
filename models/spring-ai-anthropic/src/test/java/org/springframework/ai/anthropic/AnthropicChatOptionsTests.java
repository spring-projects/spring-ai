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

package org.springframework.ai.anthropic;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest.Metadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AnthropicChatOptions}.
 *
 * @author Alexandros Pappas
 */
class AnthropicChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.metadata(new Metadata("userId_123"))
			.build();

		assertThat(options).extracting("model", "maxTokens", "stopSequences", "temperature", "topP", "topK", "metadata")
			.containsExactly("test-model", 100, List.of("stop1", "stop2"), 0.7, 0.8, 50, new Metadata("userId_123"));
	}

	@Test
	void testCopy() {
		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.metadata(new Metadata("userId_123"))
			.toolContext(Map.of("key1", "value1"))
			.build();

		AnthropicChatOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		// Ensure deep copy
		assertThat(copied.getStopSequences()).isNotSameAs(original.getStopSequences());
		assertThat(copied.getToolContext()).isNotSameAs(original.getToolContext());
	}

	@Test
	void testSetters() {
		AnthropicChatOptions options = new AnthropicChatOptions();
		options.setModel("test-model");
		options.setMaxTokens(100);
		options.setTemperature(0.7);
		options.setTopK(50);
		options.setTopP(0.8);
		options.setStopSequences(List.of("stop1", "stop2"));
		options.setMetadata(new Metadata("userId_123"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getTopP()).isEqualTo(0.8);
		assertThat(options.getStopSequences()).isEqualTo(List.of("stop1", "stop2"));
		assertThat(options.getMetadata()).isEqualTo(new Metadata("userId_123"));
	}

	@Test
	void testDefaultValues() {
		AnthropicChatOptions options = new AnthropicChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getMetadata()).isNull();
	}

	@Test
	void testBuilderWithEmptyCollections() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.stopSequences(Collections.emptyList())
			.toolContext(Collections.emptyMap())
			.build();

		assertThat(options.getStopSequences()).isEmpty();
		assertThat(options.getToolContext()).isEmpty();
	}

	@Test
	void testBuilderWithSingleElementCollections() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.stopSequences(List.of("single-stop"))
			.toolContext(Map.of("single-key", "single-value"))
			.build();

		assertThat(options.getStopSequences()).hasSize(1).containsExactly("single-stop");
		assertThat(options.getToolContext()).hasSize(1).containsEntry("single-key", "single-value");
	}

	@Test
	void testCopyWithEmptyOptions() {
		AnthropicChatOptions emptyOptions = new AnthropicChatOptions();
		AnthropicChatOptions copiedOptions = emptyOptions.copy();

		assertThat(copiedOptions).isNotSameAs(emptyOptions).isEqualTo(emptyOptions);
		assertThat(copiedOptions.getModel()).isNull();
		assertThat(copiedOptions.getMaxTokens()).isNull();
		assertThat(copiedOptions.getTemperature()).isNull();
	}

	@Test
	void testCopyMutationDoesNotAffectOriginal() {
		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.model("original-model")
			.maxTokens(100)
			.temperature(0.5)
			.stopSequences(List.of("original-stop"))
			.toolContext(Map.of("original", "value"))
			.build();

		AnthropicChatOptions copy = original.copy();
		copy.setModel("modified-model");
		copy.setMaxTokens(200);
		copy.setTemperature(0.8);

		// Original should remain unchanged
		assertThat(original.getModel()).isEqualTo("original-model");
		assertThat(original.getMaxTokens()).isEqualTo(100);
		assertThat(original.getTemperature()).isEqualTo(0.5);

		// Copy should have new values
		assertThat(copy.getModel()).isEqualTo("modified-model");
		assertThat(copy.getMaxTokens()).isEqualTo(200);
		assertThat(copy.getTemperature()).isEqualTo(0.8);
	}

	@Test
	void testEqualsAndHashCode() {
		AnthropicChatOptions options1 = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.temperature(0.7)
			.build();

		AnthropicChatOptions options2 = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.temperature(0.7)
			.build();

		AnthropicChatOptions options3 = AnthropicChatOptions.builder()
			.model("different-model")
			.maxTokens(100)
			.temperature(0.7)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());

		assertThat(options1).isNotEqualTo(options3);
		assertThat(options1.hashCode()).isNotEqualTo(options3.hashCode());
	}

	@Test
	void testChainedBuilderMethods() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(150)
			.temperature(0.6)
			.topP(0.9)
			.topK(40)
			.stopSequences(List.of("stop"))
			.metadata(new Metadata("user_456"))
			.toolContext(Map.of("context", "value"))
			.build();

		// Verify all chained methods worked
		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getMaxTokens()).isEqualTo(150);
		assertThat(options.getTemperature()).isEqualTo(0.6);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getTopK()).isEqualTo(40);
		assertThat(options.getStopSequences()).containsExactly("stop");
		assertThat(options.getMetadata()).isEqualTo(new Metadata("user_456"));
		assertThat(options.getToolContext()).containsEntry("context", "value");
	}

	@Test
	void testSettersWithNullValues() {
		AnthropicChatOptions options = new AnthropicChatOptions();

		options.setModel(null);
		options.setMaxTokens(null);
		options.setTemperature(null);
		options.setTopK(null);
		options.setTopP(null);
		options.setStopSequences(null);
		options.setMetadata(null);
		options.setToolContext(null);

		assertThat(options.getModel()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getMetadata()).isNull();
		assertThat(options.getToolContext()).isNull();
	}

	@Test
	void testBuilderAndSetterConsistency() {
		// Build an object using builder
		AnthropicChatOptions builderOptions = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.build();

		// Create equivalent object using setters
		AnthropicChatOptions setterOptions = new AnthropicChatOptions();
		setterOptions.setModel("test-model");
		setterOptions.setMaxTokens(100);
		setterOptions.setTemperature(0.7);
		setterOptions.setTopP(0.8);
		setterOptions.setTopK(50);

		assertThat(builderOptions).isEqualTo(setterOptions);
	}

	@Test
	void testMetadataEquality() {
		Metadata metadata1 = new Metadata("user_123");
		Metadata metadata2 = new Metadata("user_123");
		Metadata metadata3 = new Metadata("user_456");

		AnthropicChatOptions options1 = AnthropicChatOptions.builder().metadata(metadata1).build();

		AnthropicChatOptions options2 = AnthropicChatOptions.builder().metadata(metadata2).build();

		AnthropicChatOptions options3 = AnthropicChatOptions.builder().metadata(metadata3).build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1).isNotEqualTo(options3);
	}

	@Test
	void testZeroValues() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.maxTokens(0)
			.temperature(0.0)
			.topP(0.0)
			.topK(0)
			.build();

		assertThat(options.getMaxTokens()).isEqualTo(0);
		assertThat(options.getTemperature()).isEqualTo(0.0);
		assertThat(options.getTopP()).isEqualTo(0.0);
		assertThat(options.getTopK()).isEqualTo(0);
	}

	@Test
	void testCopyPreservesAllFields() {
		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.model("comprehensive-model")
			.maxTokens(500)
			.stopSequences(List.of("stop1", "stop2", "stop3"))
			.temperature(0.75)
			.topP(0.85)
			.topK(60)
			.metadata(new Metadata("comprehensive_test"))
			.toolContext(Map.of("key1", "value1", "key2", "value2"))
			.build();

		AnthropicChatOptions copied = original.copy();

		// Verify all fields are preserved
		assertThat(copied.getModel()).isEqualTo(original.getModel());
		assertThat(copied.getMaxTokens()).isEqualTo(original.getMaxTokens());
		assertThat(copied.getStopSequences()).isEqualTo(original.getStopSequences());
		assertThat(copied.getTemperature()).isEqualTo(original.getTemperature());
		assertThat(copied.getTopP()).isEqualTo(original.getTopP());
		assertThat(copied.getTopK()).isEqualTo(original.getTopK());
		assertThat(copied.getMetadata()).isEqualTo(original.getMetadata());
		assertThat(copied.getToolContext()).isEqualTo(original.getToolContext());

		// Ensure deep copy for collections
		assertThat(copied.getStopSequences()).isNotSameAs(original.getStopSequences());
		assertThat(copied.getToolContext()).isNotSameAs(original.getToolContext());
	}

	@Test
	void testBoundaryValues() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.maxTokens(Integer.MAX_VALUE)
			.temperature(1.0)
			.topP(1.0)
			.topK(Integer.MAX_VALUE)
			.build();

		assertThat(options.getMaxTokens()).isEqualTo(Integer.MAX_VALUE);
		assertThat(options.getTemperature()).isEqualTo(1.0);
		assertThat(options.getTopP()).isEqualTo(1.0);
		assertThat(options.getTopK()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	void testToolContextWithVariousValueTypes() {
		Map<String, Object> mixedMap = Map.of("string", "value", "number", 42, "boolean", true, "null_value", "null",
				"nested_list", List.of("a", "b", "c"), "nested_map", Map.of("inner", "value"));

		AnthropicChatOptions options = AnthropicChatOptions.builder().toolContext(mixedMap).build();

		assertThat(options.getToolContext()).containsAllEntriesOf(mixedMap);
		assertThat(options.getToolContext().get("string")).isEqualTo("value");
		assertThat(options.getToolContext().get("number")).isEqualTo(42);
		assertThat(options.getToolContext().get("boolean")).isEqualTo(true);
	}

	@Test
	void testCopyWithMutableCollections() {
		List<String> mutableStops = new java.util.ArrayList<>(List.of("stop1", "stop2"));
		Map<String, Object> mutableContext = new java.util.HashMap<>(Map.of("key", "value"));

		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.stopSequences(mutableStops)
			.toolContext(mutableContext)
			.build();

		AnthropicChatOptions copied = original.copy();

		// Modify original collections
		mutableStops.add("stop3");
		mutableContext.put("new_key", "new_value");

		// Copied instance should not be affected
		assertThat(copied.getStopSequences()).hasSize(2);
		assertThat(copied.getToolContext()).hasSize(1);
		assertThat(copied.getStopSequences()).doesNotContain("stop3");
		assertThat(copied.getToolContext()).doesNotContainKey("new_key");
	}

	@Test
	void testEqualsWithNullFields() {
		AnthropicChatOptions options1 = new AnthropicChatOptions();
		AnthropicChatOptions options2 = new AnthropicChatOptions();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testEqualsWithMixedNullAndNonNullFields() {
		AnthropicChatOptions options1 = AnthropicChatOptions.builder()
			.model("test")
			.maxTokens(null)
			.temperature(0.5)
			.build();

		AnthropicChatOptions options2 = AnthropicChatOptions.builder()
			.model("test")
			.maxTokens(null)
			.temperature(0.5)
			.build();

		AnthropicChatOptions options3 = AnthropicChatOptions.builder()
			.model("test")
			.maxTokens(100)
			.temperature(0.5)
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1).isNotEqualTo(options3);
	}

	@Test
	void testCopyDoesNotShareMetadataReference() {
		Metadata originalMetadata = new Metadata("user_123");
		AnthropicChatOptions original = AnthropicChatOptions.builder().metadata(originalMetadata).build();

		AnthropicChatOptions copied = original.copy();

		// Metadata should be the same value but potentially different reference
		assertThat(copied.getMetadata()).isEqualTo(original.getMetadata());

		// Verify changing original doesn't affect copy
		original.setMetadata(new Metadata("different_user"));
		assertThat(copied.getMetadata()).isEqualTo(originalMetadata);
	}

	@Test
	void testEqualsWithSelf() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().model("test").build();

		assertThat(options).isEqualTo(options);
		assertThat(options.hashCode()).isEqualTo(options.hashCode());
	}

	@Test
	void testEqualsWithNull() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().model("test").build();

		assertThat(options).isNotEqualTo(null);
	}

	@Test
	void testEqualsWithDifferentClass() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().model("test").build();

		assertThat(options).isNotEqualTo("not an AnthropicChatOptions");
		assertThat(options).isNotEqualTo(1);
	}

	@Test
	void testBuilderPartialConfiguration() {
		// Test builder with only some fields set
		AnthropicChatOptions onlyModel = AnthropicChatOptions.builder().model("model-only").build();

		AnthropicChatOptions onlyTokens = AnthropicChatOptions.builder().maxTokens(10).build();

		AnthropicChatOptions onlyTemperature = AnthropicChatOptions.builder().temperature(0.8).build();

		assertThat(onlyModel.getModel()).isEqualTo("model-only");
		assertThat(onlyModel.getMaxTokens()).isNull();

		assertThat(onlyTokens.getModel()).isNull();
		assertThat(onlyTokens.getMaxTokens()).isEqualTo(10);

		assertThat(onlyTemperature.getModel()).isNull();
		assertThat(onlyTemperature.getTemperature()).isEqualTo(0.8);
	}

	@Test
	void testSetterOverwriteBehavior() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().model("initial-model").maxTokens(100).build();

		// Overwrite with setters
		options.setModel("updated-model");
		options.setMaxTokens(10);

		assertThat(options.getModel()).isEqualTo("updated-model");
		assertThat(options.getMaxTokens()).isEqualTo(10);
	}

}
