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

package org.springframework.ai.anthropic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Metadata;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigEnabled;
import com.anthropic.models.messages.ThinkingConfigParam;
import com.anthropic.models.messages.ToolChoice;
import com.anthropic.models.messages.ToolChoiceAuto;
import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.AnthropicChatOptions.Builder;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.test.options.AbstractChatOptionsTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AnthropicChatOptions}. Focuses on critical behaviors: builder,
 * copy, mutate, combineWith, equals/hashCode, and validation.
 *
 * @author Soby Chacko
 * @author Sebastien Deleuze
 */
class AnthropicChatOptionsTests extends AbstractChatOptionsTests<AnthropicChatOptions, Builder> {

	@Override
	protected Class<AnthropicChatOptions> getConcreteOptionsClass() {
		return AnthropicChatOptions.class;
	}

	@Override
	protected Builder readyToBuildBuilder() {
		return AnthropicChatOptions.builder().model(Model.CLAUDE_HAIKU_4_5).maxTokens(500);
	}

	@Test
	void testBuilderWithAllFields() {
		Metadata metadata = Metadata.builder().userId("userId_123").build();
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.metadata(metadata)
			.baseUrl("https://custom.api.com")
			.timeout(Duration.ofSeconds(120))
			.maxRetries(5)
			.toolChoice(ToolChoice.ofAuto(ToolChoiceAuto.builder().build()))
			.disableParallelToolUse(true)
			.toolNames("tool1", "tool2")
			.toolContext(Map.of("key", "value"))
			.internalToolExecutionEnabled(true)
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getStopSequences()).containsExactly("stop1", "stop2");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.8);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getMetadata()).isEqualTo(metadata);
		assertThat(options.getBaseUrl()).isEqualTo("https://custom.api.com");
		assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(120));
		assertThat(options.getMaxRetries()).isEqualTo(5);
		assertThat(options.getToolChoice()).isNotNull();
		assertThat(options.getDisableParallelToolUse()).isTrue();
		assertThat(options.getToolNames()).containsExactlyInAnyOrder("tool1", "tool2");
		assertThat(options.getToolContext()).containsEntry("key", "value");
		assertThat(options.getInternalToolExecutionEnabled()).isTrue();
	}

	@Test
	void testBuilderWithModelEnum() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().model(Model.CLAUDE_SONNET_4_20250514).build();

		assertThat(options.getModel()).isEqualTo("claude-sonnet-4-20250514");
	}

	@Test
	void testCopyCreatesIndependentInstance() {
		Metadata metadata = Metadata.builder().userId("userId_123").build();
		List<String> mutableStops = new ArrayList<>(List.of("stop1", "stop2"));
		Map<String, Object> mutableContext = new HashMap<>(Map.of("key1", "value1"));

		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stopSequences(mutableStops)
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.metadata(metadata)
			.toolContext(mutableContext)
			.disableParallelToolUse(true)
			.build();

		AnthropicChatOptions copied = original.copy();

		// Verify copied is equal but not same instance
		assertThat(copied).isNotSameAs(original);
		assertThat(copied).isEqualTo(original);

		// Verify collections are deep copied
		assertThat(copied.getStopSequences()).isNotSameAs(original.getStopSequences());
		assertThat(copied.getToolContext()).isNotSameAs(original.getToolContext());

		// Modify original collections and verify copy is unchanged
		mutableStops.add("stop3");
		mutableContext.put("key2", "value2");
		assertThat(copied.getStopSequences()).hasSize(2);
		assertThat(copied.getToolContext()).hasSize(1);
	}

	@Test
	void testCombineWithOverridesOnlyNonNullValues() {
		AnthropicChatOptions base = AnthropicChatOptions.builder()
			.model("base-model")
			.maxTokens(100)
			.temperature(0.5)
			.topP(0.8)
			.baseUrl("https://base.api.com")
			.timeout(Duration.ofSeconds(60))
			.build();

		AnthropicChatOptions override = AnthropicChatOptions.builder()
			.model("override-model")
			.topK(40)
			// maxTokens, temperature, topP, baseUrl, timeout are null
			.build();

		AnthropicChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		// Override values take precedence
		assertThat(merged.getModel()).isEqualTo("override-model");
		assertThat(merged.getTopK()).isEqualTo(40);

		// Base values preserved when override is null
		assertThat(merged.getMaxTokens()).isEqualTo(100);
		assertThat(merged.getTemperature()).isEqualTo(0.5);
		assertThat(merged.getTopP()).isEqualTo(0.8);
		assertThat(merged.getBaseUrl()).isEqualTo("https://base.api.com");
		assertThat(merged.getTimeout()).isEqualTo(Duration.ofSeconds(60));
	}

	@Test
	void testCombineWithCollections() {
		AnthropicChatOptions base = AnthropicChatOptions.builder()
			.stopSequences(List.of("base-stop"))
			.toolNames(Set.of("base-tool"))
			.toolContext(Map.of("base-key", "base-value"))
			.build();

		AnthropicChatOptions override = AnthropicChatOptions.builder()
			.stopSequences(List.of("override-stop1", "override-stop2"))
			.toolNames(Set.of("override-tool"))
			// toolContext is empty, should not override
			.build();

		AnthropicChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		// Non-empty collections from override take precedence
		assertThat(merged.getStopSequences()).containsExactly("override-stop1", "override-stop2");
		assertThat(merged.getToolNames()).containsExactly("override-tool");
		// Empty collections don't override
		assertThat(merged.getToolContext()).containsEntry("base-key", "base-value");
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

		// Equal objects
		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());

		// Different objects
		assertThat(options1).isNotEqualTo(options3);

		// Null and different type
		assertThat(options1).isNotEqualTo(null);
		assertThat(options1).isNotEqualTo("not an options object");
	}

	@Test
	void testToolCallbacksValidationRejectsNull() {
		assertThatThrownBy(
				() -> AnthropicChatOptions.builder().toolCallbacks((org.springframework.ai.tool.ToolCallback[]) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolCallbacks cannot be null");
	}

	@Test
	void testToolNamesValidationRejectsNull() {
		assertThatThrownBy(() -> AnthropicChatOptions.builder().toolNames((String[]) null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("toolNames cannot be null");
	}

	@Test
	void testDefaultConstants() {
		assertThat(AnthropicChatOptions.DEFAULT_MODEL).isEqualTo("claude-haiku-4-5");
		assertThat(AnthropicChatOptions.DEFAULT_MAX_TOKENS).isEqualTo(4096);
	}

	@Test
	void testUnsupportedPenaltyMethodsReturnNull() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().build();

		// Anthropic API does not support these OpenAI-specific parameters
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
	}

	@Test
	void testImplementsStructuredOutputChatOptions() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().build();
		assertThat(options).isInstanceOf(StructuredOutputChatOptions.class);
	}

	@Test
	void testOutputSchemaRoundTrip() {
		String schema = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}";

		AnthropicChatOptions options = AnthropicChatOptions.builder().outputSchema(schema).build();

		assertThat(options.getOutputSchema()).isNotNull();
		assertThat(options.getOutputConfig()).isNotNull();
		assertThat(options.getOutputConfig().format()).isPresent();

		// Verify round-trip: the schema should parse and serialize back
		String roundTripped = options.getOutputSchema();
		assertThat(roundTripped).contains("\"type\"");
		assertThat(roundTripped).contains("\"properties\"");
		assertThat(roundTripped).contains("\"name\"");
		assertThat(roundTripped).contains("\"required\"");
	}

	@Test
	void testEffortConfiguration() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().effort(OutputConfig.Effort.HIGH).build();

		assertThat(options.getOutputConfig()).isNotNull();
		assertThat(options.getOutputConfig().effort()).isPresent();
		assertThat(options.getOutputConfig().effort().get()).isEqualTo(OutputConfig.Effort.HIGH);
		// No format set, so outputSchema should be null
		assertThat(options.getOutputSchema()).isNull();
	}

	@Test
	void testOutputConfigWithEffortAndSchema() {
		String schema = "{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"string\"}}}";

		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.effort(OutputConfig.Effort.HIGH)
			.outputSchema(schema)
			.build();

		assertThat(options.getOutputConfig()).isNotNull();
		assertThat(options.getOutputConfig().effort()).isPresent();
		assertThat(options.getOutputConfig().effort().get()).isEqualTo(OutputConfig.Effort.HIGH);
		assertThat(options.getOutputConfig().format()).isPresent();
		assertThat(options.getOutputSchema()).contains("result");
	}

	@Test
	void testOutputConfigDirectBuilder() {
		OutputConfig outputConfig = OutputConfig.builder()
			.effort(OutputConfig.Effort.MEDIUM)
			.format(JsonOutputFormat.builder()
				.schema(JsonOutputFormat.Schema.builder()
					.putAdditionalProperty("type", JsonValue.from("object"))
					.build())
				.build())
			.build();

		AnthropicChatOptions options = AnthropicChatOptions.builder().outputConfig(outputConfig).build();

		assertThat(options.getOutputConfig()).isNotNull();
		assertThat(options.getOutputConfig().effort()).isPresent();
		assertThat(options.getOutputConfig().format()).isPresent();
		assertThat(options.getOutputSchema()).contains("object");
	}

	@Test
	void testCombineWithPreservesOutputConfig() {
		OutputConfig outputConfig = OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build();

		AnthropicChatOptions base = AnthropicChatOptions.builder().model("base-model").build();

		AnthropicChatOptions override = AnthropicChatOptions.builder().outputConfig(outputConfig).build();

		AnthropicChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		assertThat(merged.getModel()).isEqualTo("base-model");
		assertThat(merged.getOutputConfig()).isNotNull();
		assertThat(merged.getOutputConfig().effort()).isPresent();
		assertThat(merged.getOutputConfig().effort().get()).isEqualTo(OutputConfig.Effort.MEDIUM);
	}

	@Test
	void testOutputConfigNullSchemaResetsConfig() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().outputSchema("{\"type\":\"object\"}").build();
		assertThat(options.getOutputConfig()).isNotNull();

		AnthropicChatOptions options2 = options.mutate().outputSchema(null).build();
		assertThat(options2.getOutputConfig()).isNull();
		assertThat(options2.getOutputSchema()).isNull();
	}

	@Test
	void testHttpHeadersBuilder() {
		Map<String, String> headers = Map.of("X-Custom-Header", "value1", "X-Request-Id", "req-123");

		AnthropicChatOptions options = AnthropicChatOptions.builder().httpHeaders(headers).build();

		assertThat(options.getHttpHeaders()).containsEntry("X-Custom-Header", "value1");
		assertThat(options.getHttpHeaders()).containsEntry("X-Request-Id", "req-123");
	}

	@Test
	void testHttpHeadersDefaultEmpty() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().build();
		assertThat(options.getHttpHeaders()).isNotNull().isEmpty();
	}

	@Test
	void testHttpHeadersCopiedInMutate() {
		Map<String, String> headers = new HashMap<>(Map.of("X-Custom", "value"));

		AnthropicChatOptions original = AnthropicChatOptions.builder().httpHeaders(headers).build();

		AnthropicChatOptions copied = original.mutate().build();

		assertThat(copied.getHttpHeaders()).containsEntry("X-Custom", "value");

		// Verify deep copy — modifying original doesn't affect copy
		original.getHttpHeaders().put("X-New", "new-value");
		assertThat(copied.getHttpHeaders()).doesNotContainKey("X-New");
	}

	@Test
	void testCombineWithPreservesHttpHeaders() {
		AnthropicChatOptions base = AnthropicChatOptions.builder().httpHeaders(Map.of("X-Base", "base-value")).build();

		AnthropicChatOptions override = AnthropicChatOptions.builder()
			.httpHeaders(Map.of("X-Override", "override-value"))
			.build();

		AnthropicChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		// Override's non-empty headers replace base
		assertThat(merged.getHttpHeaders()).containsEntry("X-Override", "override-value");
		assertThat(merged.getHttpHeaders()).doesNotContainKey("X-Base");
	}

	@Test
	void testCombineWithEmptyHttpHeadersDoNotOverride() {
		AnthropicChatOptions base = AnthropicChatOptions.builder().httpHeaders(Map.of("X-Base", "base-value")).build();

		AnthropicChatOptions override = AnthropicChatOptions.builder().build();

		AnthropicChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		// Base headers preserved when override is empty
		assertThat(merged.getHttpHeaders()).containsEntry("X-Base", "base-value");
	}

	@Test
	void testHttpHeadersInEqualsAndHashCode() {
		AnthropicChatOptions options1 = AnthropicChatOptions.builder().httpHeaders(Map.of("X-Header", "value")).build();

		AnthropicChatOptions options2 = AnthropicChatOptions.builder().httpHeaders(Map.of("X-Header", "value")).build();

		AnthropicChatOptions options3 = AnthropicChatOptions.builder()
			.httpHeaders(Map.of("X-Header", "different"))
			.build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
		assertThat(options1).isNotEqualTo(options3);
	}

	@Test
	void testCitationConsistencyValidationPasses() {
		AnthropicCitationDocument doc1 = AnthropicCitationDocument.builder()
			.plainText("Text 1")
			.title("Doc 1")
			.citationsEnabled(true)
			.build();
		AnthropicCitationDocument doc2 = AnthropicCitationDocument.builder()
			.plainText("Text 2")
			.title("Doc 2")
			.citationsEnabled(true)
			.build();

		// Should not throw — all documents have consistent citation settings
		AnthropicChatOptions options = AnthropicChatOptions.builder().citationDocuments(doc1, doc2).build();

		assertThat(options.getCitationDocuments()).hasSize(2);
	}

	@Test
	void testCitationConsistencyValidationFailsOnMixed() {
		AnthropicCitationDocument enabled = AnthropicCitationDocument.builder()
			.plainText("Text 1")
			.title("Doc 1")
			.citationsEnabled(true)
			.build();
		AnthropicCitationDocument disabled = AnthropicCitationDocument.builder()
			.plainText("Text 2")
			.title("Doc 2")
			.citationsEnabled(false)
			.build();

		assertThatThrownBy(() -> AnthropicChatOptions.builder().citationDocuments(enabled, disabled).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("consistent citation settings");
	}

	@Test
	void testCitationConsistencyValidationSkipsEmpty() {
		// Should not throw — no documents
		AnthropicChatOptions options = AnthropicChatOptions.builder().build();
		assertThat(options.getCitationDocuments()).isEmpty();
	}

	@Test
	void testSkillBuilderWithStringId() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().skill("xlsx").build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().getSkills()).hasSize(1);
		assertThat(options.getSkillContainer().getSkills().get(0).getSkillId()).isEqualTo("xlsx");
		assertThat(options.getSkillContainer().getSkills().get(0).getType()).isEqualTo(AnthropicSkillType.ANTHROPIC);
		assertThat(options.getSkillContainer().getSkills().get(0).getVersion()).isEqualTo("latest");
	}

	@Test
	void testSkillBuilderWithEnum() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().skill(AnthropicSkill.PPTX).build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().getSkills().get(0).getSkillId()).isEqualTo("pptx");
		assertThat(options.getSkillContainer().getSkills().get(0).getType()).isEqualTo(AnthropicSkillType.ANTHROPIC);
	}

	@Test
	void testMultipleSkills() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.skill(AnthropicSkill.XLSX)
			.skill(AnthropicSkill.PPTX)
			.build();

		assertThat(options.getSkillContainer()).isNotNull();
		assertThat(options.getSkillContainer().getSkills()).hasSize(2);
		assertThat(options.getSkillContainer().getSkills().get(0).getSkillId()).isEqualTo("xlsx");
		assertThat(options.getSkillContainer().getSkills().get(1).getSkillId()).isEqualTo("pptx");
	}

	@Test
	void testSkillContainerCopiedInMutate() {
		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.skill(AnthropicSkill.XLSX)
			.skill(AnthropicSkill.PDF)
			.build();

		AnthropicChatOptions copied = original.mutate().build();

		assertThat(copied.getSkillContainer()).isNotNull();
		assertThat(copied.getSkillContainer().getSkills()).hasSize(2);
		assertThat(copied.getSkillContainer().getSkills().get(0).getSkillId()).isEqualTo("xlsx");
		assertThat(copied.getSkillContainer().getSkills().get(1).getSkillId()).isEqualTo("pdf");
	}

	@Test
	void testCombineWithPreservesSkillContainer() {
		AnthropicChatOptions base = AnthropicChatOptions.builder().model("base-model").build();

		AnthropicChatOptions override = AnthropicChatOptions.builder().skill(AnthropicSkill.DOCX).build();

		AnthropicChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		assertThat(merged.getModel()).isEqualTo("base-model");
		assertThat(merged.getSkillContainer()).isNotNull();
		assertThat(merged.getSkillContainer().getSkills()).hasSize(1);
		assertThat(merged.getSkillContainer().getSkills().get(0).getSkillId()).isEqualTo("docx");
	}

	@Test
	void testSkillContainerDefaultIsNull() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().build();
		assertThat(options.getSkillContainer()).isNull();
	}

	@Test
	void testInferenceGeoBuilder() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().inferenceGeo("eu").build();
		assertThat(options.getInferenceGeo()).isEqualTo("eu");
	}

	@Test
	void testInferenceGeoPreservedInMutate() {
		AnthropicChatOptions original = AnthropicChatOptions.builder().inferenceGeo("us").build();
		AnthropicChatOptions copied = original.mutate().build();
		assertThat(copied.getInferenceGeo()).isEqualTo("us");
	}

	@Test
	void testInferenceGeoCombineWith() {
		AnthropicChatOptions base = AnthropicChatOptions.builder().inferenceGeo("us").build();
		AnthropicChatOptions override = AnthropicChatOptions.builder().inferenceGeo("eu").build();

		AnthropicChatOptions merged = base.mutate().combineWith(override.mutate()).build();
		assertThat(merged.getInferenceGeo()).isEqualTo("eu");

		// Null doesn't override
		AnthropicChatOptions noOverride = AnthropicChatOptions.builder().build();
		AnthropicChatOptions merged2 = base.mutate().combineWith(noOverride.mutate()).build();
		assertThat(merged2.getInferenceGeo()).isEqualTo("us");
	}

	@Test
	void testWebSearchToolBuilder() {
		AnthropicWebSearchTool webSearch = AnthropicWebSearchTool.builder()
			.allowedDomains(List.of("docs.spring.io"))
			.blockedDomains(List.of("example.com"))
			.maxUses(5)
			.userLocation("San Francisco", "US", "California", "America/Los_Angeles")
			.build();

		AnthropicChatOptions options = AnthropicChatOptions.builder().webSearchTool(webSearch).build();

		assertThat(options.getWebSearchTool()).isNotNull();
		assertThat(options.getWebSearchTool().getAllowedDomains()).containsExactly("docs.spring.io");
		assertThat(options.getWebSearchTool().getBlockedDomains()).containsExactly("example.com");
		assertThat(options.getWebSearchTool().getMaxUses()).isEqualTo(5);
		assertThat(options.getWebSearchTool().getUserLocation()).isNotNull();
		assertThat(options.getWebSearchTool().getUserLocation().city()).isEqualTo("San Francisco");
		assertThat(options.getWebSearchTool().getUserLocation().country()).isEqualTo("US");
	}

	@Test
	void testWebSearchToolPreservedInMutate() {
		AnthropicWebSearchTool webSearch = AnthropicWebSearchTool.builder().maxUses(3).build();
		AnthropicChatOptions original = AnthropicChatOptions.builder().webSearchTool(webSearch).build();
		AnthropicChatOptions copied = original.mutate().build();

		assertThat(copied.getWebSearchTool()).isNotNull();
		assertThat(copied.getWebSearchTool().getMaxUses()).isEqualTo(3);
	}

	@Test
	void testWebSearchToolCombineWith() {
		AnthropicWebSearchTool base = AnthropicWebSearchTool.builder().maxUses(3).build();
		AnthropicWebSearchTool override = AnthropicWebSearchTool.builder().maxUses(10).build();

		AnthropicChatOptions baseOpts = AnthropicChatOptions.builder().webSearchTool(base).build();
		AnthropicChatOptions overrideOpts = AnthropicChatOptions.builder().webSearchTool(override).build();

		AnthropicChatOptions merged = baseOpts.mutate().combineWith(overrideOpts.mutate()).build();
		assertThat(merged.getWebSearchTool().getMaxUses()).isEqualTo(10);

		// Null doesn't override
		AnthropicChatOptions noOverride = AnthropicChatOptions.builder().build();
		AnthropicChatOptions merged2 = baseOpts.mutate().combineWith(noOverride.mutate()).build();
		assertThat(merged2.getWebSearchTool().getMaxUses()).isEqualTo(3);
	}

	@Test
	void testServiceTierBuilder() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().serviceTier(AnthropicServiceTier.AUTO).build();
		assertThat(options.getServiceTier()).isEqualTo(AnthropicServiceTier.AUTO);
	}

	@Test
	void testServiceTierPreservedInMutate() {
		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.serviceTier(AnthropicServiceTier.STANDARD_ONLY)
			.build();
		AnthropicChatOptions copied = original.mutate().build();
		assertThat(copied.getServiceTier()).isEqualTo(AnthropicServiceTier.STANDARD_ONLY);
	}

	@Test
	void testServiceTierCombineWith() {
		AnthropicChatOptions base = AnthropicChatOptions.builder()
			.serviceTier(AnthropicServiceTier.STANDARD_ONLY)
			.build();
		AnthropicChatOptions override = AnthropicChatOptions.builder().serviceTier(AnthropicServiceTier.AUTO).build();

		AnthropicChatOptions merged = base.mutate().combineWith(override.mutate()).build();
		assertThat(merged.getServiceTier()).isEqualTo(AnthropicServiceTier.AUTO);

		// Null doesn't override
		AnthropicChatOptions noOverride = AnthropicChatOptions.builder().build();
		AnthropicChatOptions merged2 = base.mutate().combineWith(noOverride.mutate()).build();
		assertThat(merged2.getServiceTier()).isEqualTo(AnthropicServiceTier.STANDARD_ONLY);
	}

	@Test
	void testThinkingEnabledWithDisplay() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.thinkingEnabled(2048, ThinkingConfigEnabled.Display.SUMMARIZED)
			.maxTokens(16384)
			.build();

		assertThat(options.getThinking()).isNotNull();
		ThinkingConfigParam thinking = options.getThinking();
		ThinkingConfigEnabled enabled = thinking.enabled().get();
		assertThat(enabled.budgetTokens()).isEqualTo(2048);
		assertThat(enabled.display()).isPresent();
		assertThat(enabled.display().get()).isEqualTo(ThinkingConfigEnabled.Display.SUMMARIZED);
	}

	@Test
	void testThinkingEnabledWithOmittedDisplay() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.thinkingEnabled(4096, ThinkingConfigEnabled.Display.OMITTED)
			.maxTokens(16384)
			.build();

		ThinkingConfigEnabled enabled = options.getThinking().enabled().get();
		assertThat(enabled.display()).isPresent();
		assertThat(enabled.display().get()).isEqualTo(ThinkingConfigEnabled.Display.OMITTED);
	}

	@Test
	void testThinkingEnabledWithoutDisplayHasNoDisplay() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().thinkingEnabled(2048).maxTokens(16384).build();

		ThinkingConfigEnabled enabled = options.getThinking().enabled().get();
		assertThat(enabled.display()).isEmpty();
	}

	@Test
	void testThinkingAdaptiveWithDisplay() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.thinkingAdaptive(ThinkingConfigAdaptive.Display.SUMMARIZED)
			.maxTokens(16384)
			.build();

		assertThat(options.getThinking()).isNotNull();
		ThinkingConfigAdaptive adaptive = options.getThinking().adaptive().get();
		assertThat(adaptive.display()).isPresent();
		assertThat(adaptive.display().get()).isEqualTo(ThinkingConfigAdaptive.Display.SUMMARIZED);
	}

	@Test
	void testThinkingAdaptiveWithOmittedDisplay() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.thinkingAdaptive(ThinkingConfigAdaptive.Display.OMITTED)
			.maxTokens(16384)
			.build();

		ThinkingConfigAdaptive adaptive = options.getThinking().adaptive().get();
		assertThat(adaptive.display()).isPresent();
		assertThat(adaptive.display().get()).isEqualTo(ThinkingConfigAdaptive.Display.OMITTED);
	}

	@Test
	void testThinkingAdaptiveWithoutDisplayHasNoDisplay() {
		AnthropicChatOptions options = AnthropicChatOptions.builder().thinkingAdaptive().maxTokens(16384).build();

		ThinkingConfigAdaptive adaptive = options.getThinking().adaptive().get();
		assertThat(adaptive.display()).isEmpty();
	}

	@Test
	void testThinkingDisplayPreservedInMutate() {
		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.thinkingEnabled(2048, ThinkingConfigEnabled.Display.SUMMARIZED)
			.maxTokens(16384)
			.build();

		AnthropicChatOptions copied = original.mutate().build();

		ThinkingConfigEnabled enabled = copied.getThinking().enabled().get();
		assertThat(enabled.budgetTokens()).isEqualTo(2048);
		assertThat(enabled.display()).isPresent();
		assertThat(enabled.display().get()).isEqualTo(ThinkingConfigEnabled.Display.SUMMARIZED);
	}

	@Test
	void testThinkingDisplayPreservedInCombineWith() {
		AnthropicChatOptions base = AnthropicChatOptions.builder().model("base-model").maxTokens(16384).build();

		AnthropicChatOptions override = AnthropicChatOptions.builder()
			.thinkingAdaptive(ThinkingConfigAdaptive.Display.OMITTED)
			.build();

		AnthropicChatOptions merged = base.mutate().combineWith(override.mutate()).build();

		assertThat(merged.getModel()).isEqualTo("base-model");
		ThinkingConfigAdaptive adaptive = merged.getThinking().adaptive().get();
		assertThat(adaptive.display()).isPresent();
		assertThat(adaptive.display().get()).isEqualTo(ThinkingConfigAdaptive.Display.OMITTED);
	}

}
