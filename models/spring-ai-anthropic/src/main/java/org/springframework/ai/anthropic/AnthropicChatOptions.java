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

package org.springframework.ai.anthropic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest.OutputFormat;
import org.springframework.ai.anthropic.api.AnthropicCacheOptions;
import org.springframework.ai.anthropic.api.CitationDocument;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.Assert;

/**
 * The options to be used when sending a chat request to the Anthropic API.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @author Ilayaperumal Gopinathan
 * @author Soby Chacko
 * @author Austin Dase
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class AnthropicChatOptions implements ToolCallingChatOptions, StructuredOutputChatOptions {

	// @formatter:off
	@SuppressWarnings("NullAway.Init")
	private @JsonProperty("model") String model;
	@SuppressWarnings("NullAway.Init")
	private @JsonProperty("max_tokens") Integer maxTokens;
	private @JsonProperty("metadata") ChatCompletionRequest.@Nullable Metadata metadata;
	private @JsonProperty("stop_sequences") @Nullable List<String> stopSequences;
	private @JsonProperty("temperature") @Nullable Double temperature;
	private @JsonProperty("top_p") @Nullable Double topP;
	private @JsonProperty("top_k") @Nullable Integer topK;
	private @JsonProperty("tool_choice") AnthropicApi.@Nullable ToolChoice toolChoice;
	private @JsonProperty("thinking") ChatCompletionRequest.@Nullable ThinkingConfig thinking;

	/**
	 * Documents to be used for citation-based responses. These documents will be
	 * converted to ContentBlocks and included in the first user message of the request.
	 * Citations indicating which parts of these documents were used in the response will
	 * be returned in the response metadata under the "citations" key.
	 * @see CitationDocument
	 * @see Citation
	 */
	@JsonIgnore
	private List<CitationDocument> citationDocuments = new ArrayList<>();

	@JsonIgnore
	private AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.DISABLED;

	public AnthropicCacheOptions getCacheOptions() {
		return this.cacheOptions;
	}

	public void setCacheOptions(AnthropicCacheOptions cacheOptions) {
		this.cacheOptions = cacheOptions;
	}

	/**
	 * Container for Claude Skills to make available in this request.
	 * Skills are collections of instructions, scripts, and resources that
	 * extend Claude's capabilities for specific domains.
	 * Maximum of 8 skills per request.
	 */
	@JsonIgnore
	private AnthropicApi.@Nullable SkillContainer skillContainer;

	public AnthropicApi.@Nullable SkillContainer getSkillContainer() {
		return this.skillContainer;
	}

	public void setSkillContainer(AnthropicApi.@Nullable SkillContainer skillContainer) {
		this.skillContainer = skillContainer;
	}

	/**
	 * Collection of {@link ToolCallback}s to be used for tool calling in the chat
	 * completion requests.
	 */
	@JsonIgnore
	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	/**
	 * Collection of tool names to be resolved at runtime and used for tool calling in the
	 * chat completion requests.
	 */
	@JsonIgnore
	private Set<String> toolNames = new HashSet<>();

	/**
	 * Whether to enable the tool execution lifecycle internally in ChatModel.
	 */
	@JsonIgnore
	private @Nullable Boolean internalToolExecutionEnabled;

	@JsonIgnore
	private Map<String, Object> toolContext = new HashMap<>();


	/**
	 * Optional HTTP headers to be added to the chat completion request.
	 */
	@JsonIgnore
	private Map<String, String> httpHeaders = new HashMap<>();

	/**
	 * The desired response format for structured output.
	 */
	private @JsonProperty("output_format") @Nullable OutputFormat outputFormat;

	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static AnthropicChatOptions fromOptions(AnthropicChatOptions fromOptions) {
		return builder().model(fromOptions.getModel())
			.maxTokens(fromOptions.getMaxTokens())
			.metadata(fromOptions.getMetadata())
			.stopSequences(
					fromOptions.getStopSequences() != null ? new ArrayList<>(fromOptions.getStopSequences()) : null)
			.temperature(fromOptions.getTemperature())
			.topP(fromOptions.getTopP())
			.topK(fromOptions.getTopK())
			.toolChoice(fromOptions.getToolChoice())
			.thinking(fromOptions.getThinking())
			.toolCallbacks(new ArrayList<>(fromOptions.getToolCallbacks()))
			.toolNames(new HashSet<>(fromOptions.getToolNames()))
			.internalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled())
			.toolContext(new HashMap<>(fromOptions.getToolContext()))
			.httpHeaders(new HashMap<>(fromOptions.getHttpHeaders()))
			.cacheOptions(fromOptions.getCacheOptions())
			.citationDocuments(new ArrayList<>(fromOptions.getCitationDocuments()))
			.outputFormat(fromOptions.getOutputFormat())
			.skillContainer(fromOptions.getSkillContainer())
			.build();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public ChatCompletionRequest.@Nullable Metadata getMetadata() {
		return this.metadata;
	}

	public void setMetadata(ChatCompletionRequest.@Nullable Metadata metadata) {
		this.metadata = metadata;
	}

	@Override
	public @Nullable List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(@Nullable List<String> stopSequences) {
		this.stopSequences = stopSequences;
	}

	@Override
	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	@Override
	public @Nullable Integer getTopK() {
		return this.topK;
	}

	public void setTopK(@Nullable Integer topK) {
		this.topK = topK;
	}

	public AnthropicApi.@Nullable ToolChoice getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(AnthropicApi.@Nullable ToolChoice toolChoice) {
		this.toolChoice = toolChoice;
	}

	public ChatCompletionRequest.@Nullable ThinkingConfig getThinking() {
		return this.thinking;
	}

	public void setThinking(ChatCompletionRequest.@Nullable ThinkingConfig thinking) {
		this.thinking = thinking;
	}

	@Override
	@JsonIgnore
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	@JsonIgnore
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	@JsonIgnore
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	@JsonIgnore
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
	}

	@Override
	@JsonIgnore
	public @Nullable Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	@Override
	@JsonIgnore
	public @Nullable Double getFrequencyPenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public @Nullable Double getPresencePenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	@JsonIgnore
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	@JsonIgnore
	public Map<String, String> getHttpHeaders() {
		return this.httpHeaders;
	}

	public void setHttpHeaders(Map<String, String> httpHeaders) {
		this.httpHeaders = httpHeaders;
	}

	public List<CitationDocument> getCitationDocuments() {
		return this.citationDocuments;
	}

	public void setCitationDocuments(List<CitationDocument> citationDocuments) {
		Assert.notNull(citationDocuments, "Citation documents cannot be null");
		this.citationDocuments = citationDocuments;
	}

	/**
	 * Validate that all citation documents have consistent citation settings. Anthropic
	 * requires all documents to have citations enabled if any do.
	 */
	public void validateCitationConsistency() {
		if (this.citationDocuments.isEmpty()) {
			return;
		}

		boolean hasEnabledCitations = this.citationDocuments.stream().anyMatch(CitationDocument::isCitationsEnabled);
		boolean hasDisabledCitations = this.citationDocuments.stream().anyMatch(doc -> !doc.isCitationsEnabled());

		if (hasEnabledCitations && hasDisabledCitations) {
			throw new IllegalArgumentException(
					"Anthropic Citations API requires all documents to have consistent citation settings. "
							+ "Either enable citations for all documents or disable for all documents.");
		}
	}

	public @Nullable OutputFormat getOutputFormat() {
		return this.outputFormat;
	}

	public void setOutputFormat(OutputFormat outputFormat) {
		Assert.notNull(outputFormat, "outputFormat cannot be null");
		this.outputFormat = outputFormat;
	}

	@Override
	@JsonIgnore
	public @Nullable String getOutputSchema() {
		return this.getOutputFormat() != null ? ModelOptionsUtils.toJsonString(this.getOutputFormat().schema()) : null;
	}

	@Override
	@JsonIgnore
	public void setOutputSchema(String outputSchema) {
		this.setOutputFormat(new OutputFormat(outputSchema));
	}

	@Override
	@SuppressWarnings("unchecked")
	public AnthropicChatOptions copy() {
		return fromOptions(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof AnthropicChatOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.metadata, that.metadata)
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.toolChoice, that.toolChoice)
				&& Objects.equals(this.thinking, that.thinking)
				&& Objects.equals(this.toolCallbacks, that.toolCallbacks)
				&& Objects.equals(this.toolNames, that.toolNames)
				&& Objects.equals(this.internalToolExecutionEnabled, that.internalToolExecutionEnabled)
				&& Objects.equals(this.toolContext, that.toolContext)
				&& Objects.equals(this.httpHeaders, that.httpHeaders)
				&& Objects.equals(this.cacheOptions, that.cacheOptions)
				&& Objects.equals(this.outputFormat, that.outputFormat)
				&& Objects.equals(this.citationDocuments, that.citationDocuments)
				&& Objects.equals(this.skillContainer, that.skillContainer);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.maxTokens, this.metadata, this.stopSequences, this.temperature, this.topP,
				this.topK, this.toolChoice, this.thinking, this.toolCallbacks, this.toolNames,
				this.internalToolExecutionEnabled, this.toolContext, this.httpHeaders, this.cacheOptions,
				this.outputFormat, this.citationDocuments, this.skillContainer);
	}

	public static final class Builder {

		private final AnthropicChatOptions options = new AnthropicChatOptions();

		public Builder model(String model) {
			this.options.model = model;
			return this;
		}

		public Builder model(AnthropicApi.ChatModel model) {
			this.options.model = model.getValue();
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.options.maxTokens = maxTokens;
			return this;
		}

		public Builder metadata(ChatCompletionRequest.@Nullable Metadata metadata) {
			this.options.metadata = metadata;
			return this;
		}

		public Builder stopSequences(@Nullable List<String> stopSequences) {
			this.options.stopSequences = stopSequences;
			return this;
		}

		public Builder temperature(@Nullable Double temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder topP(@Nullable Double topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder topK(@Nullable Integer topK) {
			this.options.topK = topK;
			return this;
		}

		public Builder toolChoice(AnthropicApi.@Nullable ToolChoice toolChoice) {
			this.options.toolChoice = toolChoice;
			return this;
		}

		public Builder thinking(ChatCompletionRequest.@Nullable ThinkingConfig thinking) {
			this.options.thinking = thinking;
			return this;
		}

		public Builder thinking(AnthropicApi.ThinkingType type, Integer budgetTokens) {
			this.options.thinking = new ChatCompletionRequest.ThinkingConfig(type, budgetTokens);
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder toolCallbacks(ToolCallback... toolCallbacks) {
			Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
			this.options.toolCallbacks.addAll(Arrays.asList(toolCallbacks));
			return this;
		}

		public Builder toolNames(Set<String> toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.setToolNames(toolNames);
			return this;
		}

		public Builder toolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.toolNames.addAll(Set.of(toolNames));
			return this;
		}

		public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
			return this;
		}

		public Builder toolContext(Map<String, Object> toolContext) {
			if (this.options.toolContext == null) {
				this.options.toolContext = toolContext;
			}
			else {
				this.options.toolContext.putAll(toolContext);
			}
			return this;
		}

		public Builder httpHeaders(Map<String, String> httpHeaders) {
			this.options.setHttpHeaders(httpHeaders);
			return this;
		}

		public Builder cacheOptions(AnthropicCacheOptions cacheOptions) {
			this.options.setCacheOptions(cacheOptions);
			return this;
		}

		/**
		 * Set citation documents for the request.
		 * @param citationDocuments List of documents to include for citations
		 * @return Builder for method chaining
		 */
		public Builder citationDocuments(List<CitationDocument> citationDocuments) {
			this.options.setCitationDocuments(citationDocuments);
			return this;
		}

		/**
		 * Set citation documents from variable arguments.
		 * @param documents Variable number of CitationDocument objects
		 * @return Builder for method chaining
		 */
		public Builder citationDocuments(CitationDocument... documents) {
			Assert.notNull(documents, "Citation documents cannot be null");
			this.options.citationDocuments.addAll(Arrays.asList(documents));
			return this;
		}

		/**
		 * Add a single citation document.
		 * @param document Citation document to add
		 * @return Builder for method chaining
		 */
		public Builder addCitationDocument(CitationDocument document) {
			Assert.notNull(document, "Citation document cannot be null");
			this.options.citationDocuments.add(document);
			return this;
		}

		public Builder outputFormat(@Nullable OutputFormat outputFormat) {
			this.options.outputFormat = outputFormat;
			return this;
		}

		public Builder outputSchema(String outputSchema) {
			this.options.setOutputSchema(outputSchema);
			return this;
		}

		/**
		 * Set the Skills container for this request.
		 * @param skillContainer Container with skills to make available
		 * @return Builder for method chaining
		 */
		public Builder skillContainer(AnthropicApi.@Nullable SkillContainer skillContainer) {
			this.options.setSkillContainer(skillContainer);
			return this;
		}

		/**
		 * Add a skill by its ID or name. Automatically detects whether it's a pre-built
		 * Anthropic skill (xlsx, pptx, docx, pdf) or a custom skill ID.
		 *
		 * <p>
		 * Example: <pre>{@code
		 * AnthropicChatOptions options = AnthropicChatOptions.builder()
		 *     .model("claude-sonnet-4-5")
		 *     .skill("xlsx")                          // Pre-built skill
		 *     .skill("skill_01abc123...")             // Custom skill
		 *     .build();
		 * }</pre>
		 * @param skillIdOrName The skill ID or name
		 * @return Builder for method chaining
		 */
		public Builder skill(String skillIdOrName) {
			Assert.hasText(skillIdOrName, "Skill ID or name cannot be empty");
			AnthropicApi.AnthropicSkill prebuilt = AnthropicApi.AnthropicSkill.fromId(skillIdOrName);
			if (prebuilt != null) {
				return this.skill(prebuilt.toSkill());
			}
			return this.skill(new AnthropicApi.Skill(AnthropicApi.SkillType.CUSTOM, skillIdOrName));
		}

		/**
		 * Add a skill by its ID or name with a specific version.
		 * @param skillIdOrName The skill ID or name
		 * @param version The version (e.g., "latest", "20251013")
		 * @return Builder for method chaining
		 */
		public Builder skill(String skillIdOrName, String version) {
			Assert.hasText(skillIdOrName, "Skill ID or name cannot be empty");
			Assert.hasText(version, "Version cannot be empty");
			AnthropicApi.AnthropicSkill prebuilt = AnthropicApi.AnthropicSkill.fromId(skillIdOrName);
			if (prebuilt != null) {
				return this.skill(prebuilt.toSkill(version));
			}
			return this.skill(new AnthropicApi.Skill(AnthropicApi.SkillType.CUSTOM, skillIdOrName, version));
		}

		/**
		 * Add a pre-built Anthropic skill using the enum.
		 *
		 * <p>
		 * Example: <pre>{@code
		 * AnthropicChatOptions options = AnthropicChatOptions.builder()
		 *     .model("claude-sonnet-4-5")
		 *     .skill(AnthropicSkill.XLSX)
		 *     .skill(AnthropicSkill.PPTX)
		 *     .build();
		 * }</pre>
		 * @param anthropicSkill Pre-built Anthropic skill to add
		 * @return Builder for method chaining
		 */
		public Builder skill(AnthropicApi.AnthropicSkill anthropicSkill) {
			Assert.notNull(anthropicSkill, "AnthropicSkill cannot be null");
			return this.skill(anthropicSkill.toSkill());
		}

		/**
		 * Add a pre-built Anthropic skill with specific version.
		 * @param anthropicSkill Pre-built Anthropic skill to add
		 * @param version Version of the skill (e.g., "latest", "20251013")
		 * @return Builder for method chaining
		 */
		public Builder skill(AnthropicApi.AnthropicSkill anthropicSkill, String version) {
			Assert.notNull(anthropicSkill, "AnthropicSkill cannot be null");
			Assert.hasText(version, "Version cannot be empty");
			return this.skill(anthropicSkill.toSkill(version));
		}

		/**
		 * Add a Skill record directly.
		 * @param skill Skill to add
		 * @return Builder for method chaining
		 */
		public Builder skill(AnthropicApi.Skill skill) {
			Assert.notNull(skill, "Skill cannot be null");
			if (this.options.skillContainer == null) {
				this.options.skillContainer = AnthropicApi.SkillContainer.builder().skill(skill).build();
			}
			else {
				// Rebuild container with additional skill
				List<AnthropicApi.Skill> existingSkills = new ArrayList<>(this.options.skillContainer.skills());
				existingSkills.add(skill);
				this.options.skillContainer = new AnthropicApi.SkillContainer(existingSkills);
			}
			return this;
		}

		/**
		 * Add multiple skills by their IDs or names.
		 * @param skillIds The skill IDs or names
		 * @return Builder for method chaining
		 */
		public Builder skills(String... skillIds) {
			Assert.notEmpty(skillIds, "Skill IDs cannot be empty");
			for (String skillId : skillIds) {
				this.skill(skillId);
			}
			return this;
		}

		/**
		 * Add multiple skills from a list of IDs or names.
		 * @param skillIds The list of skill IDs or names
		 * @return Builder for method chaining
		 */
		public Builder skills(List<String> skillIds) {
			Assert.notEmpty(skillIds, "Skill IDs cannot be empty");
			skillIds.forEach(this::skill);
			return this;
		}

		/**
		 * Add an Anthropic pre-built skill (xlsx, pptx, docx, pdf).
		 * @param anthropicSkill Pre-built Anthropic skill to add
		 * @return Builder for method chaining
		 * @deprecated Use {@link #skill(AnthropicApi.AnthropicSkill)} instead
		 */
		@Deprecated
		public Builder anthropicSkill(AnthropicApi.AnthropicSkill anthropicSkill) {
			return this.skill(anthropicSkill);
		}

		/**
		 * Add an Anthropic pre-built skill with specific version.
		 * @param anthropicSkill Pre-built Anthropic skill to add
		 * @param version Version of the skill (e.g., "latest", "20251013")
		 * @return Builder for method chaining
		 * @deprecated Use {@link #skill(AnthropicApi.AnthropicSkill, String)} instead
		 */
		@Deprecated
		public Builder anthropicSkill(AnthropicApi.AnthropicSkill anthropicSkill, String version) {
			return this.skill(anthropicSkill, version);
		}

		/**
		 * Add a custom skill by ID.
		 * @param skillId Custom skill ID
		 * @return Builder for method chaining
		 * @deprecated Use {@link #skill(String)} instead
		 */
		@Deprecated
		public Builder customSkill(String skillId) {
			return this.skill(skillId);
		}

		/**
		 * Add a custom skill with specific version.
		 * @param skillId Custom skill ID
		 * @param version Version of the skill
		 * @return Builder for method chaining
		 * @deprecated Use {@link #skill(String, String)} instead
		 */
		@Deprecated
		public Builder customSkill(String skillId, String version) {
			return this.skill(skillId, version);
		}

		public AnthropicChatOptions build() {
			this.options.validateCitationConsistency();
			return this.options;
		}

	}

}
