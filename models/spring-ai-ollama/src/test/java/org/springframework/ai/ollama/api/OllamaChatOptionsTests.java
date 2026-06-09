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

package org.springframework.ai.ollama.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.ollama.api.OllamaChatOptions.Builder;
import org.springframework.ai.test.options.AbstractChatOptionsTests;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.util.ResourceUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Nicolas Krier
 */
class OllamaChatOptionsTests extends AbstractChatOptionsTests<OllamaChatOptions, Builder> {

	@Override
	protected Class<OllamaChatOptions> getConcreteOptionsClass() {
		return OllamaChatOptions.class;
	}

	@Override
	protected Builder readyToBuildBuilder() {
		return OllamaChatOptions.builder();
	}

	@Test
	void testBasicOptions() {
		var b1 = OllamaChatOptions.builder().model("model").mainGPU(12);

		var b = OllamaChatOptions.builder().mainGPU(12).model("model");

		var options = OllamaChatOptions.builder().temperature(3.14).topK(30).stop(List.of("a", "b", "c")).build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("temperature", 3.14);
		assertThat(optionsMap).containsEntry("top_k", 30);
		assertThat(optionsMap).containsEntry("stop", List.of("a", "b", "c"));
	}

	@Test
	void testAllNumericOptions() {
		var options = OllamaChatOptions.builder()
			.numCtx(2048)
			.numBatch(512)
			.numGPU(1)
			.mainGPU(0)
			.numThread(8)
			.numKeep(5)
			.seed(42)
			.numPredict(100)
			.topK(40)
			.topP(0.9)
			.tfsZ(1.0f)
			.typicalP(1.0f)
			.repeatLastN(64)
			.temperature(0.7)
			.repeatPenalty(1.1)
			.presencePenalty(0.0)
			.frequencyPenalty(0.0)
			.mirostat(2)
			.mirostatTau(5.0f)
			.mirostatEta(0.1f)
			.build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("num_ctx", 2048);
		assertThat(optionsMap).containsEntry("num_batch", 512);
		assertThat(optionsMap).containsEntry("num_gpu", 1);
		assertThat(optionsMap).containsEntry("main_gpu", 0);
		assertThat(optionsMap).containsEntry("num_thread", 8);
		assertThat(optionsMap).containsEntry("num_keep", 5);
		assertThat(optionsMap).containsEntry("seed", 42);
		assertThat(optionsMap).containsEntry("num_predict", 100);
		assertThat(optionsMap).containsEntry("top_k", 40);
		assertThat(optionsMap).containsEntry("top_p", 0.9);
		assertThat(optionsMap).containsEntry("tfs_z", 1.0f);
		assertThat(optionsMap).containsEntry("typical_p", 1.0f);
		assertThat(optionsMap).containsEntry("repeat_last_n", 64);
		assertThat(optionsMap).containsEntry("temperature", 0.7);
		assertThat(optionsMap).containsEntry("repeat_penalty", 1.1);
		assertThat(optionsMap).containsEntry("presence_penalty", 0.0);
		assertThat(optionsMap).containsEntry("frequency_penalty", 0.0);
		assertThat(optionsMap).containsEntry("mirostat", 2);
		assertThat(optionsMap).containsEntry("mirostat_tau", 5.0f);
		assertThat(optionsMap).containsEntry("mirostat_eta", 0.1f);
	}

	@Test
	void testBooleanOptions() {
		var options = OllamaChatOptions.builder()
			.truncate(true)
			.useNUMA(true)
			.lowVRAM(false)
			.f16KV(true)
			.logitsAll(false)
			.vocabOnly(false)
			.useMMap(true)
			.useMLock(false)
			.penalizeNewline(true)
			.build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("truncate", true);
		assertThat(optionsMap).containsEntry("numa", true);
		assertThat(optionsMap).containsEntry("low_vram", false);
		assertThat(optionsMap).containsEntry("f16_kv", true);
		assertThat(optionsMap).containsEntry("logits_all", false);
		assertThat(optionsMap).containsEntry("vocab_only", false);
		assertThat(optionsMap).containsEntry("use_mmap", true);
		assertThat(optionsMap).containsEntry("use_mlock", false);
		assertThat(optionsMap).containsEntry("penalize_newline", true);
	}

	@Test
	void testModelAndFormat() {
		var options = OllamaChatOptions.builder().model("llama2").format("json").build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("model", "llama2");
		assertThat(optionsMap).containsEntry("format", "json");
	}

	@Test
	void testOutputSchemaOptionWithJsonSchemaObjectAsString() {
		var jsonSchemaAsText = ResourceUtils.getText("classpath:country-json-schema.json");
		var options = OllamaChatOptions.builder().outputSchema(jsonSchemaAsText).build();

		assertThat(options.getOutputSchema()).isEqualToIgnoringWhitespace(jsonSchemaAsText);
	}

	@Test
	void testOutputSchemaOptionWithJsonAsString() {
		assertThatThrownBy(() -> OllamaChatOptions.builder().outputSchema("json"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Conversion from JSON to java.util.Map<java.lang.String, java.lang.Object> failed");
	}

	@Test
	void testFunctionAndToolOptions() {
		ToolCallback callback1 = FunctionToolCallback.builder("function1", (String s) -> s)
			.description("Function 1")
			.inputType(String.class)
			.build();
		ToolCallback callback2 = FunctionToolCallback.builder("function2", (String s) -> s)
			.description("Function 2")
			.inputType(String.class)
			.build();

		var options = OllamaChatOptions.builder()
			.toolCallbacks(callback1, callback2)
			.toolContext(Map.of("key1", "value1", "key2", "value2"))
			.build();

		// Tool-related fields are not included in the map due to @JsonIgnore
		var optionsMap = options.toMap();
		assertThat(optionsMap).doesNotContainKey("toolCallbacks");
		assertThat(optionsMap).doesNotContainKey("tool_context");

		// But they are accessible through getters
		assertThat(options.getToolCallbacks()).containsExactlyInAnyOrder(callback1, callback2);
		assertThat(options.getToolContext())
			.containsExactlyInAnyOrderEntriesOf(Map.of("key1", "value1", "key2", "value2"));
	}

	@Test
	void testFromOptions() {
		ToolCallback callback = FunctionToolCallback.builder("function1", (String s) -> s)
			.description("Function 1")
			.inputType(String.class)
			.build();

		var originalOptions = OllamaChatOptions.builder()
			.model("llama2")
			.temperature(0.7)
			.topK(40)
			.toolCallbacks(callback)
			.build();

		var copiedOptions = originalOptions.mutate().build();

		// Test the copied options directly rather than through toMap()
		assertThat(copiedOptions.getModel()).isEqualTo("llama2");
		assertThat(copiedOptions.getTemperature()).isEqualTo(0.7);
		assertThat(copiedOptions.getTopK()).isEqualTo(40);
		assertThat(copiedOptions.getToolCallbacks()).containsExactly(callback);
	}

	@Test
	void testFunctionOptionsNotInMap() {
		ToolCallback callback = FunctionToolCallback.builder("function1", (String s) -> s)
			.description("Function 1")
			.inputType(String.class)
			.build();

		var options = OllamaChatOptions.builder().model("llama2").toolCallbacks(callback).build();

		var optionsMap = options.toMap();

		// Verify tool-related fields are not included in the map due to @JsonIgnore
		assertThat(optionsMap).containsEntry("model", "llama2");
		assertThat(optionsMap).doesNotContainKey("toolCallbacks");
		assertThat(optionsMap).doesNotContainKey("proxyToolCalls");
		assertThat(optionsMap).doesNotContainKey("toolContext");

		// But verify they are still accessible through getters
		assertThat(options.getToolCallbacks()).containsExactly(callback);
	}

	@Test
	void testEmptyOptions() {
		var options = OllamaChatOptions.builder().build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsOnlyKeys("model");
		assertThat(optionsMap.get("model")).isEqualTo(OllamaModel.MISTRAL.id());

		assertThat(options.getModel()).isEqualTo(OllamaModel.MISTRAL.id());

		// Verify all getters return null
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getToolCallbacks()).isNull();
		assertThat(options.getToolContext()).isNull();
	}

	@Test
	void testNullValuesNotIncludedInMap() {
		var options = OllamaChatOptions.builder().model("llama2").temperature(null).topK(null).stop(null).build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("model", "llama2");
		assertThat(optionsMap).doesNotContainKey("temperature");
		assertThat(optionsMap).doesNotContainKey("top_k");
		assertThat(optionsMap).doesNotContainKey("stop");
	}

	@Test
	void testZeroValuesIncludedInMap() {
		var options = OllamaChatOptions.builder().temperature(0.0).topK(0).mainGPU(0).numGPU(0).seed(0).build();

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("temperature", 0.0);
		assertThat(optionsMap).containsEntry("top_k", 0);
		assertThat(optionsMap).containsEntry("main_gpu", 0);
		assertThat(optionsMap).containsEntry("num_gpu", 0);
		assertThat(optionsMap).containsEntry("seed", 0);
	}

	/**
	 * Demonstrates the difference between simple "json" format and JSON Schema format.
	 *
	 * Simple "json" format: Tells Ollama to return any valid JSON structure. JSON Schema
	 * format: Tells Ollama to return JSON matching a specific schema.
	 */
	@Test
	void testSimpleJsonFormatVsJsonSchema() {
		var simpleJsonOptions = OllamaChatOptions.builder().format("json").build();

		var simpleJsonMap = simpleJsonOptions.toMap();
		assertThat(simpleJsonMap).containsEntry("format", "json");
		assertThat(simpleJsonOptions.getFormat()).isEqualTo("json");

		var jsonSchemaAsText = ResourceUtils.getText("classpath:country-json-schema.json");
		var schemaOptions = OllamaChatOptions.builder().outputSchema(jsonSchemaAsText).build();

		var schemaMap = schemaOptions.toMap();
		assertThat(schemaMap).containsKey("format");
		assertThat(schemaMap.get("format")).isInstanceOf(Map.class);

		// Verify the schema contains expected structure
		@SuppressWarnings("unchecked")
		Map<String, Object> formatSchema = (Map<String, Object>) schemaMap.get("format");
		assertThat(formatSchema).containsEntry("type", "object");
		assertThat(formatSchema).containsKey("properties");
		assertThat(formatSchema).containsKey("required");

		var formatOnlyOptions = OllamaChatOptions.builder().format("json").build();
		assertThat(formatOnlyOptions.getOutputSchema()).isEqualTo("json");

		var schemaRoundTrip = OllamaChatOptions.builder().outputSchema(jsonSchemaAsText).build();
		assertThat(schemaRoundTrip.getOutputSchema()).isEqualToIgnoringWhitespace(jsonSchemaAsText);
	}

	/**
	 * Tests that setFormat("json") and getFormat() work correctly for simple JSON format.
	 */
	@Test
	void testSimpleJsonFormatDirectAccess() {
		var options = OllamaChatOptions.builder().format("json").build();

		assertThat(options.getFormat()).isEqualTo("json");

		var optionsMap = options.toMap();
		assertThat(optionsMap).containsEntry("format", "json");

		// Verify it serializes correctly
		assertThat(options.getFormat()).isInstanceOf(String.class);
	}

	/**
	 * Tests getOutputSchema() properly handles all format types: null, String, and Map.
	 */
	@Test
	void testGetOutputSchemaHandlesAllFormatTypes() {
		var nullFormatOptions = OllamaChatOptions.builder().build();
		assertThat(nullFormatOptions.getOutputSchema()).isNull();

		var stringFormatOptions = OllamaChatOptions.builder().format("json").build();
		assertThat(stringFormatOptions.getOutputSchema()).isEqualTo("json");
		assertThat(stringFormatOptions.getOutputSchema()).doesNotContain("\"");

		var jsonSchemaAsText = ResourceUtils.getText("classpath:country-json-schema.json");
		var schemaFormatOptions = OllamaChatOptions.builder().outputSchema(jsonSchemaAsText).build();
		String retrievedSchema = schemaFormatOptions.getOutputSchema();

		// Should be valid JSON
		assertThat(retrievedSchema).isNotNull();
		assertThat(retrievedSchema).contains("\"type\"");
		assertThat(retrievedSchema).contains("\"properties\"");
		assertThat(retrievedSchema).contains("\"required\"");

		assertThat(retrievedSchema).isEqualToIgnoringWhitespace(jsonSchemaAsText);
	}

	/**
	 * Tests that outputSchema() properly handles JSON Schema strings.
	 */
	@Test
	void testSetOutputSchemaWithValidJsonSchema() {
		var jsonSchemaAsText = ResourceUtils.getText("classpath:country-json-schema.json");

		var options = OllamaChatOptions.builder().outputSchema(jsonSchemaAsText).build();

		// Format should be a Map, not a String
		assertThat(options.getFormat()).isInstanceOf(Map.class);

		// toMap() should contain the parsed schema
		var optionsMap = options.toMap();
		assertThat(optionsMap).containsKey("format");
		assertThat(optionsMap.get("format")).isInstanceOf(Map.class);

		// getOutputSchema() should return the original JSON string (ignoring whitespace)
		assertThat(options.getOutputSchema()).isEqualToIgnoringWhitespace(jsonSchemaAsText);
	}

	@Test
	void testCombineWithCollections() {
		ToolCallback baseTool = FunctionToolCallback.builder("base-tool", (String s) -> s)
			.description("Base tool")
			.inputType(String.class)
			.build();
		ToolCallback combineTool1 = FunctionToolCallback.builder("combine-tool1", (String s) -> s)
			.description("Combine tool 1")
			.inputType(String.class)
			.build();
		ToolCallback combineTool2 = FunctionToolCallback.builder("combine-tool2", (String s) -> s)
			.description("Combine tool 2")
			.inputType(String.class)
			.build();

		var base = OllamaChatOptions.builder()
			.stop(List.of("base-stop"))
			.toolCallbacks(baseTool)
			.toolContext(Map.of("base-key", "base-value"));

		var combine = OllamaChatOptions.builder()
			.stop(List.of("combine-stop1", "combine-stop2"))
			.toolCallbacks(combineTool1, combineTool2)
			.toolContext(Map.of("combine-key1", "combine-value1", "combine-key2", "combine-value2"));

		var merged = base.combineWith(combine).build();

		assertThat(merged.getStop()).containsExactly("base-stop", "combine-stop1", "combine-stop2");
		assertThat(merged.getToolCallbacks()).containsExactlyInAnyOrder(baseTool, combineTool1, combineTool2);
		assertThat(merged.getToolContext()).containsEntry("base-key", "base-value");
		assertThat(merged.getToolContext()).containsEntry("combine-key1", "combine-value1");
		assertThat(merged.getToolContext()).containsEntry("combine-key2", "combine-value2");
	}

}
