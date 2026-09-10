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

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Request object used to look up model capabilities.
 *
 * @author Jewoo Shin
 * @since 2.0.1
 */
public final class ModelCapabilitiesRequest {

	private final String provider;

	private final String model;

	private final Map<String, Object> metadata;

	/**
	 * Create a request for the given provider and model.
	 * @param provider the provider identifier
	 * @param model the model identifier
	 */
	public ModelCapabilitiesRequest(String provider, String model) {
		this(provider, model, Map.of());
	}

	/**
	 * Create a request for the given provider, model, and lookup metadata.
	 * @param provider the provider identifier
	 * @param model the model identifier
	 * @param metadata additional provider-specific lookup metadata
	 */
	public ModelCapabilitiesRequest(String provider, String model, Map<String, Object> metadata) {
		Assert.hasText(provider, "provider cannot be empty");
		Assert.hasText(model, "model cannot be empty");
		Assert.notNull(metadata, "metadata cannot be null");
		this.provider = provider;
		this.model = model;
		this.metadata = Map.copyOf(metadata);
	}

	/**
	 * Return the provider identifier.
	 * @return the provider identifier
	 */
	public String getProvider() {
		return this.provider;
	}

	/**
	 * Return the model identifier.
	 * @return the model identifier
	 */
	public String getModel() {
		return this.model;
	}

	/**
	 * Return provider-specific lookup metadata.
	 * @return provider-specific lookup metadata
	 */
	public Map<String, Object> getMetadata() {
		return this.metadata;
	}

	/**
	 * Return a provider-specific lookup metadata value.
	 * @param key the metadata key
	 * @return the metadata value, or {@code null} when not present
	 */
	public @Nullable Object getMetadata(String key) {
		Assert.notNull(key, "key cannot be null");
		return this.metadata.get(key);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ModelCapabilitiesRequest that)) {
			return false;
		}
		return this.provider.equals(that.provider) && this.model.equals(that.model)
				&& this.metadata.equals(that.metadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.provider, this.model, this.metadata);
	}

	@Override
	public String toString() {
		return "ModelCapabilitiesRequest{" + "provider='" + this.provider + '\'' + ", model='" + this.model + '\''
				+ ", metadata=" + this.metadata + '}';
	}

}
