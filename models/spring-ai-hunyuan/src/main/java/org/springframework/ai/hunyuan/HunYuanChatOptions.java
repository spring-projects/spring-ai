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

package org.springframework.ai.hunyuan;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.hunyuan.api.HunYuanApi;
import org.springframework.util.Assert;

/**
 * Options for HunYuan chat completions.
 *
 * @author Guo Junyu
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HunYuanChatOptions implements FunctionCallingOptions {

	private @JsonProperty("Model") String model;

	private @JsonProperty("Temperature") Double temperature;

	private @JsonProperty("TopP") Double topP;

	private @JsonProperty("Seed") Integer seed;

	private @JsonProperty("EnableEnhancement") Boolean enableEnhancement;

	private @JsonProperty("StreamModeration") Boolean streamModeration;

	private @JsonProperty("Stop") List<String> stop;

	private @JsonProperty("Tools") List<HunYuanApi.FunctionTool> tools;

	private @JsonProperty("ToolChoice") String toolChoice;

	private @JsonProperty("CustomTool") HunYuanApi.FunctionTool customTool;

	private @JsonProperty("SearchInfo") Boolean searchInfo;

	private @JsonProperty("Citation") Boolean citation;

	private @JsonProperty("EnableSpeedSearch") Boolean enableSpeedSearch;

	private @JsonProperty("EnableMultimedia") Boolean enableMultimedia;

	private @JsonProperty("EnableDeepSearch") Boolean enableDeepSearch;

	private @JsonProperty("ForceSearchEnhancement") Boolean ForceSearchEnhancement;

	private @JsonProperty("EnableRecommendedQuestions") Boolean enableRecommendedQuestions;

	/**
	 * HunYuan Tool Function Callbacks to register with the ChatModel. For Prompt Options
	 * the functionCallbacks are automatically enabled for the duration of the prompt
	 * execution. For Default Options the functionCallbacks are registered but disabled by
	 * default. Use the enableFunctions to set the functions from the registry to be used
	 * by the ChatModel chat completion requests.
	 */
	@JsonIgnore
	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests. Functions with those names must exist in the
	 * functionCallbacks registry. The {@link #functionCallbacks} from the PromptOptions
	 * are automatically enabled for the duration of the prompt execution.
	 *
	 * Note that function enabled with the default options are enabled for all chat
	 * completion requests. This could impact the token count and the billing. If the
	 * functions is set in a prompt options, then the enabled functions are only active
	 * for the duration of this prompt execution.
	 */
	@JsonIgnore
	private Set<String> functions = new HashSet<>();

	@JsonIgnore
	private Boolean proxyToolCalls;

	@JsonIgnore
	private Map<String, Object> toolContext;

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public List<FunctionCallback> getFunctionCallbacks() {
		return this.functionCallbacks;
	}

	@Override
	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.functionCallbacks = functionCallbacks;
	}

	@Override
	public Set<String> getFunctions() {
		return this.functions;
	}

	public void setFunctions(Set<String> functionNames) {
		this.functions = functionNames;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public Double getFrequencyPenalty() {
		return null;
	}

	@Override
	public Integer getMaxTokens() {
		return null;
	}

	@Override
	public Double getPresencePenalty() {
		return null;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Integer getSeed() {
		return this.seed;
	}

	public void setSeed(Integer seed) {
		this.seed = seed;
	}

	@Override
	@JsonIgnore
	public List<String> getStopSequences() {
		return getStop();
	}

	@JsonIgnore
	public void setStopSequences(List<String> stopSequences) {
		setStop(stopSequences);
	}

	public List<String> getStop() {
		return this.stop;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
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

	@Override
	public <T extends ChatOptions> T copy() {
		return null;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	public Boolean getEnableEnhancement() {
		return enableEnhancement;
	}

	public void setEnableEnhancement(Boolean enableEnhancement) {
		this.enableEnhancement = enableEnhancement;
	}

	public Boolean getStreamModeration() {
		return streamModeration;
	}

	public void setStreamModeration(Boolean streamModeration) {
		this.streamModeration = streamModeration;
	}

	public List<HunYuanApi.FunctionTool> getTools() {
		return tools;
	}

	public void setTools(List<HunYuanApi.FunctionTool> tools) {
		this.tools = tools;
	}

	public String getToolChoice() {
		return toolChoice;
	}

	public void setToolChoice(String toolChoice) {
		this.toolChoice = toolChoice;
	}

	public HunYuanApi.FunctionTool getCustomTool() {
		return customTool;
	}

	public void setCustomTool(HunYuanApi.FunctionTool customTool) {
		this.customTool = customTool;
	}

	public Boolean getSearchInfo() {
		return searchInfo;
	}

	public void setSearchInfo(Boolean searchInfo) {
		this.searchInfo = searchInfo;
	}

	public Boolean getCitation() {
		return citation;
	}

	public void setCitation(Boolean citation) {
		this.citation = citation;
	}

	public Boolean getEnableSpeedSearch() {
		return enableSpeedSearch;
	}

	public void setEnableSpeedSearch(Boolean enableSpeedSearch) {
		this.enableSpeedSearch = enableSpeedSearch;
	}

	public Boolean getEnableMultimedia() {
		return enableMultimedia;
	}

	public void setEnableMultimedia(Boolean enableMultimedia) {
		this.enableMultimedia = enableMultimedia;
	}

	public Boolean getEnableDeepSearch() {
		return enableDeepSearch;
	}

	public void setEnableDeepSearch(Boolean enableDeepSearch) {
		this.enableDeepSearch = enableDeepSearch;
	}

	public Boolean getForceSearchEnhancement() {
		return ForceSearchEnhancement;
	}

	public void setForceSearchEnhancement(Boolean forceSearchEnhancement) {
		ForceSearchEnhancement = forceSearchEnhancement;
	}

	public Boolean getEnableRecommendedQuestions() {
		return enableRecommendedQuestions;
	}

	public void setEnableRecommendedQuestions(Boolean enableRecommendedQuestions) {
		this.enableRecommendedQuestions = enableRecommendedQuestions;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		return null;
	}

	@Override
	public Boolean getProxyToolCalls() {
		return this.proxyToolCalls;
	}

	public void setProxyToolCalls(Boolean proxyToolCalls) {
		this.proxyToolCalls = proxyToolCalls;
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
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		HunYuanChatOptions that = (HunYuanChatOptions) o;

		if (!model.equals(that.model))
			return false;
		if (!Objects.equals(temperature, that.temperature))
			return false;
		if (!Objects.equals(topP, that.topP))
			return false;
		if (!Objects.equals(seed, that.seed))
			return false;
		if (!Objects.equals(enableEnhancement, that.enableEnhancement))
			return false;
		if (!Objects.equals(streamModeration, that.streamModeration))
			return false;
		if (!Objects.equals(stop, that.stop))
			return false;
		if (!Objects.equals(tools, that.tools))
			return false;
		if (!Objects.equals(toolChoice, that.toolChoice))
			return false;
		if (!Objects.equals(customTool, that.customTool))
			return false;
		if (!Objects.equals(searchInfo, that.searchInfo))
			return false;
		if (!Objects.equals(citation, that.citation))
			return false;
		if (!Objects.equals(enableSpeedSearch, that.enableSpeedSearch))
			return false;
		if (!Objects.equals(enableMultimedia, that.enableMultimedia))
			return false;
		if (!Objects.equals(enableDeepSearch, that.enableDeepSearch))
			return false;
		if (!Objects.equals(ForceSearchEnhancement, that.ForceSearchEnhancement))
			return false;
		if (!Objects.equals(enableRecommendedQuestions, that.enableRecommendedQuestions))
			return false;
		if (!Objects.equals(functionCallbacks, that.functionCallbacks))
			return false;
		if (!Objects.equals(functions, that.functions))
			return false;
		if (!Objects.equals(proxyToolCalls, that.proxyToolCalls))
			return false;
		return Objects.equals(toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (temperature != null ? temperature.hashCode() : 0);
		result = prime * result + (topP != null ? topP.hashCode() : 0);
		result = prime * result + (seed != null ? seed.hashCode() : 0);
		result = prime * result + (enableEnhancement != null ? enableEnhancement.hashCode() : 0);
		result = prime * result + (streamModeration != null ? streamModeration.hashCode() : 0);
		result = prime * result + (stop != null ? stop.hashCode() : 0);
		result = prime * result + (tools != null ? tools.hashCode() : 0);
		result = prime * result + (toolChoice != null ? toolChoice.hashCode() : 0);
		result = prime * result + (customTool != null ? customTool.hashCode() : 0);
		result = prime * result + (searchInfo != null ? searchInfo.hashCode() : 0);
		result = prime * result + (citation != null ? citation.hashCode() : 0);
		result = prime * result + (enableSpeedSearch != null ? enableSpeedSearch.hashCode() : 0);
		result = prime * result + (enableMultimedia != null ? enableMultimedia.hashCode() : 0);
		result = prime * result + (enableDeepSearch != null ? enableDeepSearch.hashCode() : 0);
		result = prime * result + (ForceSearchEnhancement != null ? ForceSearchEnhancement.hashCode() : 0);
		result = prime * result + (enableRecommendedQuestions != null ? enableRecommendedQuestions.hashCode() : 0);
		result = prime * result + (functionCallbacks != null ? functionCallbacks.hashCode() : 0);
		result = prime * result + (functions != null ? functions.hashCode() : 0);
		result = prime * result + (proxyToolCalls != null ? proxyToolCalls.hashCode() : 0);
		result = prime * result + (toolContext != null ? toolContext.hashCode() : 0);
		return result;
	}

	public static class Builder {

		private final HunYuanChatOptions options = new HunYuanChatOptions();

		public Builder model(String model) {
			options.setModel(model);
			return this;
		}

		public Builder temperature(Double temperature) {
			options.setTemperature(temperature);
			return this;
		}

		public Builder topP(Double topP) {
			options.setTopP(topP);
			return this;
		}

		public Builder seed(Integer seed) {
			options.setSeed(seed);
			return this;
		}

		public Builder enableEnhancement(Boolean enableEnhancement) {
			options.setEnableEnhancement(enableEnhancement);
			return this;
		}

		public Builder streamModeration(Boolean streamModeration) {
			options.setStreamModeration(streamModeration);
			return this;
		}

		public Builder stop(List<String> stop) {
			options.setStop(stop);
			return this;
		}

		public Builder tools(List<HunYuanApi.FunctionTool> tools) {
			options.setTools(tools);
			return this;
		}

		public Builder toolChoice(String toolChoice) {
			options.setToolChoice(toolChoice);
			return this;
		}

		public Builder customTool(HunYuanApi.FunctionTool customTool) {
			options.setCustomTool(customTool);
			return this;
		}

		public Builder searchInfo(Boolean searchInfo) {
			options.setSearchInfo(searchInfo);
			return this;
		}

		public Builder citation(Boolean citation) {
			options.setCitation(citation);
			return this;
		}

		public Builder enableSpeedSearch(Boolean enableSpeedSearch) {
			options.setEnableSpeedSearch(enableSpeedSearch);
			return this;
		}

		public Builder enableMultimedia(Boolean enableMultimedia) {
			options.setEnableMultimedia(enableMultimedia);
			return this;
		}

		public Builder enableDeepSearch(Boolean enableDeepSearch) {
			options.setEnableDeepSearch(enableDeepSearch);
			return this;
		}

		public Builder forceSearchEnhancement(Boolean forceSearchEnhancement) {
			options.setForceSearchEnhancement(forceSearchEnhancement);
			return this;
		}

		public Builder enableRecommendedQuestions(Boolean enableRecommendedQuestions) {
			options.setEnableRecommendedQuestions(enableRecommendedQuestions);
			return this;
		}

		public Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			options.setFunctionCallbacks(functionCallbacks);
			return this;
		}

		public Builder functions(Set<String> functions) {
			options.setFunctions(functions);
			return this;
		}

		public Builder function(String functionName) {
			Assert.hasText(functionName, "Function name must not be empty");
			if (this.options.functions == null) {
				this.options.functions = new HashSet<>();
			}
			this.options.functions.add(functionName);
			return this;
		}

		public Builder proxyToolCalls(Boolean proxyToolCalls) {
			options.setProxyToolCalls(proxyToolCalls);
			return this;
		}

		public Builder toolContext(Map<String, Object> toolContext) {
			options.setToolContext(toolContext);
			return this;
		}

		public HunYuanChatOptions build() {
			return options;
		}

	}

}
