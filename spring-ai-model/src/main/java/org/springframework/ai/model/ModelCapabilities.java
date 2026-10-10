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

package org.springframework.ai.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Runtime metadata describing known capabilities and limits for a model.
 * <p>
 * Nullable values are unknown and should not be interpreted as unsupported.
 *
 * @author Jewoo Shin
 * @since 2.0.1
 */
public final class ModelCapabilities {

	private final @Nullable Integer contextWindowTokens;

	private final @Nullable Integer maxOutputTokens;

	private final Set<ModelFeature> supportedFeatures;

	private final Map<String, Object> metadata;

	private ModelCapabilities(Builder builder) {
		this.contextWindowTokens = builder.contextWindowTokens;
		this.maxOutputTokens = builder.maxOutputTokens;
		this.supportedFeatures = builder.supportedFeatures.isEmpty() ? Set.of() : Set.copyOf(builder.supportedFeatures);
		this.metadata = Map.copyOf(builder.metadata);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Return the model context window size in tokens, or {@code null} when unknown.
	 * @return the context window size in tokens
	 */
	public @Nullable Integer getContextWindowTokens() {
		return this.contextWindowTokens;
	}

	/**
	 * Return the maximum output size in tokens, or {@code null} when unknown.
	 * @return the maximum output size in tokens
	 */
	public @Nullable Integer getMaxOutputTokens() {
		return this.maxOutputTokens;
	}

	/**
	 * Return the features known to be supported by the model. A missing feature means
	 * that support is not advertised by this metadata.
	 * @return the supported features
	 */
	public Set<ModelFeature> getSupportedFeatures() {
		return this.supportedFeatures;
	}

	/**
	 * Return whether this metadata advertises support for the given feature.
	 * @param feature the feature to check
	 * @return {@code true} when the feature is known to be supported
	 */
	public boolean supports(ModelFeature feature) {
		Assert.notNull(feature, "feature cannot be null");
		return this.supportedFeatures.contains(feature);
	}

	/**
	 * Return provider-specific metadata associated with these capabilities.
	 * @return the provider-specific metadata
	 */
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ModelCapabilities that)) {
			return false;
		}
		return Objects.equals(this.contextWindowTokens, that.contextWindowTokens)
				&& Objects.equals(this.maxOutputTokens, that.maxOutputTokens)
				&& this.supportedFeatures.equals(that.supportedFeatures) && this.metadata.equals(that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.contextWindowTokens, this.maxOutputTokens, this.supportedFeatures, this.metadata);
	}

	@Override
	public String toString() {
		return "ModelCapabilities{" + "contextWindowTokens=" + this.contextWindowTokens + ", maxOutputTokens="
				+ this.maxOutputTokens + ", supportedFeatures=" + this.supportedFeatures + ", metadata=" + this.metadata
				+ '}';
	}

	public static final class Builder {

		private @Nullable Integer contextWindowTokens;

		private @Nullable Integer maxOutputTokens;

		private Set<ModelFeature> supportedFeatures = EnumSet.noneOf(ModelFeature.class);

		private Map<String, Object> metadata = Map.of();

		private Builder() {
		}

		/**
		 * Set the model context window size in tokens.
		 * @param contextWindowTokens the context window size in tokens, or {@code null}
		 * when unknown
		 * @return this builder
		 */
		public Builder contextWindowTokens(@Nullable Integer contextWindowTokens) {
			assertPositive(contextWindowTokens, "contextWindowTokens");
			this.contextWindowTokens = contextWindowTokens;
			return this;
		}

		/**
		 * Set the maximum output size in tokens.
		 * @param maxOutputTokens the maximum output size in tokens, or {@code null} when
		 * unknown
		 * @return this builder
		 */
		public Builder maxOutputTokens(@Nullable Integer maxOutputTokens) {
			assertPositive(maxOutputTokens, "maxOutputTokens");
			this.maxOutputTokens = maxOutputTokens;
			return this;
		}

		/**
		 * Set the features known to be supported by the model.
		 * @param supportedFeatures the supported features
		 * @return this builder
		 */
		public Builder supportedFeatures(Set<ModelFeature> supportedFeatures) {
			Assert.notNull(supportedFeatures, "supportedFeatures cannot be null");
			this.supportedFeatures = supportedFeatures.isEmpty() ? EnumSet.noneOf(ModelFeature.class)
					: EnumSet.copyOf(supportedFeatures);
			return this;
		}

		/**
		 * Set provider-specific metadata associated with these capabilities.
		 * @param metadata the provider-specific metadata
		 * @return this builder
		 */
		public Builder metadata(Map<String, Object> metadata) {
			Assert.notNull(metadata, "metadata cannot be null");
			this.metadata = Map.copyOf(metadata);
			return this;
		}

		/**
		 * Build the immutable capabilities instance.
		 * @return a new {@link ModelCapabilities}
		 */
		public ModelCapabilities build() {
			return new ModelCapabilities(this);
		}

		private static void assertPositive(@Nullable Integer value, String name) {
			Assert.isTrue(value == null || value > 0, name + " must be greater than zero");
		}

	}

}
