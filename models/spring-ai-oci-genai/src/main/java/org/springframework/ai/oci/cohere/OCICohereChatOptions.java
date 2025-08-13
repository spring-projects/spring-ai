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

package org.springframework.ai.oci.cohere;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oracle.bmc.generativeaiinference.model.CohereTool;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * The configuration information for OCI chat requests.
 *
 * @author Anders Swanson
 * @author Ilayaperumal Gopinathan
 * @author Alexnadros Pappas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OCICohereChatOptions implements ChatOptions {

	@JsonProperty("model")
	private String model;

	/**
	 * The maximum number of tokens to generate per request.
	 */
	@JsonProperty("maxTokens")
	private Integer maxTokens;

	/**
	 * The OCI Compartment to run chat requests in.
	 */
	@JsonProperty("compartment")
	private String compartment;

	/**
	 * The serving mode of OCI Gen AI model used. May be "on-demand" or "dedicated".
	 */
	@JsonProperty("servingMode")
	private String servingMode;

	/**
	 * The optional override to the chat model's prompt preamble.
	 */
	@JsonProperty("preambleOverride")
	private String preambleOverride;

	/**
	 * The sample temperature, where higher values are more random, and lower values are
	 * more deterministic.
	 */
	@JsonProperty("temperature")
	private Double temperature;

	/**
	 * The Top P parameter modifies the probability of tokens sampled. E.g., a value of
	 * 0.25 means only tokens from the top 25% probability mass will be considered.
	 */
	@JsonProperty("topP")
	private Double topP;

	/**
	 * The Top K parameter limits the number of potential tokens considered at each step
	 * of text generation. E.g., a value of 5 means only the top 5 most probable tokens
	 * will be considered during each step of text generation.
	 */
	@JsonProperty("topK")
	private Integer topK;

	/**
	 * The frequency penalty assigns a penalty to repeated tokens depending on how many
	 * times it has already appeared in the prompt or output. Higher values will reduce
	 * repeated tokens and outputs will be more random.
	 */
	@JsonProperty("frequencyPenalty")
	private Double frequencyPenalty;

	/**
	 * The presence penalty assigns a penalty to each token when it appears in the output
	 * to encourage generating outputs with tokens that haven't been used.
	 */
	@JsonProperty("presencePenalty")
	private Double presencePenalty;

	/**
	 * A collection of textual sequences that will end completions generation.
	 */
	@JsonProperty("stop")
	private List<String> stop;

	/**
	 * Documents for chat context.
	 */
	@JsonProperty("documents")
	private List<Object> documents;

	/**
	 * Tools for the chatbot.
	 */
	@JsonProperty("tools")
	private List<CohereTool> tools;

	public static OCICohereChatOptions fromOptions(OCICohereChatOptions fromOptions) {
		return builder().model(fromOptions.model)
			.maxTokens(fromOptions.maxTokens)
			.compartment(fromOptions.compartment)
			.servingMode(fromOptions.servingMode)
			.preambleOverride(fromOptions.preambleOverride)
			.temperature(fromOptions.temperature)
			.topP(fromOptions.topP)
			.topK(fromOptions.topK)
			.stop(fromOptions.stop != null ? new ArrayList<>(fromOptions.stop) : null)
			.frequencyPenalty(fromOptions.frequencyPenalty)
			.presencePenalty(fromOptions.presencePenalty)
			.documents(fromOptions.documents != null ? new ArrayList<>(fromOptions.documents) : null)
			.tools(fromOptions.tools != null ? new ArrayList<>(fromOptions.tools) : null)
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public void setPresencePenalty(Double presencePenalty) {
		this.presencePenalty = presencePenalty;
	}

	public void setFrequencyPenalty(Double frequencyPenalty) {
		this.frequencyPenalty = frequencyPenalty;
	}

	public void setTopK(Integer topK) {
		this.topK = topK;
	}

	public void setTopP(Double topP) {
		this.topP = topP;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public String getPreambleOverride() {
		return this.preambleOverride;
	}

	public void setPreambleOverride(String preambleOverride) {
		this.preambleOverride = preambleOverride;
	}

	public String getServingMode() {
		return this.servingMode;
	}

	public void setServingMode(String servingMode) {
		this.servingMode = servingMode;
	}

	public String getCompartment() {
		return this.compartment;
	}

	public void setCompartment(String compartment) {
		this.compartment = compartment;
	}

	public void setMaxTokens(Integer maxTokens) {
		this.maxTokens = maxTokens;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public List<String> getStop() {
		return this.stop;
	}

	public void setStop(List<String> stop) {
		this.stop = stop;
	}

	public List<Object> getDocuments() {
		return this.documents;
	}

	public void setDocuments(List<Object> documents) {
		this.documents = documents;
	}

	public List<CohereTool> getTools() {
		return this.tools;
	}

	public void setTools(List<CohereTool> tools) {
		this.tools = tools;
	}

	/*
	 * ChatModel overrides.
	 */

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Override
	public Integer getMaxTokens() {
		return this.maxTokens;
	}

	@Override
	public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	@Override
	public List<String> getStopSequences() {
		return this.stop;
	}

	@Override
	public Double getTemperature() {
		return this.temperature;
	}

	@Override
	public Integer getTopK() {
		return this.topK;
	}

	@Override
	public Double getTopP() {
		return this.topP;
	}

	@Override
	@SuppressWarnings("unchecked")
	public ChatOptions copy() {
		return fromOptions(this);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.maxTokens, this.compartment, this.servingMode, this.preambleOverride,
				this.temperature, this.topP, this.topK, this.stop, this.frequencyPenalty, this.presencePenalty,
				this.documents, this.tools);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		OCICohereChatOptions that = (OCICohereChatOptions) o;

		return Objects.equals(this.model, that.model) && Objects.equals(this.maxTokens, that.maxTokens)
				&& Objects.equals(this.compartment, that.compartment)
				&& Objects.equals(this.servingMode, that.servingMode)
				&& Objects.equals(this.preambleOverride, that.preambleOverride)
				&& Objects.equals(this.temperature, that.temperature) && Objects.equals(this.topP, that.topP)
				&& Objects.equals(this.topK, that.topK) && Objects.equals(this.stop, that.stop)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.presencePenalty, that.presencePenalty)
				&& Objects.equals(this.documents, that.documents) && Objects.equals(this.tools, that.tools);
	}

	public static class Builder {

		protected OCICohereChatOptions chatOptions;

		public Builder() {
			this.chatOptions = new OCICohereChatOptions();
		}

		public Builder(OCICohereChatOptions chatOptions) {
			this.chatOptions = chatOptions;
		}

		public Builder model(String model) {
			this.chatOptions.model = model;
			return this;
		}

		public Builder maxTokens(Integer maxTokens) {
			this.chatOptions.maxTokens = maxTokens;
			return this;
		}

		public Builder compartment(String compartment) {
			this.chatOptions.compartment = compartment;
			return this;
		}

		public Builder servingMode(String servingMode) {
			this.chatOptions.servingMode = servingMode;
			return this;
		}

		public Builder preambleOverride(String preambleOverride) {
			this.chatOptions.preambleOverride = preambleOverride;
			return this;
		}

		public Builder temperature(Double temperature) {
			this.chatOptions.temperature = temperature;
			return this;
		}

		public Builder topP(Double topP) {
			this.chatOptions.topP = topP;
			return this;
		}

		public Builder topK(Integer topK) {
			this.chatOptions.topK = topK;
			return this;
		}

		public Builder frequencyPenalty(Double frequencyPenalty) {
			this.chatOptions.frequencyPenalty = frequencyPenalty;
			return this;
		}

		public Builder presencePenalty(Double presencePenalty) {
			this.chatOptions.presencePenalty = presencePenalty;
			return this;
		}

		public Builder stop(List<String> stop) {
			this.chatOptions.stop = stop;
			return this;
		}

		public Builder documents(List<Object> documents) {
			this.chatOptions.documents = documents;
			return this;
		}

		public Builder tools(List<CohereTool> tools) {
			this.chatOptions.tools = tools;
			return this;
		}

		public OCICohereChatOptions build() {
			return this.chatOptions;
		}

	}

}
