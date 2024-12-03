/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.springframework.ai.assistant;

import java.util.Map;

/**
 * A builder class for creating instances of AssistantOptions. Use the builder() method to
 * obtain a new instance of AssistantOptionsBuilder. Use the various with*() methods to
 * set the fields for the assistant, such as model, name, description, and instructions.
 *
 * @author Alexandros Pappas
 */
public final class AssistantOptionsBuilder {

	private final DefaultAssistantModelOptions options = new DefaultAssistantModelOptions();

	private AssistantOptionsBuilder() {
	}

	public static AssistantOptionsBuilder builder() {
		return new AssistantOptionsBuilder();
	}

	public AssistantOptionsBuilder withModel(String model) {
		this.options.setModel(model);
		return this;
	}

	public AssistantOptionsBuilder withName(String name) {
		this.options.setName(name);
		return this;
	}

	public AssistantOptionsBuilder withDescription(String description) {
		this.options.setDescription(description);
		return this;
	}

	public AssistantOptionsBuilder withInstructions(String instructions) {
		this.options.setInstructions(instructions);
		return this;
	}

	public AssistantOptionsBuilder withMetadata(Map<String, String> metadata) {
		this.options.setMetadata(metadata);
		return this;
	}

	public AssistantOptionsBuilder withTemperature(Double temperature) {
		this.options.setTemperature(temperature);
		return this;
	}

	public AssistantOptionsBuilder withTopP(Double topP) {
		this.options.setTopP(topP);
		return this;
	}

	public AssistantOptions build() {
		return this.options;
	}

	/**
	 * Default implementation of AssistantOptions for the builder.
	 */
	private static class DefaultAssistantModelOptions implements AssistantOptions {

		private String model;

		private String name;

		private String description;

		private String instructions;

		private Map<String, String> metadata;

		private Double temperature;

		private Double topP;

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

	}

}
