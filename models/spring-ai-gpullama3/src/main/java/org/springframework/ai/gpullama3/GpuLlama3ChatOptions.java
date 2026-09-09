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

package org.springframework.ai.gpullama3;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.prompt.ChatOptions;

/**
 * Chat options for the GPULlama3 Spring AI chat model.
 * <p>
 * This class implements Spring AI's {@link ChatOptions} contract while also carrying
 * GPULlama3-specific options such as model path, GPU execution mode, context length, and
 * sampling seed.
 * </p>
 *
 * <p>
 * It separates load-time options from request-level options. Load-time options such as
 * {@code modelPath}, {@code onGpu}, and {@code contextLength} are fixed after the model
 * is loaded and cannot be changed per request. Request-level options such as
 * {@code maxTokens}, {@code temperature}, {@code topP}, and {@code seed} may be
 * overridden by runtime options supplied in a {@code Prompt}.
 * </p>
 *
 * <p>
 * Some Spring AI portable options are retained for API compatibility even though
 * GPULlama3 does not currently support them directly, including {@code stopSequences},
 * {@code topK}, {@code frequencyPenalty}, and {@code presencePenalty}.
 * </p>
 *
 * @since 2.0.1
 */
public final class GpuLlama3ChatOptions implements ChatOptions {

	private final @Nullable Path modelPath; // load .gguf file

	private final @Nullable String model;

	private final @Nullable Boolean onGpu;

	private final @Nullable Integer contextLength;

	private final @Nullable Integer maxTokens;

	private final @Nullable Double temperature;

	private final @Nullable Double topP;

	private final @Nullable Long seed;

	/**
	 * Spring AI compatibility options currently ignored by GPULlama3.
	 */
	private final @Nullable List<String> stopSequences;

	private final @Nullable Integer topK;

	private final @Nullable Double frequencyPenalty;

	private final @Nullable Double presencePenalty;

	private GpuLlama3ChatOptions(Builder builder) {
		this.modelPath = builder.modelPath;
		this.model = builder.model;
		this.onGpu = builder.onGpu;
		this.contextLength = builder.contextLength;
		this.maxTokens = builder.maxTokens;
		this.temperature = builder.temperature;
		this.topP = builder.topP;
		this.seed = builder.seed;
		this.stopSequences = copyList(builder.stopSequences);
		this.topK = builder.topK;
		this.frequencyPenalty = builder.frequencyPenalty;
		this.presencePenalty = builder.presencePenalty;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public Builder mutate() {
		return new Builder(this);
	}

	@Nullable public Path getModelPath() {
		return this.modelPath;
	}

	@Override
	@Nullable public String getModel() {
		return this.model;
	}

	@Nullable public Boolean getOnGpu() {
		return this.onGpu;
	}

	@Nullable public Integer getContextLength() {
		return this.contextLength;
	}

	@Override
	@Nullable public Integer getMaxTokens() {
		return this.maxTokens;
	}

	@Override
	@Nullable public Double getTemperature() {
		return this.temperature;
	}

	@Override
	@Nullable public Double getTopP() {
		return this.topP;
	}

	@Nullable public Long getSeed() {
		return this.seed;
	}

	@Override
	@Nullable public List<String> getStopSequences() {
		return this.stopSequences;
	}

	@Override
	@Nullable public Integer getTopK() {
		return this.topK;
	}

	@Override
	@Nullable public Double getFrequencyPenalty() {
		return this.frequencyPenalty;
	}

	@Override
	@Nullable public Double getPresencePenalty() {
		return this.presencePenalty;
	}

	@SuppressWarnings("unchecked")
	public <T extends ChatOptions> T copy() {
		return (T) mutate().build();
	}

	/**
	 * Merges default chat options with options supplied on a single request.
	 *
	 * <p>
	 * Non-null request-level options override the defaults. Load-time options, such as
	 * {@code modelPath}, {@code onGpu}, and {@code contextLength}, must stay unchanged
	 * because the GPULlama3 runtime has already been loaded with them.
	 * </p>
	 * @param runtimeOptions options attached to the current {@code Prompt}, or
	 * {@code null}
	 * @return effective options for the current request
	 */
	public GpuLlama3ChatOptions mergeWithRuntimeOptions(@Nullable ChatOptions runtimeOptions) {
		if (runtimeOptions == null) {
			return this;
		}

		Builder builder = mutate();

		if (runtimeOptions.getMaxTokens() != null) {
			builder.maxTokens(runtimeOptions.getMaxTokens());
		}
		if (runtimeOptions.getTemperature() != null) {
			builder.temperature(runtimeOptions.getTemperature());
		}
		if (runtimeOptions.getTopP() != null) {
			builder.topP(runtimeOptions.getTopP());
		}
		if (runtimeOptions.getStopSequences() != null) {
			builder.stopSequences(runtimeOptions.getStopSequences());
		}
		if (runtimeOptions.getTopK() != null) {
			builder.topK(runtimeOptions.getTopK());
		}
		if (runtimeOptions.getFrequencyPenalty() != null) {
			builder.frequencyPenalty(runtimeOptions.getFrequencyPenalty());
		}
		if (runtimeOptions.getPresencePenalty() != null) {
			builder.presencePenalty(runtimeOptions.getPresencePenalty());
		}

		if (runtimeOptions instanceof GpuLlama3ChatOptions gpuLlama3RuntimeOptions) {
			mergeProviderSpecificOptions(builder, gpuLlama3RuntimeOptions);
		}

		return builder.build();
	}

	/**
	 * Validates load-time options and merges provider-specific request options.
	 */
	private void mergeProviderSpecificOptions(Builder builder, GpuLlama3ChatOptions runtimeOptions) {
		if (runtimeOptions.modelPath != null && !Objects.equals(this.modelPath, runtimeOptions.modelPath)) {
			throw new IllegalArgumentException("modelPath is a load-time option and cannot be changed per request");
		}
		if (runtimeOptions.onGpu != null && !Objects.equals(this.onGpu, runtimeOptions.onGpu)) {
			throw new IllegalArgumentException("onGpu is a load-time option and cannot be changed per request");
		}
		if (runtimeOptions.contextLength != null && !Objects.equals(this.contextLength, runtimeOptions.contextLength)) {
			throw new IllegalArgumentException("contextLength is a load-time option and cannot be changed per request");
		}
		if (runtimeOptions.seed != null) {
			builder.seed(runtimeOptions.seed);
		}
	}

	private static @Nullable List<String> copyList(@Nullable List<String> values) {
		return values == null ? null : List.copyOf(values);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof GpuLlama3ChatOptions that)) {
			return false;
		}
		return Objects.equals(this.modelPath, that.modelPath) && Objects.equals(this.model, that.model)
				&& Objects.equals(this.onGpu, that.onGpu) && Objects.equals(this.contextLength, that.contextLength)
				&& Objects.equals(this.maxTokens, that.maxTokens) && Objects.equals(this.temperature, that.temperature)
				&& Objects.equals(this.topP, that.topP) && Objects.equals(this.seed, that.seed)
				&& Objects.equals(this.stopSequences, that.stopSequences) && Objects.equals(this.topK, that.topK)
				&& Objects.equals(this.frequencyPenalty, that.frequencyPenalty)
				&& Objects.equals(this.presencePenalty, that.presencePenalty);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.modelPath, this.model, this.onGpu, this.contextLength, this.maxTokens,
				this.temperature, this.topP, this.seed, this.stopSequences, this.topK, this.frequencyPenalty,
				this.presencePenalty);
	}

	@Override
	public String toString() {
		return "GpuLlama3ChatOptions{" + "modelPath=" + this.modelPath + ", model='" + this.model + '\'' + ", onGpu="
				+ this.onGpu + ", contextLength=" + this.contextLength + ", maxTokens=" + this.maxTokens
				+ ", temperature=" + this.temperature + ", topP=" + this.topP + ", seed=" + this.seed
				+ ", stopSequences=" + this.stopSequences + ", topK=" + this.topK + ", frequencyPenalty="
				+ this.frequencyPenalty + ", presencePenalty=" + this.presencePenalty + '}';
	}

	/**
	 * Implementation of the Builder pattern for GpuLlama3ChatOptions
	 *
	 * @since 2.0.1
	 */
	public static final class Builder implements ChatOptions.Builder<Builder> {

		private @Nullable Path modelPath;

		private @Nullable String model;

		private @Nullable Boolean onGpu;

		private @Nullable Integer contextLength;

		private @Nullable Integer maxTokens;

		private @Nullable Double temperature;

		private @Nullable Double topP;

		private @Nullable Long seed;

		private @Nullable List<String> stopSequences;

		private @Nullable Integer topK;

		private @Nullable Double frequencyPenalty;

		private @Nullable Double presencePenalty;

		private Builder() {
		}

		private Builder(GpuLlama3ChatOptions options) {
			this.modelPath = options.modelPath;
			this.model = options.model;
			this.onGpu = options.onGpu;
			this.contextLength = options.contextLength;
			this.maxTokens = options.maxTokens;
			this.temperature = options.temperature;
			this.topP = options.topP;
			this.seed = options.seed;
			this.stopSequences = options.stopSequences;
			this.topK = options.topK;
			this.frequencyPenalty = options.frequencyPenalty;
			this.presencePenalty = options.presencePenalty;
		}

		@Override
		public Builder clone() {
			return new Builder(build());
		}

		public Builder modelPath(@Nullable Path modelPath) {
			this.modelPath = modelPath;
			return this;
		}

		@Override
		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder onGpu(@Nullable Boolean onGpu) {
			this.onGpu = onGpu;
			return this;
		}

		public Builder contextLength(@Nullable Integer contextLength) {
			this.contextLength = contextLength;
			return this;
		}

		@Override
		public Builder maxTokens(@Nullable Integer maxTokens) {
			this.maxTokens = maxTokens;
			return this;
		}

		@Override
		public Builder temperature(@Nullable Double temperature) {
			this.temperature = temperature;
			return this;
		}

		@Override
		public Builder topP(@Nullable Double topP) {
			this.topP = topP;
			return this;
		}

		public Builder seed(@Nullable Long seed) {
			this.seed = seed;
			return this;
		}

		@Override
		public Builder stopSequences(@Nullable List<String> stopSequences) {
			this.stopSequences = copyList(stopSequences);
			return this;
		}

		@Override
		public Builder topK(@Nullable Integer topK) {
			this.topK = topK;
			return this;
		}

		@Override
		public Builder frequencyPenalty(@Nullable Double frequencyPenalty) {
			this.frequencyPenalty = frequencyPenalty;
			return this;
		}

		@Override
		public Builder presencePenalty(@Nullable Double presencePenalty) {
			this.presencePenalty = presencePenalty;
			return this;
		}

		@Override
		public GpuLlama3ChatOptions build() {
			return new GpuLlama3ChatOptions(this);
		}

		@Override
		public Builder combineWith(ChatOptions.Builder<?> other) {
			ChatOptions otherOptions = other.build();
			if (otherOptions.getModel() != null) {
				this.model = otherOptions.getModel();
			}
			if (otherOptions.getMaxTokens() != null) {
				this.maxTokens = otherOptions.getMaxTokens();
			}
			if (otherOptions.getTemperature() != null) {
				this.temperature = otherOptions.getTemperature();
			}
			if (otherOptions.getTopP() != null) {
				this.topP = otherOptions.getTopP();
			}
			if (otherOptions.getStopSequences() != null) {
				this.stopSequences = copyList(otherOptions.getStopSequences());
			}
			if (otherOptions.getTopK() != null) {
				this.topK = otherOptions.getTopK();
			}
			if (otherOptions.getFrequencyPenalty() != null) {
				this.frequencyPenalty = otherOptions.getFrequencyPenalty();
			}
			if (otherOptions.getPresencePenalty() != null) {
				this.presencePenalty = otherOptions.getPresencePenalty();
			}
			if (otherOptions instanceof GpuLlama3ChatOptions gpuLlama3Options) {
				combineProviderSpecificOptions(gpuLlama3Options);
			}
			return this;
		}

		private void combineProviderSpecificOptions(GpuLlama3ChatOptions otherOptions) {
			if (otherOptions.modelPath != null) {
				this.modelPath = otherOptions.modelPath;
			}
			if (otherOptions.onGpu != null) {
				this.onGpu = otherOptions.onGpu;
			}
			if (otherOptions.contextLength != null) {
				this.contextLength = otherOptions.contextLength;
			}
			if (otherOptions.seed != null) {
				this.seed = otherOptions.seed;
			}
		}

	}

}
