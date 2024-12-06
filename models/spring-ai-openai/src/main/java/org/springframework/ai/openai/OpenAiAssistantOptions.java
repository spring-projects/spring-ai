/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.assistant.AssistantOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.assistants.ResponseFormat;
import org.springframework.ai.openai.api.assistants.tools.Tool;
import org.springframework.ai.openai.api.assistants.tools.ToolResources;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI Assistant API options. Represents the configuration options for creating and
 * managing assistants via OpenAI API.
 *
 * @author Alexandros Pappas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiAssistantOptions implements AssistantOptions {

	@JsonProperty("model")
	private String model;

	@JsonProperty("name")
	private String name;

	@JsonProperty("description")
	private String description;

	@JsonProperty("instructions")
	private String instructions;

	@JsonProperty("tools")
	private List<Tool> tools;

	@JsonProperty("tool_resources")
	private ToolResources toolResources;

	@JsonProperty("metadata")
	private Map<String, String> metadata;

	@JsonProperty("temperature")
	private Double temperature;

	@JsonProperty("top_p")
	private Double topP;

	@JsonProperty("response_format")
	private ResponseFormat responseFormat;

	public static Builder builder() {
		return new Builder();
	}

	public static OpenAiAssistantOptions fromOptions(OpenAiAssistantOptions fromOptions) {
		return OpenAiAssistantOptions.builder()
			.withModel(fromOptions.getModel())
			.withName(fromOptions.getName())
			.withDescription(fromOptions.getDescription())
			.withInstructions(fromOptions.getInstructions())
			.withTools(fromOptions.getTools())
			.withToolResources(fromOptions.getToolResources())
			.withMetadata(fromOptions.getMetadata())
			.withTemperature(fromOptions.getTemperature())
			.withTopP(fromOptions.getTopP())
			.withResponseFormat(fromOptions.getResponseFormat())
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
	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public String getInstructions() {
		return this.instructions;
	}

	public void setInstructions(String instructions) {
		this.instructions = instructions;
	}

	public List<Tool> getTools() {
		return this.tools;
	}

	public void setTools(List<Tool> tools) {
		this.tools = tools;
	}

	public ToolResources getToolResources() {
		return this.toolResources;
	}

	public void setToolResources(ToolResources toolResources) {
		this.toolResources = toolResources;
	}

	@Override
	public Map<String, String> getMetadata() {
		return this.metadata;
	}

	public void setMetadata(Map<String, String> metadata) {
		this.metadata = metadata;
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

	public ResponseFormat getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(ResponseFormat responseFormat) {
		this.responseFormat = responseFormat;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		OpenAiAssistantOptions other = (OpenAiAssistantOptions) o;
		return Objects.equals(this.model, other.model) && Objects.equals(this.name, other.name)
				&& Objects.equals(this.description, other.description)
				&& Objects.equals(this.instructions, other.instructions) && Objects.equals(this.tools, other.tools)
				&& Objects.equals(this.toolResources, other.toolResources)
				&& Objects.equals(this.metadata, other.metadata) && Objects.equals(this.temperature, other.temperature)
				&& Objects.equals(this.topP, other.topP) && Objects.equals(this.responseFormat, other.responseFormat);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.name, this.description, this.instructions, this.tools, this.toolResources,
				this.metadata, this.temperature, this.topP, this.responseFormat);
	}

	@Override
	public String toString() {
		return "OpenAiAssistantOptions: " + ModelOptionsUtils.toJsonString(this);
	}

	public static final class Builder {

		private final OpenAiAssistantOptions options;

		private Builder() {
			this.options = new OpenAiAssistantOptions();
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder withName(String name) {
			this.options.setName(name);
			return this;
		}

		public Builder withDescription(String description) {
			this.options.setDescription(description);
			return this;
		}

		public Builder withInstructions(String instructions) {
			this.options.setInstructions(instructions);
			return this;
		}

		public Builder withTools(List<Tool> tools) {
			this.options.setTools(tools);
			return this;
		}

		public Builder withToolResources(ToolResources toolResources) {
			this.options.setToolResources(toolResources);
			return this;
		}

		public Builder withMetadata(Map<String, String> metadata) {
			this.options.setMetadata(metadata);
			return this;
		}

		public Builder withTemperature(Double temperature) {
			this.options.setTemperature(temperature);
			return this;
		}

		public Builder withTopP(Double topP) {
			this.options.setTopP(topP);
			return this;
		}

		public Builder withResponseFormat(ResponseFormat responseFormat) {
			this.options.setResponseFormat(responseFormat);
			return this;
		}

		public OpenAiAssistantOptions build() {
			return this.options;
		}

	}

}
