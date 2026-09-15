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

package org.springframework.ai.model.openai.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.openai.OpenAiChatModel.ResponseFormat;
import org.springframework.ai.openai.responses.HostedTool;
import org.springframework.ai.openai.responses.OpenAiResponsesChatOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the OpenAI Responses API chat model, selected with
 * {@code spring.ai.openai.chat.api=responses}.
 * <p>
 * Connection settings are shared with the Chat Completions model under
 * {@code spring.ai.openai}, and can be overridden here.
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 */
@ConfigurationProperties(OpenAiResponsesChatProperties.CONFIG_PREFIX)
public class OpenAiResponsesChatProperties extends AbstractOpenAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.responses";

	private @Nullable Integer maxOutputTokens;

	private @Nullable Double temperature;

	private @Nullable Double topP;

	private @Nullable Integer topLogprobs;

	private @Nullable String reasoningEffort;

	private @Nullable String reasoningSummary;

	private @Nullable String verbosity;

	private @Nullable ResponseFormat responseFormat;

	private @Nullable Boolean strict;

	private @Nullable Object toolChoice;

	private @Nullable Boolean parallelToolCalls;

	private @Nullable Integer maxToolCalls;

	private @Nullable List<String> include;

	private @Nullable String truncation;

	private @Nullable String serviceTier;

	private @Nullable String promptCacheKey;

	private @Nullable String safetyIdentifier;

	private @Nullable Map<String, String> metadata;

	private @Nullable Map<String, Object> extraBody;

	private HostedTools hostedTools = new HostedTools();

	public @Nullable Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	public void setMaxOutputTokens(@Nullable Integer maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	public @Nullable Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(@Nullable Double temperature) {
		this.temperature = temperature;
	}

	public @Nullable Double getTopP() {
		return this.topP;
	}

	public void setTopP(@Nullable Double topP) {
		this.topP = topP;
	}

	public @Nullable Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	public void setTopLogprobs(@Nullable Integer topLogprobs) {
		this.topLogprobs = topLogprobs;
	}

	public @Nullable String getReasoningEffort() {
		return this.reasoningEffort;
	}

	public void setReasoningEffort(@Nullable String reasoningEffort) {
		this.reasoningEffort = reasoningEffort;
	}

	public @Nullable String getReasoningSummary() {
		return this.reasoningSummary;
	}

	public void setReasoningSummary(@Nullable String reasoningSummary) {
		this.reasoningSummary = reasoningSummary;
	}

	public @Nullable String getVerbosity() {
		return this.verbosity;
	}

	public void setVerbosity(@Nullable String verbosity) {
		this.verbosity = verbosity;
	}

	public @Nullable ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(@Nullable ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public @Nullable Boolean getStrict() {
		return this.strict;
	}

	public void setStrict(@Nullable Boolean strict) {
		this.strict = strict;
	}

	public @Nullable Object getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(@Nullable Object toolChoice) {
		this.toolChoice = toolChoice;
	}

	public @Nullable Boolean getParallelToolCalls() {
		return this.parallelToolCalls;
	}

	public void setParallelToolCalls(@Nullable Boolean parallelToolCalls) {
		this.parallelToolCalls = parallelToolCalls;
	}

	public @Nullable Integer getMaxToolCalls() {
		return this.maxToolCalls;
	}

	public void setMaxToolCalls(@Nullable Integer maxToolCalls) {
		this.maxToolCalls = maxToolCalls;
	}

	public @Nullable List<String> getInclude() {
		return this.include;
	}

	public void setInclude(@Nullable List<String> include) {
		this.include = include;
	}

	public @Nullable String getTruncation() {
		return this.truncation;
	}

	public void setTruncation(@Nullable String truncation) {
		this.truncation = truncation;
	}

	public @Nullable String getServiceTier() {
		return this.serviceTier;
	}

	public void setServiceTier(@Nullable String serviceTier) {
		this.serviceTier = serviceTier;
	}

	public @Nullable String getPromptCacheKey() {
		return this.promptCacheKey;
	}

	public void setPromptCacheKey(@Nullable String promptCacheKey) {
		this.promptCacheKey = promptCacheKey;
	}

	public @Nullable String getSafetyIdentifier() {
		return this.safetyIdentifier;
	}

	public void setSafetyIdentifier(@Nullable String safetyIdentifier) {
		this.safetyIdentifier = safetyIdentifier;
	}

	public @Nullable Map<String, String> getMetadata() {
		return this.metadata;
	}

	public void setMetadata(@Nullable Map<String, String> metadata) {
		this.metadata = metadata;
	}

	public @Nullable Map<String, Object> getExtraBody() {
		return this.extraBody;
	}

	public void setExtraBody(@Nullable Map<String, Object> extraBody) {
		this.extraBody = extraBody;
	}

	public HostedTools getHostedTools() {
		return this.hostedTools;
	}

	public void setHostedTools(HostedTools hostedTools) {
		this.hostedTools = hostedTools;
	}

	public OpenAiResponsesChatOptions toOptions() {
		return OpenAiResponsesChatOptions.builder()
			.timeout(this.getTimeout())
			.model(this.getModel())
			.maxOutputTokens(this.maxOutputTokens)
			.temperature(this.temperature)
			.topP(this.topP)
			.topLogprobs(this.topLogprobs)
			.reasoningEffort(this.reasoningEffort)
			.reasoningSummary(this.reasoningSummary)
			.verbosity(this.verbosity)
			.responseFormat(this.responseFormat)
			.strict(this.strict)
			.toolChoice(this.toolChoice)
			.parallelToolCalls(this.parallelToolCalls)
			.maxToolCalls(this.maxToolCalls)
			.hostedTools(this.hostedTools.toHostedTools())
			.include(this.include)
			.truncation(this.truncation)
			.serviceTier(this.serviceTier)
			.promptCacheKey(this.promptCacheKey)
			.safetyIdentifier(this.safetyIdentifier)
			.metadata(this.metadata)
			.extraBody(this.extraBody)
			.build();
	}

	/**
	 * The subset of server-executed tools that is configurable declaratively. Anything
	 * richer is configured with the builder, since a hosted tool can carry structure that
	 * does not map onto flat properties.
	 */
	public static class HostedTools {

		private WebSearch webSearch = new WebSearch();

		private FileSearch fileSearch = new FileSearch();

		private CodeInterpreter codeInterpreter = new CodeInterpreter();

		private ImageGeneration imageGeneration = new ImageGeneration();

		public WebSearch getWebSearch() {
			return this.webSearch;
		}

		public void setWebSearch(WebSearch webSearch) {
			this.webSearch = webSearch;
		}

		public FileSearch getFileSearch() {
			return this.fileSearch;
		}

		public void setFileSearch(FileSearch fileSearch) {
			this.fileSearch = fileSearch;
		}

		public CodeInterpreter getCodeInterpreter() {
			return this.codeInterpreter;
		}

		public void setCodeInterpreter(CodeInterpreter codeInterpreter) {
			this.codeInterpreter = codeInterpreter;
		}

		public ImageGeneration getImageGeneration() {
			return this.imageGeneration;
		}

		public void setImageGeneration(ImageGeneration imageGeneration) {
			this.imageGeneration = imageGeneration;
		}

		@Nullable List<HostedTool> toHostedTools() {
			List<HostedTool> tools = new ArrayList<>();
			if (this.webSearch.isEnabled()) {
				tools.add(new HostedTool.WebSearch(this.webSearch.getSearchContextSize(),
						this.webSearch.getAllowedDomains()));
			}
			if (this.fileSearch.getVectorStoreIds() != null && !this.fileSearch.getVectorStoreIds().isEmpty()) {
				tools.add(new HostedTool.FileSearch(this.fileSearch.getVectorStoreIds(),
						this.fileSearch.getMaxNumResults()));
			}
			if (this.codeInterpreter.isEnabled()) {
				tools.add(new HostedTool.CodeInterpreter(this.codeInterpreter.getContainerId()));
			}
			if (this.imageGeneration.isEnabled()) {
				tools.add(new HostedTool.ImageGeneration(this.imageGeneration.getModel(),
						this.imageGeneration.getSize()));
			}
			// Null rather than empty, so it reads as "not configured" when merged
			return tools.isEmpty() ? null : tools;
		}

		public static class WebSearch {

			private boolean enabled;

			private @Nullable String searchContextSize;

			private @Nullable List<String> allowedDomains;

			public boolean isEnabled() {
				return this.enabled;
			}

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}

			public @Nullable String getSearchContextSize() {
				return this.searchContextSize;
			}

			public void setSearchContextSize(@Nullable String searchContextSize) {
				this.searchContextSize = searchContextSize;
			}

			public @Nullable List<String> getAllowedDomains() {
				return this.allowedDomains;
			}

			public void setAllowedDomains(@Nullable List<String> allowedDomains) {
				this.allowedDomains = allowedDomains;
			}

		}

		public static class FileSearch {

			private @Nullable List<String> vectorStoreIds;

			private @Nullable Integer maxNumResults;

			public @Nullable List<String> getVectorStoreIds() {
				return this.vectorStoreIds;
			}

			public void setVectorStoreIds(@Nullable List<String> vectorStoreIds) {
				this.vectorStoreIds = vectorStoreIds;
			}

			public @Nullable Integer getMaxNumResults() {
				return this.maxNumResults;
			}

			public void setMaxNumResults(@Nullable Integer maxNumResults) {
				this.maxNumResults = maxNumResults;
			}

		}

		public static class CodeInterpreter {

			private boolean enabled;

			private @Nullable String containerId;

			public boolean isEnabled() {
				return this.enabled;
			}

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}

			public @Nullable String getContainerId() {
				return this.containerId;
			}

			public void setContainerId(@Nullable String containerId) {
				this.containerId = containerId;
			}

		}

		public static class ImageGeneration {

			private boolean enabled;

			private @Nullable String model;

			private @Nullable String size;

			public boolean isEnabled() {
				return this.enabled;
			}

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}

			public @Nullable String getModel() {
				return this.model;
			}

			public void setModel(@Nullable String model) {
				this.model = model;
			}

			public @Nullable String getSize() {
				return this.size;
			}

			public void setSize(@Nullable String size) {
				this.size = size;
			}

		}

	}

}
