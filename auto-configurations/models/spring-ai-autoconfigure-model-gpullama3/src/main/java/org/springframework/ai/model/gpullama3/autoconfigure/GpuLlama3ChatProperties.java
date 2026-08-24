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

package org.springframework.ai.model.gpullama3.autoconfigure;

import java.nio.file.Path;
import java.util.List;

import org.springframework.ai.gpullama3.GpuLlama3ChatOptions;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

/**
 * Configuration properties for the GPULlama3 chat model.
 */
@ConfigurationProperties(GpuLlama3ChatProperties.CONFIG_PREFIX)
public class GpuLlama3ChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.gpullama3.chat";

	public static final String MODEL_TYPE = SpringAIModels.GPULLAMA3;

	private boolean enabled = true;

	private Path modelPath;

	private String model;

	private Boolean onGpu = false;

	private Integer contextLength = 2048;

	private Options options = new Options();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Path getModelPath() {
		return this.modelPath;
	}

	public void setModelPath(Path modelPath) {
		this.modelPath = modelPath;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Boolean getOnGpu() {
		return this.onGpu;
	}

	public void setOnGpu(Boolean onGpu) {
		this.onGpu = onGpu;
	}

	public Integer getContextLength() {
		return this.contextLength;
	}

	public void setContextLength(Integer contextLength) {
		this.contextLength = contextLength;
	}

	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = (options != null) ? options : new Options();
	}

	public GpuLlama3ChatOptions toOptions() {
		Assert.notNull(this.modelPath, CONFIG_PREFIX + ".model-path must be set");
		Assert.isTrue(this.contextLength != null && this.contextLength > 0,
				CONFIG_PREFIX + ".context-length must be positive");
		Assert.isTrue(this.options.getMaxTokens() == null || this.options.getMaxTokens() > 0,
				CONFIG_PREFIX + ".options.max-tokens must be positive");

		return GpuLlama3ChatOptions.builder()
			.modelPath(this.modelPath)
			.model(this.model)
			.onGpu(this.onGpu)
			.contextLength(this.contextLength)
			.maxTokens(this.options.getMaxTokens())
			.temperature(this.options.getTemperature())
			.topP(this.options.getTopP())
			.seed(this.options.getSeed())
			.stopSequences(this.options.getStopSequences())
			.topK(this.options.getTopK())
			.frequencyPenalty(this.options.getFrequencyPenalty())
			.presencePenalty(this.options.getPresencePenalty())
			.build();
	}

	public static class Options {

		private Integer maxTokens = 512;

		private Double temperature = 0.1;

		private Double topP = 1.0;

		private Long seed = 12345L;

		private List<String> stopSequences;

		private Integer topK;

		private Double frequencyPenalty;

		private Double presencePenalty;

		public Integer getMaxTokens() {
			return this.maxTokens;
		}

		public void setMaxTokens(Integer maxTokens) {
			this.maxTokens = maxTokens;
		}

		public Double getTemperature() {
			return this.temperature;
		}

		public void setTemperature(Double temperature) {
			this.temperature = temperature;
		}

		public Double getTopP() {
			return this.topP;
		}

		public void setTopP(Double topP) {
			this.topP = topP;
		}

		public Long getSeed() {
			return this.seed;
		}

		public void setSeed(Long seed) {
			this.seed = seed;
		}

		public List<String> getStopSequences() {
			return this.stopSequences;
		}

		public void setStopSequences(List<String> stopSequences) {
			this.stopSequences = stopSequences;
		}

		public Integer getTopK() {
			return this.topK;
		}

		public void setTopK(Integer topK) {
			this.topK = topK;
		}

		public Double getFrequencyPenalty() {
			return this.frequencyPenalty;
		}

		public void setFrequencyPenalty(Double frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
		}

		public Double getPresencePenalty() {
			return this.presencePenalty;
		}

		public void setPresencePenalty(Double presencePenalty) {
			this.presencePenalty = presencePenalty;
		}

	}

}
