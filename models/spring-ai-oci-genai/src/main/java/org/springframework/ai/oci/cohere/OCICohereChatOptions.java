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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oracle.bmc.generativeaiinference.model.CohereTool;

import org.springframework.ai.chat.prompt.AbstractChatOptions;
import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * The configuration information for OCI chat requests.
 *
 * @author Anders Swanson
 * @author Ilayaperumal Gopinathan
 * @author Alexandros Pappas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OCICohereChatOptions extends AbstractChatOptions implements ChatOptions {

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
	public List<String> getStopSequences() {
		return this.stop;
	}

	@Override
	@SuppressWarnings("unchecked")
	public OCICohereChatOptions copy() {
		return fromOptions(this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		OCICohereChatOptions that = (OCICohereChatOptions) o;

		if (model != null ? !model.equals(that.model) : that.model != null)
			return false;
		if (maxTokens != null ? !maxTokens.equals(that.maxTokens) : that.maxTokens != null)
			return false;
		if (compartment != null ? !compartment.equals(that.compartment) : that.compartment != null)
			return false;
		if (servingMode != null ? !servingMode.equals(that.servingMode) : that.servingMode != null)
			return false;
		if (preambleOverride != null ? !preambleOverride.equals(that.preambleOverride) : that.preambleOverride != null)
			return false;
		if (temperature != null ? !temperature.equals(that.temperature) : that.temperature != null)
			return false;
		if (topP != null ? !topP.equals(that.topP) : that.topP != null)
			return false;
		if (topK != null ? !topK.equals(that.topK) : that.topK != null)
			return false;
		if (stop != null ? !stop.equals(that.stop) : that.stop != null)
			return false;
		if (frequencyPenalty != null ? !frequencyPenalty.equals(that.frequencyPenalty) : that.frequencyPenalty != null)
			return false;
		if (presencePenalty != null ? !presencePenalty.equals(that.presencePenalty) : that.presencePenalty != null)
			return false;
		if (documents != null ? !documents.equals(that.documents) : that.documents != null)
			return false;
		return tools != null ? tools.equals(that.tools) : that.tools == null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.model == null) ? 0 : this.model.hashCode());
		result = prime * result + ((this.maxTokens == null) ? 0 : this.maxTokens.hashCode());
		result = prime * result + ((this.compartment == null) ? 0 : this.compartment.hashCode());
		result = prime * result + ((this.servingMode == null) ? 0 : this.servingMode.hashCode());
		result = prime * result + ((this.preambleOverride == null) ? 0 : this.preambleOverride.hashCode());
		result = prime * result + ((this.temperature == null) ? 0 : this.temperature.hashCode());
		result = prime * result + ((this.topP == null) ? 0 : this.topP.hashCode());
		result = prime * result + ((this.topK == null) ? 0 : this.topK.hashCode());
		result = prime * result + ((this.stop == null) ? 0 : this.stop.hashCode());
		result = prime * result + ((this.frequencyPenalty == null) ? 0 : this.frequencyPenalty.hashCode());
		result = prime * result + ((this.presencePenalty == null) ? 0 : this.presencePenalty.hashCode());
		result = prime * result + ((this.documents == null) ? 0 : this.documents.hashCode());
		result = prime * result + ((this.tools == null) ? 0 : this.tools.hashCode());
		return result;
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
