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

package org.springframework.ai.openaiofficial;

import com.openai.models.FunctionDefinition;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletionAudioParam;
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption;
import com.openai.models.responses.ResponseCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.openai.models.ChatModel.GPT_5_MINI;

/**
 * Configuration information for the Chat Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialChatOptions extends AbstractOpenAiOfficialOptions implements ToolCallingChatOptions {

	public static final String DEFAULT_CHAT_MODEL = GPT_5_MINI.asString();

	private static final Logger logger = LoggerFactory.getLogger(OpenAiOfficialChatOptions.class);

	private Double frequencyPenalty;

	private Map<String, Integer> logitBias;

	private Boolean logprobs;

	private Integer topLogprobs;

	private Integer maxTokens;

	private Integer maxCompletionTokens;

	private Integer n;

	private ChatCompletionAudioParam outputAudio;

	private Double presencePenalty;

	private ResponseFormatJsonSchema responseFormat;

	private ResponseCreateParams.StreamOptions streamOptions;

	private Integer seed;

	private List<String> stop;

	private Double temperature;

	private Double topP;

	private List<FunctionDefinition> tools;

	private ChatCompletionToolChoiceOption toolChoice;

	private String user;

	private Boolean parallelToolCalls;

	private Boolean store;

	private Map<String, String> metadata;

	private String reasoningEffort;

	private String verbosity;

	private String serviceTier;

	private List<ToolCallback> toolCallbacks = new ArrayList<>();

	private Set<String> toolNames = new HashSet<>();

	private Boolean internalToolExecutionEnabled;

	private Map<String, String> httpHeaders = new HashMap<>();

	private Map<String, Object> toolContext = new HashMap<>();

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public Map<String, Integer> getLogitBias() {
		return this.logitBias;
	}

	public void setLogitBias(Map<String, Integer> logitBias) {
		this.logitBias = logitBias;
	}

	public Boolean getLogprobs() {
		return this.logprobs;
	}

	public void setLogprobs(Boolean logprobs) {
		this.logprobs = logprobs;
	}

	public Integer getTopLogprobs() {
		return this.topLogprobs;
	}

	public void setTopLogprobs(Integer topLogprobs) {
		this.topLogprobs = topLogprobs;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public Integer getMaxCompletionTokens() {
		return this.maxCompletionTokens;
	}

	public void setMaxCompletionTokens(Integer maxCompletionTokens) {
		this.maxCompletionTokens = maxCompletionTokens;
	}

	public Integer getN() {
		return this.n;
	}

	public void setN(Integer n) {
		this.n = n;
	}

	public ChatCompletionAudioParam getOutputAudio() {
		return this.outputAudio;
	}

	public void setOutputAudio(ChatCompletionAudioParam outputAudio) {
		this.outputAudio = outputAudio;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public ResponseFormatJsonSchema getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(ResponseFormatJsonSchema responseFormat) {
		this.responseFormat = responseFormat;
	}

	public ResponseCreateParams.StreamOptions getStreamOptions() {
		return this.streamOptions;
	}

	public void setStreamOptions(ResponseCreateParams.StreamOptions streamOptions) {
		this.streamOptions = streamOptions;
	}

	public Integer getSeed() {
		return this.seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	public List<String> getStop() {
		return this.stop;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
	}

	@Override
	public List<String> getStopSequences() {
		return getStop();
	}

	public void setStopSequences(List<String> stopSequences) {
		setStop(stopSequences);
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	public List<FunctionDefinition> getTools() {
		return this.tools;
	}

	public void setTools(List<FunctionDefinition> tools) {
		this.tools = tools;
	}

	public ChatCompletionToolChoiceOption getToolChoice() {
		return this.toolChoice;
	}

	public void setToolChoice(ChatCompletionToolChoiceOption toolChoice) {
		this.toolChoice = toolChoice;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

	public Boolean getParallelToolCalls() {
		return this.parallelToolCalls;
	}

	public void setParallelToolCalls(Boolean parallelToolCalls) {
		this.parallelToolCalls = parallelToolCalls;
	}

	public Boolean getStore() {
		return this.store;
	}

	public void setStore(Boolean store) {
		this.store = store;
	}

	public Map<String, String> getMetadata() {
		return this.metadata;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
	}

	public String getReasoningEffort() {
		return this.reasoningEffort;
	}

	public void setReasoningEffort(String reasoningEffort) {
		this.reasoningEffort = reasoningEffort;
	}

	public String getVerbosity() {
		return this.verbosity;
	}

	public void setVerbosity(String verbosity) {
		this.verbosity = verbosity;
	}

	public String getServiceTier() {
		return this.serviceTier;
	}

	public void setServiceTier(String serviceTier) {
		this.serviceTier = serviceTier;
	}

	@Override
	public List<ToolCallback> getToolCallbacks() {
		return this.toolCallbacks;
	}

	@Override
	public void setToolCallbacks(List<ToolCallback> toolCallbacks) {
		Assert.notNull(toolCallbacks, "toolCallbacks cannot be null");
		Assert.noNullElements(toolCallbacks, "toolCallbacks cannot contain null elements");
		this.toolCallbacks = toolCallbacks;
	}

	@Override
	public Set<String> getToolNames() {
		return this.toolNames;
	}

	@Override
	public void setToolNames(Set<String> toolNames) {
		Assert.notNull(toolNames, "toolNames cannot be null");
		Assert.noNullElements(toolNames, "toolNames cannot contain null elements");
		toolNames.forEach(tool -> Assert.hasText(tool, "toolNames cannot contain empty elements"));
		this.toolNames = toolNames;
	}

	@Override
	@Nullable
	public Boolean getInternalToolExecutionEnabled() {
		return this.internalToolExecutionEnabled;
	}

	@Override
	public void setInternalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
		this.internalToolExecutionEnabled = internalToolExecutionEnabled;
	}

	public Map<String, String> getHttpHeaders() {
		return this.httpHeaders;
	}

	public void setHttpHeaders(Map<String, String> httpHeaders) {
		this.httpHeaders = httpHeaders;
	}

	@Override
	public Map<String, Object> getToolContext() {
		return this.toolContext;
	}

	@Override
	public void setToolContext(Map<String, Object> toolContext) {
		this.toolContext = toolContext;
	}

	@Override
	public Integer getTopK() {
		return null;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public OpenAiOfficialChatOptions copy() {
		return builder().from(this).build();
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass())
			return false;
		OpenAiOfficialChatOptions options = (OpenAiOfficialChatOptions) o;
		return Objects.equals(frequencyPenalty, options.frequencyPenalty)
				&& Objects.equals(logitBias, options.logitBias) && Objects.equals(logprobs, options.logprobs)
				&& Objects.equals(topLogprobs, options.topLogprobs) && Objects.equals(maxTokens, options.maxTokens)
				&& Objects.equals(n, options.n) && Objects.equals(outputAudio, options.outputAudio)
				&& Objects.equals(presencePenalty, options.presencePenalty)
				&& Objects.equals(responseFormat, options.responseFormat)
				&& Objects.equals(streamOptions, options.streamOptions) && Objects.equals(seed, options.seed)
				&& Objects.equals(stop, options.stop) && Objects.equals(temperature, options.temperature)
				&& Objects.equals(topP, options.topP) && Objects.equals(tools, options.tools)
				&& Objects.equals(toolChoice, options.toolChoice) && Objects.equals(user, options.user)
				&& Objects.equals(parallelToolCalls, options.parallelToolCalls) && Objects.equals(store, options.store)
				&& Objects.equals(metadata, options.metadata)
				&& Objects.equals(reasoningEffort, options.reasoningEffort)
				&& Objects.equals(verbosity, options.verbosity) && Objects.equals(serviceTier, options.serviceTier)
				&& Objects.equals(toolCallbacks, options.toolCallbacks) && Objects.equals(toolNames, options.toolNames)
				&& Objects.equals(internalToolExecutionEnabled, options.internalToolExecutionEnabled)
				&& Objects.equals(httpHeaders, options.httpHeaders) && Objects.equals(toolContext, options.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(frequencyPenalty, logitBias, logprobs, topLogprobs, maxTokens, n, outputAudio,
				presencePenalty, responseFormat, streamOptions, seed, stop, temperature, topP, tools, toolChoice, user,
				parallelToolCalls, store, metadata, reasoningEffort, verbosity, serviceTier, toolCallbacks, toolNames,
				internalToolExecutionEnabled, httpHeaders, toolContext);
	}

	@Override
	public String toString() {
		return "OpenAiOfficialChatOptions{" + "frequencyPenalty=" + frequencyPenalty + ", logitBias=" + logitBias
				+ ", logprobs=" + logprobs + ", topLogprobs=" + topLogprobs + ", maxTokens=" + maxTokens + ", n=" + n
				+ ", outputAudio=" + outputAudio + ", presencePenalty=" + presencePenalty + ", responseFormat="
				+ responseFormat + ", streamOptions=" + streamOptions + ", seed=" + seed + ", stop=" + stop
				+ ", temperature=" + temperature + ", topP=" + topP + ", tools=" + tools + ", toolChoice=" + toolChoice
				+ ", user='" + user + '\'' + ", parallelToolCalls=" + parallelToolCalls + ", store=" + store
				+ ", metadata=" + metadata + ", reasoningEffort='" + reasoningEffort + '\'' + ", verbosity='"
				+ verbosity + '\'' + ", serviceTier='" + serviceTier + '\'' + ", toolCallbacks=" + toolCallbacks
				+ ", toolNames=" + toolNames + ", internalToolExecutionEnabled=" + internalToolExecutionEnabled
				+ ", httpHeaders=" + httpHeaders + ", toolContext=" + toolContext + '}';
	}

	public static final class Builder {

		private final OpenAiOfficialChatOptions options = new OpenAiOfficialChatOptions();

		public Builder from(OpenAiOfficialChatOptions fromOptions) {
			this.options.setModel(fromOptions.getModel());
			this.options.setDeploymentName(fromOptions.getDeploymentName());
			this.options.setFrequencyPenalty(fromOptions.getFrequencyPenalty());
			this.options.setLogitBias(fromOptions.getLogitBias());
			this.options.setLogprobs(fromOptions.getLogprobs());
			this.options.setTopLogprobs(fromOptions.getTopLogprobs());
			this.options.setMaxTokens(fromOptions.getMaxTokens());
			this.options.setMaxCompletionTokens(fromOptions.getMaxCompletionTokens());
			this.options.setN(fromOptions.getN());
			this.options.setOutputAudio(fromOptions.getOutputAudio());
			this.options.setPresencePenalty(fromOptions.getPresencePenalty());
			this.options.setResponseFormat(fromOptions.getResponseFormat());
			this.options.setStreamOptions(fromOptions.getStreamOptions());
			this.options.setSeed(fromOptions.getSeed());
			this.options.setStop(fromOptions.getStop() != null ? new ArrayList<>(fromOptions.getStop()) : null);
			this.options.setTemperature(fromOptions.getTemperature());
			this.options.setTopP(fromOptions.getTopP());
			this.options.setTools(fromOptions.getTools());
			this.options.setToolChoice(fromOptions.getToolChoice());
			this.options.setUser(fromOptions.getUser());
			this.options.setParallelToolCalls(fromOptions.getParallelToolCalls());
			this.options.setToolCallbacks(new ArrayList<>(fromOptions.getToolCallbacks()));
			this.options.setToolNames(new HashSet<>(fromOptions.getToolNames()));
			this.options.setHttpHeaders(
					fromOptions.getHttpHeaders() != null ? new HashMap<>(fromOptions.getHttpHeaders()) : null);
			this.options.setInternalToolExecutionEnabled(fromOptions.getInternalToolExecutionEnabled());
			this.options.setToolContext(new HashMap<>(fromOptions.getToolContext()));
			this.options.setStore(fromOptions.getStore());
			this.options.setMetadata(fromOptions.getMetadata());
			this.options.setReasoningEffort(fromOptions.getReasoningEffort());
			this.options.setVerbosity(fromOptions.getVerbosity());
			this.options.setServiceTier(fromOptions.getServiceTier());
			return this;
		}

		public Builder merge(OpenAiOfficialChatOptions from) {
			if (from.getModel() != null) {
				this.options.setModel(from.getModel());
			}
			if (from.getDeploymentName() != null) {
				this.options.setDeploymentName(from.getDeploymentName());
			}
			if (from.getFrequencyPenalty() != null) {
				this.options.setFrequencyPenalty(from.getFrequencyPenalty());
			}
			if (from.getLogitBias() != null) {
				this.options.setLogitBias(from.getLogitBias());
			}
			if (from.getLogprobs() != null) {
				this.options.setLogprobs(from.getLogprobs());
			}
			if (from.getTopLogprobs() != null) {
				this.options.setTopLogprobs(from.getTopLogprobs());
			}
			if (from.getMaxTokens() != null) {
				this.options.setMaxTokens(from.getMaxTokens());
			}
			if (from.getMaxCompletionTokens() != null) {
				this.options.setMaxCompletionTokens(from.getMaxCompletionTokens());
			}
			if (from.getN() != null) {
				this.options.setN(from.getN());
			}
			if (from.getOutputAudio() != null) {
				this.options.setOutputAudio(from.getOutputAudio());
			}
			if (from.getPresencePenalty() != null) {
				this.options.setPresencePenalty(from.getPresencePenalty());
			}
			if (from.getResponseFormat() != null) {
				this.options.setResponseFormat(from.getResponseFormat());
			}
			if (from.getStreamOptions() != null) {
				this.options.setStreamOptions(from.getStreamOptions());
			}
			if (from.getSeed() != null) {
				this.options.setSeed(from.getSeed());
			}
			if (from.getStop() != null) {
				this.options.setStop(new ArrayList<>(from.getStop()));
			}
			if (from.getTemperature() != null) {
				this.options.setTemperature(from.getTemperature());
			}
			if (from.getTopP() != null) {
				this.options.setTopP(from.getTopP());
			}
			if (from.getTools() != null) {
				this.options.setTools(from.getTools());
			}
			if (from.getToolChoice() != null) {
				this.options.setToolChoice(from.getToolChoice());
			}
			if (from.getUser() != null) {
				this.options.setUser(from.getUser());
			}
			if (from.getParallelToolCalls() != null) {
				this.options.setParallelToolCalls(from.getParallelToolCalls());
			}
			if (!from.getToolCallbacks().isEmpty()) {
				this.options.setToolCallbacks(new ArrayList<>(from.getToolCallbacks()));
			}
			if (!from.getToolNames().isEmpty()) {
				this.options.setToolNames(new HashSet<>(from.getToolNames()));
			}
			if (from.getHttpHeaders() != null) {
				this.options.setHttpHeaders(new HashMap<>(from.getHttpHeaders()));
			}
			if (from.getInternalToolExecutionEnabled() != null) {
				this.options.setInternalToolExecutionEnabled(from.getInternalToolExecutionEnabled());
			}
			if (!from.getToolContext().isEmpty()) {
				this.options.setToolContext(new HashMap<>(from.getToolContext()));
			}
			if (from.getStore() != null) {
				this.options.setStore(from.getStore());
			}
			if (from.getMetadata() != null) {
				this.options.setMetadata(from.getMetadata());
			}
			if (from.getReasoningEffort() != null) {
				this.options.setReasoningEffort(from.getReasoningEffort());
			}
			if (from.getVerbosity() != null) {
				this.options.setVerbosity(from.getVerbosity());
			}
			if (from.getServiceTier() != null) {
				this.options.setServiceTier(from.getServiceTier());
			}
			return this;
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder deploymentName(String deploymentName) {
			this.options.setDeploymentName(deploymentName);
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.options.setFrequencyPenalty(frequencyPenalty);
			return this;
		}

		public Builder logitBias(Map<String, Integer> logitBias) {
			this.options.setLogitBias(logitBias);
			return this;
		}

		public Builder logprobs(Boolean logprobs) {
			this.options.setLogprobs(logprobs);
			return this;
		}

		public Builder topLogprobs(Integer topLogprobs) {
			this.options.setTopLogprobs(topLogprobs);
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			if (maxTokens != null && this.options.getMaxCompletionTokens() != null) {
				logger
					.warn("Both maxTokens and maxCompletionTokens are set. OpenAI API does not support setting both parameters simultaneously. "
							+ "The previously set maxCompletionTokens ({}) will be cleared and maxTokens ({}) will be used.",
							this.options.getMaxCompletionTokens(), maxTokens);
				this.options.setMaxCompletionTokens(null);
			}
			this.options.setMaxTokens(maxTokens);
			return this;
		}

		public Builder maxCompletionTokens(Integer maxCompletionTokens) {
			if (maxCompletionTokens != null && this.options.getMaxTokens() != null) {
				logger
					.warn("Both maxTokens and maxCompletionTokens are set. OpenAI API does not support setting both parameters simultaneously. "
							+ "The previously set maxTokens ({}) will be cleared and maxCompletionTokens ({}) will be used.",
							this.options.getMaxTokens(), maxCompletionTokens);
				this.options.setMaxTokens(null);
			}
			this.options.setMaxCompletionTokens(maxCompletionTokens);
			return this;
		}

		public Builder N(Integer n) {
			this.options.setN(n);
			return this;
		}

		public Builder outputAudio(ChatCompletionAudioParam audio) {
			this.options.setOutputAudio(audio);
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.options.setPresencePenalty(presencePenalty);
			return this;
		}

		public Builder responseFormat(ResponseFormatJsonSchema responseFormat) {
			this.options.setResponseFormat(responseFormat);
			return this;
		}

		public Builder seed(Integer seed) {
			this.options.setSeed(seed);
			return this;
		}

		public Builder stop(List<String> stop) {
			this.options.setStop(stop);
			return this;
		}

		public Builder temperature(Double temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder topP(Double topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder tools(List<FunctionDefinition> tools) {
			this.options.setTools(tools);
			return this;
		}

		public Builder toolChoice(ChatCompletionToolChoiceOption toolChoice) {
			this.options.setToolChoice(toolChoice);
			return this;
		}

		public Builder user(String user) {
			this.options.setUser(user);
			return this;
		}

		public Builder parallelToolCalls(Boolean parallelToolCalls) {
			this.options.setParallelToolCalls(parallelToolCalls);
			return this;
		}

		public Builder toolCallbacks(List<ToolCallback> toolCallbacks) {
			this.options.setToolCallbacks(toolCallbacks);
			return this;
		}

		public Builder toolCallbacks(ToolCallback... toolCallbacks) {
			this.options.setToolCallbacks(Arrays.asList(toolCallbacks));
			return this;
		}

		public Builder toolNames(Set<String> toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.setToolNames(toolNames);
			return this;
		}

		public Builder toolNames(String... toolNames) {
			Assert.notNull(toolNames, "toolNames cannot be null");
			this.options.setToolNames(new HashSet<>(Arrays.asList(toolNames)));
			return this;
		}

		public Builder httpHeaders(Map<String, String> httpHeaders) {
			this.options.setHttpHeaders(httpHeaders);
			return this;
		}

		public Builder internalToolExecutionEnabled(@Nullable Boolean internalToolExecutionEnabled) {
			this.options.setInternalToolExecutionEnabled(internalToolExecutionEnabled);
			return this;
		}

		public Builder toolContext(Map<String, Object> toolContext) {
			this.options.setToolContext(toolContext);
			return this;
		}

		public Builder store(Boolean store) {
			this.options.setStore(store);
			return this;
		}

		public Builder metadata(Map<String, String> metadata) {
			this.options.setMetadata(metadata);
			return this;
		}

		public Builder reasoningEffort(String reasoningEffort) {
			this.options.setReasoningEffort(reasoningEffort);
			return this;
		}

		public Builder verbosity(String verbosity) {
			this.options.setVerbosity(verbosity);
			return this;
		}

		public Builder serviceTier(String serviceTier) {
			this.options.setServiceTier(serviceTier);
			return this;
		}

		public OpenAiOfficialChatOptions build() {
			return this.options;
		}

	}

}
