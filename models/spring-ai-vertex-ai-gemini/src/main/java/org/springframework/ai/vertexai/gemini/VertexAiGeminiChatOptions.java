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

package org.springframework.ai.vertexai.gemini;

import java.util.ArrayList;
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

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel.ChatModel;
import org.springframework.ai.vertexai.gemini.common.VertexAiGeminiSafetySetting;
import org.springframework.util.Assert;

/**
 * Options for the Vertex AI Gemini Chat API.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Grogdunn
 * @author Ilayaperumal Gopinathan
 * @since 1.0.0
 */
@JsonInclude(Include.NON_NULL)
public class VertexAiGeminiChatOptions implements FunctionCallingOptions {

	// https://cloud.google.com/vertex-ai/docs/reference/rest/v1/GenerationConfig

	/**
	 * Optional. Stop sequences.
	 */
	private @JsonProperty("stopSequences") List<String> stopSequences;

	// @formatter:off

	/**
	 * Optional. Controls the randomness of predictions.
	 */
	private @JsonProperty("temperature") Double temperature;

	/**
	 * Optional. If specified, nucleus sampling will be used.
	 */
	private @JsonProperty("topP") Double topP;

	/**
	 * Optional. If specified, top k sampling will be used.
	 */
	private @JsonProperty("topK") Integer topK;

	/**
	 * Optional. The maximum number of tokens to generate.
	 */
	private @JsonProperty("candidateCount") Integer candidateCount;

	/**
	 * Optional. The maximum number of tokens to generate.
	 */
	private @JsonProperty("maxOutputTokens") Integer maxOutputTokens;

	/**
	 * Gemini model name.
	 */
	private @JsonProperty("modelName") String model;

	/**
	 * Optional. Output response mimetype of the generated candidate text.
	 * - text/plain: (default) Text output.
	 * - application/json: JSON response in the candidates.
	 */
	private @JsonProperty("responseMimeType") String responseMimeType;

	/**
	 * Tool Function Callbacks to register with the ChatModel.
	 * For Prompt Options the functionCallbacks are automatically enabled for the duration of the prompt execution.
	 * For Default Options the functionCallbacks are registered but disabled by default. Use the enableFunctions to set the functions
	 * from the registry to be used by the ChatModel chat completion requests.
	 */
	@JsonIgnore
	private List<FunctionCallback> functionCallbacks = new ArrayList<>();

	/**
	 * List of functions, identified by their names, to configure for function calling in
	 * the chat completion requests.
	 * Functions with those names must exist in the functionCallbacks registry.
	 * The {@link #functionCallbacks} from the PromptOptions are automatically enabled for the duration of the prompt execution.
	 *
	 * Note that function enabled with the default options are enabled for all chat completion requests. This could impact the token count and the billing.
	 * If the functions is set in a prompt options, then the enabled functions are only active for the duration of this prompt execution.
	 */
	@JsonIgnore
	private Set<String> functions = new HashSet<>();

	/**
	 * Use Google search Grounding feature
	 */
	@JsonIgnore
	private boolean googleSearchRetrieval = false;

	@JsonIgnore
	private List<VertexAiGeminiSafetySetting> safetySettings = new ArrayList<>();

	@JsonIgnore
	private Boolean proxyToolCalls;

	@JsonIgnore
	private Map<String, Object> toolContext;

	public static Builder builder() {
		return new Builder();
	}

	// @formatter:on

	public static VertexAiGeminiChatOptions fromOptions(VertexAiGeminiChatOptions fromOptions) {
		VertexAiGeminiChatOptions options = new VertexAiGeminiChatOptions();
		options.setStopSequences(fromOptions.getStopSequences());
		options.setTemperature(fromOptions.getTemperature());
		options.setTopP(fromOptions.getTopP());
		options.setTopK(fromOptions.getTopK());
		options.setCandidateCount(fromOptions.getCandidateCount());
		options.setMaxOutputTokens(fromOptions.getMaxOutputTokens());
		options.setModel(fromOptions.getModel());
		options.setFunctionCallbacks(fromOptions.getFunctionCallbacks());
		options.setResponseMimeType(fromOptions.getResponseMimeType());
		options.setFunctions(fromOptions.getFunctions());
		options.setResponseMimeType(fromOptions.getResponseMimeType());
		options.setGoogleSearchRetrieval(fromOptions.getGoogleSearchRetrieval());
		options.setSafetySettings(fromOptions.getSafetySettings());
		options.setProxyToolCalls(fromOptions.getProxyToolCalls());
		options.setToolContext(fromOptions.getToolContext());
		return options;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stopSequences;
	}

	public void setStopSequences(List<String> stopSequences) {
		this.stopSequences = stopSequences;
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

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	public Integer getCandidateCount() {
		return this.candidateCount;
	}

	public void setCandidateCount(Integer candidateCount) {
		this.candidateCount = candidateCount;
	}

	@Override
	@JsonIgnore
	public Integer getMaxTokens() {
		return getMaxOutputTokens();
	}

	@JsonIgnore
	public void setMaxTokens(Integer maxTokens) {
		setMaxOutputTokens(maxTokens);
	}

	public Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	public void setMaxOutputTokens(Integer maxOutputTokens) {
		this.maxOutputTokens = maxOutputTokens;
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String modelName) {
		this.model = modelName;
	}

	public String getResponseMimeType() {
		return this.responseMimeType;
	}

	public void setResponseMimeType(String mimeType) {
		this.responseMimeType = mimeType;
	}

	public List<FunctionCallback> getFunctionCallbacks() {
		return this.functionCallbacks;
	}

	public void setFunctionCallbacks(List<FunctionCallback> functionCallbacks) {
		this.functionCallbacks = functionCallbacks;
	}

	public Set<String> getFunctions() {
		return this.functions;
	}

	public void setFunctions(Set<String> functions) {
		this.functions = functions;
	}

	@Override
	@JsonIgnore
	public Double getFrequencyPenalty() {
		return null;
	}

	@Override
	@JsonIgnore
	public Double getPresencePenalty() {
		return null;
	}

	public boolean getGoogleSearchRetrieval() {
		return this.googleSearchRetrieval;
	}

	public void setGoogleSearchRetrieval(boolean googleSearchRetrieval) {
		this.googleSearchRetrieval = googleSearchRetrieval;
	}

	public List<VertexAiGeminiSafetySetting> getSafetySettings() {
		return this.safetySettings;
	}

	public void setSafetySettings(List<VertexAiGeminiSafetySetting> safetySettings) {
		Assert.notNull(safetySettings, "safetySettings must not be null");
		this.safetySettings = safetySettings;
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
		if (this == o) {
			return true;
		}
		if (!(o instanceof VertexAiGeminiChatOptions that)) {
			return false;
		}
		return this.googleSearchRetrieval == that.googleSearchRetrieval
				&& Objects.equals(this.stopSequences, that.stopSequences)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.candidateCount, that.candidateCount)
				&& Objects.equals(this.maxOutputTokens, that.maxOutputTokens) && Objects.equals(this.model, that.model)
				&& Objects.equals(this.responseMimeType, that.responseMimeType)
				&& Objects.equals(this.functionCallbacks, that.functionCallbacks)
				&& Objects.equals(this.functions, that.functions)
				&& Objects.equals(this.safetySettings, that.safetySettings)
				&& Objects.equals(this.proxyToolCalls, that.proxyToolCalls)
				&& Objects.equals(this.toolContext, that.toolContext);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.stopSequences, this.temperature, this.topP, this.topK, this.candidateCount,
				this.maxOutputTokens, this.model, this.responseMimeType, this.functionCallbacks, this.functions,
				this.googleSearchRetrieval, this.safetySettings, this.proxyToolCalls, this.toolContext);
	}

	@Override
	public String toString() {
		return "VertexAiGeminiChatOptions{" + "stopSequences=" + this.stopSequences + ", temperature="
				+ this.temperature + ", topP=" + this.topP + ", topK=" + this.topK + ", candidateCount="
				+ this.candidateCount + ", maxOutputTokens=" + this.maxOutputTokens + ", model='" + this.model + '\''
				+ ", responseMimeType='" + this.responseMimeType + '\'' + ", functionCallbacks="
				+ this.functionCallbacks + ", functions=" + this.functions + ", googleSearchRetrieval="
				+ this.googleSearchRetrieval + ", safetySettings=" + this.safetySettings + '}';
	}

	@Override
	public VertexAiGeminiChatOptions copy() {
		return fromOptions(this);
	}

	public FunctionCallingOptions merge(ChatOptions options) {
		VertexAiGeminiChatOptions.Builder builder = VertexAiGeminiChatOptions.builder();

		// Merge chat-specific options
		builder.model(options.getModel() != null ? options.getModel() : this.getModel())
			.maxOutputTokens(options.getMaxTokens() != null ? options.getMaxTokens() : this.getMaxOutputTokens())
			.stopSequences(options.getStopSequences() != null ? options.getStopSequences() : this.getStopSequences())
			.temperature(options.getTemperature() != null ? options.getTemperature() : this.getTemperature())
			.topP(options.getTopP() != null ? options.getTopP() : this.getTopP())
			.topK(options.getTopK() != null ? options.getTopK() : this.getTopK());

		// Try to get function-specific properties if options is a FunctionCallingOptions
		if (options instanceof FunctionCallingOptions functionOptions) {
			builder.proxyToolCalls(functionOptions.getProxyToolCalls() != null ? functionOptions.getProxyToolCalls()
					: this.proxyToolCalls);

			Set<String> functions = new HashSet<>();
			if (this.functions != null) {
				functions.addAll(this.functions);
			}
			if (functionOptions.getFunctions() != null) {
				functions.addAll(functionOptions.getFunctions());
			}
			builder.functions(functions);

			List<FunctionCallback> functionCallbacks = new ArrayList<>();
			if (this.functionCallbacks != null) {
				functionCallbacks.addAll(this.functionCallbacks);
			}
			if (functionOptions.getFunctionCallbacks() != null) {
				functionCallbacks.addAll(functionOptions.getFunctionCallbacks());
			}
			builder.functionCallbacks(functionCallbacks);

			Map<String, Object> context = new HashMap<>();
			if (this.toolContext != null) {
				context.putAll(this.toolContext);
			}
			if (functionOptions.getToolContext() != null) {
				context.putAll(functionOptions.getToolContext());
			}
			builder.toolContext(context);
		}
		else {
			// If not a FunctionCallingOptions, preserve current function-specific
			// properties
			builder.proxyToolCalls(this.proxyToolCalls);
			builder.functions(this.functions != null ? new HashSet<>(this.functions) : null);
			builder.functionCallbacks(this.functionCallbacks != null ? new ArrayList<>(this.functionCallbacks) : null);
			builder.toolContext(this.toolContext != null ? new HashMap<>(this.toolContext) : null);
		}

		// Preserve Vertex AI Gemini-specific properties
		builder.candidateCount(this.candidateCount)
			.responseMimeType(this.responseMimeType)
			.googleSearchRetrieval(this.googleSearchRetrieval)
			.safetySettings(this.safetySettings != null ? new ArrayList<>(this.safetySettings) : null);

		return builder.build();
	}

	public enum TransportType {

		GRPC, REST

	}

	public static class Builder {

		private VertexAiGeminiChatOptions options = new VertexAiGeminiChatOptions();

		public Builder stopSequences(List<String> stopSequences) {
			this.options.setStopSequences(stopSequences);
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

		public Builder topK(Integer topK) {
			this.options.setTopK(topK);
			return this;
		}

		public Builder candidateCount(Integer candidateCount) {
			this.options.setCandidateCount(candidateCount);
			return this;
		}

		public Builder maxOutputTokens(Integer maxOutputTokens) {
			this.options.setMaxOutputTokens(maxOutputTokens);
			return this;
		}

		public Builder model(String modelName) {
			this.options.setModel(modelName);
			return this;
		}

		public Builder model(ChatModel model) {
			this.options.setModel(model.getValue());
			return this;
		}

		public Builder responseMimeType(String mimeType) {
			Assert.notNull(mimeType, "mimeType must not be null");
			this.options.setResponseMimeType(mimeType);
			return this;
		}

		public Builder functionCallbacks(List<FunctionCallback> functionCallbacks) {
			this.options.functionCallbacks = functionCallbacks;
			return this;
		}

		public Builder functions(Set<String> functionNames) {
			Assert.notNull(functionNames, "Function names must not be null");
			this.options.functions = functionNames;
			return this;
		}

		public Builder function(String functionName) {
			Assert.hasText(functionName, "Function name must not be empty");
			this.options.functions.add(functionName);
			return this;
		}

		public Builder googleSearchRetrieval(boolean googleSearch) {
			this.options.googleSearchRetrieval = googleSearch;
			return this;
		}

		public Builder safetySettings(List<VertexAiGeminiSafetySetting> safetySettings) {
			Assert.notNull(safetySettings, "safetySettings must not be null");
			this.options.safetySettings = safetySettings;
			return this;
		}

		public Builder proxyToolCalls(boolean proxyToolCalls) {
			this.options.proxyToolCalls = proxyToolCalls;
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

		public VertexAiGeminiChatOptions build() {
			return this.options;
		}

	}

}
