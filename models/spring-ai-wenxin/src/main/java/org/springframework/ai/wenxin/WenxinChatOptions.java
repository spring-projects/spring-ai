package org.springframework.ai.wenxin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.wenxin.api.WenxinApi;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author lvchzh
 * @date 2024年05月14日 下午5:26
 * @description:
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WenxinChatOptions implements FunctionCallingOptions, ChatOptions {

	//
	private @JsonProperty("model") String model;

	private @JsonProperty("penalty_score") Float penaltyScore;

	private @JsonProperty("max_output_tokens") Integer maxOutputTokens;

	private @JsonProperty("response_format") WenxinApi.ChatCompletionRequest.ResponseFormat responseFormat;

	private @JsonProperty("stop") List<String> stop;

	private @JsonProperty("temperature") Float temperature;

	private @JsonProperty("top_p") Float topP;

	private @JsonProperty("functions") List<WenxinApi.FunctionTool> tools;

	private @JsonProperty("tool_choice") String toolChoice;

	private @JsonProperty("user_id") String userId;

	private @JsonProperty("system") String system;

	private @JsonProperty("disable_search") Boolean disableSearch;

	private @JsonProperty("enable_citation") Boolean enableCitation;

	private @JsonProperty("enable_trace") Boolean enableTrace;

	@NestedConfigurationProperty
	@JsonIgnore
	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	@NestedConfigurationProperty
	@JsonIgnore
	private Set<String> functions = new HashSet<>();

	public static Builder builder() {
		return new Builder();
	}

	// @formatter:off
	public static WenxinChatOptions fromOptions(WenxinChatOptions fromOptions) {
		return WenxinChatOptions.builder()
				.withModel(fromOptions.getModel())
				.withPenaltyScore(fromOptions.getPenaltyScore())
				.withMaxOutputTokens(fromOptions.getMaxOutputTokens())
				.withResponseFormat(fromOptions.getResponseFormat())
				.withStop(fromOptions.getStop())
				.withTemperature(fromOptions.getTemperature())
				.withTopP(fromOptions.getTopP())
				.withTools(fromOptions.getTools())
				.withToolChoice(fromOptions.getToolChoice())
				.withUserId(fromOptions.getUserId())
				.withSystem(fromOptions.getSystem())
				.withDisableSearch(fromOptions.getDisableSearch())
				.withEnableCitation(fromOptions.getEnableCitation())
				.withEnableTrace(fromOptions.getEnableTrace())
				.withFunctionCallbacks(fromOptions.getFunctionCallbacks())
				.withFunctions(fromOptions.getFunctions())
				.build();
	}
	// @formatter:on

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Float getPenaltyScore() {
		return penaltyScore;
	}

	public void setPenaltyScore(Float penaltyScore) {
		this.penaltyScore = penaltyScore;
	}

	public Integer getMaxOutputTokens() {
		return maxOutputTokens;
	}

	public void setMaxOutputTokens(Integer maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	public WenxinApi.ChatCompletionRequest.ResponseFormat getResponseFormat() {
		return responseFormat;
	}

	public void setResponseFormat(WenxinApi.ChatCompletionRequest.ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	public List<String> getStop() {
		return stop;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
	}

	@Override
	public Float getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	@Override
	public Float getTopP() {
		return this.topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	@Override
	@JsonIgnore
	public Integer getTopK() {
		throw new UnsupportedOperationException("Unimplemented method 'getTopK'");
	}

	@JsonIgnore
	public void setTopK(Integer topK) {
		throw new UnsupportedOperationException("Unimplemented method 'setTopK'");
	}

	public List<WenxinApi.FunctionTool> getTools() {
		return tools;
	}

	public void setTools(List<WenxinApi.FunctionTool> tools) {
		this.tools = tools;
	}

	public String getToolChoice() {
		return toolChoice;
	}

	public void setToolChoice(String toolChoice) {
		this.toolChoice = toolChoice;
	}

	public String getUserId() {
		return userId;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public String getSystem() {
		return system;
	}

	public void setSystem(String system) {
		this.system = system;
	}

	public Boolean getDisableSearch() {
		return disableSearch;
	}

	public void setDisableSearch(Boolean disableSearch) {
		this.disableSearch = disableSearch;
	}

	public Boolean getEnableCitation() {
		return enableCitation;
	}

	public void setEnableCitation(Boolean enableCitation) {
		this.enableCitation = enableCitation;
	}

	public Boolean getEnableTrace() {
		return enableTrace;
	}

	public void setEnableTrace(Boolean enableTrace) {
		this.enableTrace = enableTrace;
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
		return functions;
	}

	@Override
	public void setFunctions(Set<String> functionNames) {
		this.functions = functionNames;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((model == null) ? 0 : model.hashCode());
		result = prime * result + ((penaltyScore == null) ? 0 : penaltyScore.hashCode());
		result = prime * result + ((maxOutputTokens == null) ? 0 : maxOutputTokens.hashCode());
		result = prime * result + ((responseFormat == null) ? 0 : responseFormat.hashCode());
		result = prime * result + ((stop == null) ? 0 : stop.hashCode());
		result = prime * result + ((temperature == null) ? 0 : temperature.hashCode());
		result = prime * result + ((topP == null) ? 0 : topP.hashCode());
		result = prime * result + ((tools == null) ? 0 : tools.hashCode());
		result = prime * result + ((toolChoice == null) ? 0 : toolChoice.hashCode());
		result = prime * result + ((userId == null) ? 0 : userId.hashCode());
		result = prime * result + ((system == null) ? 0 : system.hashCode());
		result = prime * result + ((disableSearch == null) ? 0 : disableSearch.hashCode());
		result = prime * result + ((enableCitation == null) ? 0 : enableCitation.hashCode());
		result = prime * result + ((enableTrace == null) ? 0 : enableTrace.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		WenxinChatOptions other = (WenxinChatOptions) obj;
		if (this.model == null) {
			if (other.model != null) {
				return false;
			}
		}
		else if (!this.model.equals(other.model)) {
			return false;
		}
		if (this.penaltyScore == null) {
			if (other.penaltyScore != null) {
				return false;
			}
		}
		else if (!this.penaltyScore.equals(other.penaltyScore)) {
			return false;
		}
		if (this.maxOutputTokens == null) {
			if (other.maxOutputTokens != null) {
				return false;
			}
		}
		else if (!this.maxOutputTokens.equals(other.maxOutputTokens)) {
			return false;
		}
		if (this.responseFormat != other.responseFormat) {
			return false;
		}
		if (this.stop == null) {
			if (other.stop != null) {
				return false;
			}
		}
		else if (!this.stop.equals(other.stop)) {
			return false;
		}
		if (this.temperature == null) {
			if (other.temperature != null) {
				return false;
			}
		}
		else if (!this.temperature.equals(other.temperature)) {
			return false;
		}
		if (this.topP == null) {
			if (other.topP != null) {
				return false;
			}
		}
		else if (!this.topP.equals(other.topP)) {
			return false;
		}
		if (this.tools == null) {
			if (other.tools != null) {
				return false;
			}
		}
		else if (!this.tools.equals(other.tools)) {
			return false;
		}
		if (this.toolChoice == null) {
			if (other.toolChoice != null) {
				return false;
			}
		}
		else if (!this.toolChoice.equals(other.toolChoice)) {
			return false;
		}
		if (this.userId == null) {
			if (other.userId != null) {
				return false;
			}
		}
		else if (!this.userId.equals(other.userId)) {
			return false;
		}
		if (this.system == null) {
			if (other.system != null) {
				return false;
			}
		}
		else if (!this.system.equals(other.system)) {
			return false;
		}
		if (this.disableSearch == null) {
			if (other.disableSearch != null) {
				return false;
			}
		}
		else if (!this.disableSearch.equals(other.disableSearch)) {
			return false;
		}
		if (this.enableCitation == null) {
			if (other.enableCitation != null) {
				return false;
			}
		}
		else if (!this.enableCitation.equals(other.enableCitation)) {
			return false;
		}
		if (this.enableTrace == null) {
			if (other.enableTrace != null) {
				return false;
			}
		}
		else if (!this.enableTrace.equals(other.enableTrace)) {
			return false;
		}
		return true;
	}

	public static class Builder {

		protected WenxinChatOptions options;

		public Builder() {
			this.options = new WenxinChatOptions();
		}

		public Builder(WenxinChatOptions options) {
			this.options = options;
		}

		public Builder withModel(String model) {
			this.options.model = model;
			return this;
		}

		public Builder withPenaltyScore(Float penaltyScore) {
			this.options.penaltyScore = penaltyScore;
			return this;
		}

		public Builder withMaxOutputTokens(Integer maxOutputTokens) {
			this.options.maxOutputTokens = maxOutputTokens;
			return this;
		}

		public Builder withResponseFormat(WenxinApi.ChatCompletionRequest.ResponseFormat responseFormat) {
			this.options.responseFormat = responseFormat;
			return this;
		}

		public Builder withStop(List<String> stop) {
			this.options.stop = stop;
			return this;
		}

		public Builder withTemperature(Float temperature) {
			this.options.temperature = temperature;
			return this;
		}

		public Builder withTopP(Float topP) {
			this.options.topP = topP;
			return this;
		}

		public Builder withTools(List<WenxinApi.FunctionTool> tools) {
			this.options.tools = tools;
			return this;
		}

		public Builder withToolChoice(String toolChoice) {
			this.options.toolChoice = toolChoice;
			return this;
		}

		public Builder withUserId(String userId) {
			this.options.userId = userId;
			return this;
		}

		public Builder withSystem(String system) {
			this.options.system = system;
			return this;
		}

		public Builder withDisableSearch(Boolean disableSearch) {
			this.options.disableSearch = disableSearch;
			return this;
		}

		public Builder withEnableCitation(Boolean enableCitation) {
			this.options.enableCitation = enableCitation;
			return this;
		}

		public Builder withEnableTrace(Boolean enableTrace) {
			this.options.enableTrace = enableTrace;
			return this;
		}

		public Builder withFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
			this.options.functionCallbacks = functionCallbacks;
			return this;
		}

		public Builder withFunctions(Set<String> functionNames) {
			Assert.notNull(functionNames, "Function names must not be null");
			this.options.functions = functionNames;
			return this;
		}

		public Builder withFunction(String functionName) {
			Assert.hasText(functionName, "Function name must not be empty");
			this.options.functions.add(functionName);
			return this;
		}

		public WenxinChatOptions build() {
			return this.options;
		}

	}

}
