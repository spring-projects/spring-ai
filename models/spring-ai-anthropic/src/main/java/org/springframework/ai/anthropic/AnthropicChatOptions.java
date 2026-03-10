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
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
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

	/**
	 * Speed mode for inference. Set to "fast" for faster output (up to 2.5x).
	 */
	private @JsonProperty("speed") @Nullable String speed;

	/**
	 * Additional parameters to pass to the Anthropic API. Accepts any key-value pairs
	 * that will be included at the top level of the JSON request.
	 */
	private @JsonProperty("extra_body") @Nullable Map<String, Object> extraBody;

	// @formatter:on

	public static AnthropicChatOptions.Builder<?> builder() {
		return new Builder<>();
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
			.speed(fromOptions.getSpeed())
			.extraBody(fromOptions.getExtraBody())
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

	public @Nullable String getSpeed() {
		return this.speed;
	}

	public void setSpeed(@Nullable String speed) {
		this.speed = speed;
	}

	public @Nullable Map<String, Object> getExtraBody() {
		return this.extraBody;
	}

	public void setExtraBody(@Nullable Map<String, Object> extraBody) {
		this.extraBody = extraBody;
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
	public AnthropicChatOptions.Builder<?> mutate() {
		return builder()
			// ChatOptions
			.model(this.model)
			.frequencyPenalty(this.getFrequencyPenalty())
			.maxTokens(this.maxTokens)
			.presencePenalty(this.getPresencePenalty())
			.stopSequences(this.stopSequences)
			.temperature(this.temperature)
			.topK(this.topK)
			.topP(this.topP)
			// ToolCallingChatOptions
			.toolCallbacks(this.getToolCallbacks())
			.toolNames(this.getToolNames())
			.toolContext(this.getToolContext())
			.internalToolExecutionEnabled(this.getInternalToolExecutionEnabled())
			// StructuredOutputChatOptions
			.outputFormat(this.outputFormat)
			// Anthropic Specific
			.metadata(this.metadata)
			.toolChoice(this.toolChoice)
			.thinking(this.thinking)
			.citationDocuments(this.getCitationDocuments())
			.cacheOptions(this.getCacheOptions())
			.skillContainer(this.getSkillContainer())
			.httpHeaders(this.getHttpHeaders())
			.speed(this.speed)
			.extraBody(this.extraBody);
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
				&& Objects.equals(this.skillContainer, that.skillContainer) && Objects.equals(this.speed, that.speed)
				&& Objects.equals(this.extraBody, that.extraBody);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.maxTokens, this.metadata, this.stopSequences, this.temperature, this.topP,
				this.topK, this.toolChoice, this.thinking, this.toolCallbacks, this.toolNames,
				this.internalToolExecutionEnabled, this.toolContext, this.httpHeaders, this.cacheOptions,
				this.outputFormat, this.citationDocuments, this.skillContainer, this.speed, this.extraBody);
	}

	public static class Builder<B extends Builder<B>> extends DefaultToolCallingChatOptions.Builder<B>
			implements StructuredOutputChatOptions.Builder<B> {

		private ChatCompletionRequest.@Nullable Metadata metadata;

		private AnthropicApi.@Nullable ToolChoice toolChoice;

		private ChatCompletionRequest.@Nullable ThinkingConfig thinking;

		private List<CitationDocument> citationDocuments = new ArrayList<>();

		private AnthropicCacheOptions cacheOptions = AnthropicCacheOptions.DISABLED;

		private AnthropicApi.@Nullable SkillContainer skillContainer;

		private Map<String, String> httpHeaders = new HashMap<>();

		private @Nullable OutputFormat outputFormat;

		private @Nullable String speed;

		private @Nullable Map<String, Object> extraBody;

		@Override
		public B outputSchema(@Nullable String outputSchema) {
			if (outputSchema != null) {
				this.outputFormat = new OutputFormat(outputSchema);
			}
			else {
				this.outputFormat = null;
			}
			return self();
		}

		public B model(AnthropicApi.@Nullable ChatModel model) {
			if (model != null) {
				this.model(model.getName());
			}
			else {
				this.model((String) null);
			}
			return self();
		}

		public B metadata(ChatCompletionRequest.@Nullable Metadata metadata) {
			this.metadata = metadata;
			return self();
		}

		public B toolChoice(AnthropicApi.@Nullable ToolChoice toolChoice) {
			this.toolChoice = toolChoice;
			return self();
		}

		public B thinking(ChatCompletionRequest.@Nullable ThinkingConfig thinking) {
			this.thinking = thinking;
			return self();
		}

		public B thinking(AnthropicApi.ThinkingType type, Integer budgetTokens) {
			this.thinking = new ChatCompletionRequest.ThinkingConfig(type, budgetTokens);
			return self();
		}

		public B httpHeaders(Map<String, String> httpHeaders) {
			this.httpHeaders = httpHeaders;
			return self();
		}

		public B cacheOptions(AnthropicCacheOptions cacheOptions) {
			this.cacheOptions = cacheOptions;
			return self();
		}

		/**
		 * Set citation documents for the request.
		 * @param citationDocuments List of documents to include for citations
		 * @return Builder for method chaining
		 */
		public B citationDocuments(List<CitationDocument> citationDocuments) {
			Assert.notNull(citationDocuments, "Citation documents cannot be null");
			this.citationDocuments = citationDocuments;
			return self();
		}

		/**
		 * Set citation documents from variable arguments.
		 * @param documents Variable number of CitationDocument objects
		 * @return Builder for method chaining
		 */
		public B citationDocuments(CitationDocument... documents) {
			Assert.notNull(documents, "Citation documents cannot be null");
			this.citationDocuments.addAll(Arrays.asList(documents));
			return self();
		}

		/**
		 * Add a single citation document.
		 * @param document Citation document to add
		 * @return Builder for method chaining
		 */
		public B addCitationDocument(CitationDocument document) {
			Assert.notNull(document, "Citation document cannot be null");
			this.citationDocuments.add(document);
			return self();
		}

		public B outputFormat(@Nullable OutputFormat outputFormat) {
			this.outputFormat = outputFormat;
			return self();
		}

		public B speed(@Nullable String speed) {
			this.speed = speed;
			return self();
		}

		public B extraBody(@Nullable Map<String, Object> extraBody) {
			this.extraBody = extraBody;
			return self();
		}

		/**
		 * Set the Skills container for this request.
		 * @param skillContainer Container with skills to make available
		 * @return Builder for method chaining
		 */
		public B skillContainer(AnthropicApi.@Nullable SkillContainer skillContainer) {
			this.skillContainer = skillContainer;
			return self();
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
		public B skill(String skillIdOrName) {
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
		public B skill(String skillIdOrName, String version) {
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
		public B skill(AnthropicApi.AnthropicSkill anthropicSkill) {
			Assert.notNull(anthropicSkill, "AnthropicSkill cannot be null");
			return this.skill(anthropicSkill.toSkill());
		}

		/**
		 * Add a pre-built Anthropic skill with specific version.
		 * @param anthropicSkill Pre-built Anthropic skill to add
		 * @param version Version of the skill (e.g., "latest", "20251013")
		 * @return Builder for method chaining
		 */
		public B skill(AnthropicApi.AnthropicSkill anthropicSkill, String version) {
			Assert.notNull(anthropicSkill, "AnthropicSkill cannot be null");
			Assert.hasText(version, "Version cannot be empty");
			return this.skill(anthropicSkill.toSkill(version));
		}

		/**
		 * Add a Skill record directly.
		 * @param skill Skill to add
		 * @return Builder for method chaining
		 */
		public B skill(AnthropicApi.Skill skill) {
			Assert.notNull(skill, "Skill cannot be null");
			if (this.skillContainer == null) {
				this.skillContainer = AnthropicApi.SkillContainer.builder().skill(skill).build();
			}
			else {
				List<AnthropicApi.Skill> existingSkills = new ArrayList<>(this.skillContainer.skills());
				existingSkills.add(skill);
				this.skillContainer = new AnthropicApi.SkillContainer(existingSkills);
			}
			return self();
		}

		/**
		 * Add multiple skills by their IDs or names.
		 * @param skillIds The skill IDs or names
		 * @return Builder for method chaining
		 */
		public B skills(String... skillIds) {
			Assert.notEmpty(skillIds, "Skill IDs cannot be empty");
			for (String skillId : skillIds) {
				this.skill(skillId);
			}
			return self();
		}

		/**
		 * Add multiple skills from a list of IDs or names.
		 * @param skillIds The list of skill IDs or names
		 * @return Builder for method chaining
		 */
		public B skills(List<String> skillIds) {
			Assert.notEmpty(skillIds, "Skill IDs cannot be empty");
			skillIds.forEach(this::skill);
			return self();
		}

		/**
		 * Add an Anthropic pre-built skill (xlsx, pptx, docx, pdf).
		 * @param anthropicSkill Pre-built Anthropic skill to add
		 * @return Builder for method chaining
		 * @deprecated Use {@link #skill(AnthropicApi.AnthropicSkill)} instead
		 */
		@Deprecated
		public B anthropicSkill(AnthropicApi.AnthropicSkill anthropicSkill) {
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
		public B anthropicSkill(AnthropicApi.AnthropicSkill anthropicSkill, String version) {
			return this.skill(anthropicSkill, version);
		}

		/**
		 * Add a custom skill by ID.
		 * @param skillId Custom skill ID
		 * @return Builder for method chaining
		 * @deprecated Use {@link #skill(String)} instead
		 */
		@Deprecated
		public B customSkill(String skillId) {
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
		public B customSkill(String skillId, String version) {
			return this.skill(skillId, version);
		}

		@Override
		public B combineWith(ChatOptions.Builder<?> other) {
			super.combineWith(other);
			if (other instanceof Builder<?> options) {
				if (options.metadata != null) {
					this.metadata = options.metadata;
				}
				if (options.toolChoice != null) {
					this.toolChoice = options.toolChoice;
				}
				if (options.thinking != null) {
					this.thinking = options.thinking;
				}
				if (!options.citationDocuments.isEmpty()) {
					this.citationDocuments = options.citationDocuments;
				}
				if (options.cacheOptions != AnthropicCacheOptions.DISABLED) {
					this.cacheOptions = options.cacheOptions;
				}
				if (options.skillContainer != null) {
					this.skillContainer = options.skillContainer;
				}
				if (!options.httpHeaders.isEmpty()) {
					this.httpHeaders = options.httpHeaders;
				}
				if (options.outputFormat != null) {
					this.outputFormat = options.outputFormat;
				}
				if (options.speed != null) {
					this.speed = options.speed;
				}
				if (options.extraBody != null) {
					this.extraBody = options.extraBody;
				}
			}
			return self();
		}

		@SuppressWarnings("NullAway")
		public AnthropicChatOptions build() {
			// TODO: add assertions, remove SuppressWarnings
			// Assert.state(this.model != null, "model must be set");
			// Assert.state(this.maxTokens != null, "maxTokens must be set");
			AnthropicChatOptions options = new AnthropicChatOptions();
			options.model = this.model;
			options.maxTokens = this.maxTokens;
			options.metadata = this.metadata;
			options.stopSequences = this.stopSequences;
			options.temperature = this.temperature;
			options.topP = this.topP;
			options.topK = this.topK;
			options.toolChoice = this.toolChoice;
			options.thinking = this.thinking;
			options.citationDocuments = this.citationDocuments;
			options.cacheOptions = this.cacheOptions;
			options.skillContainer = this.skillContainer;
			options.toolCallbacks = this.toolCallbacks;
			options.toolNames = this.toolNames;
			options.internalToolExecutionEnabled = this.internalToolExecutionEnabled;
			options.toolContext = this.toolContext;
			options.httpHeaders = this.httpHeaders;
			options.outputFormat = this.outputFormat;
			options.speed = this.speed;
			options.extraBody = this.extraBody;
			options.validateCitationConsistency();
			return options;
		}

	}

}
