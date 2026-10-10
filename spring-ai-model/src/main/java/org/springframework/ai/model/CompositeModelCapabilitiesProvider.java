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

import java.util.List;
import java.util.Optional;

import org.springframework.util.Assert;

/**
 * Composite {@link ModelCapabilitiesProvider} that queries providers in order and returns
 * the first matching capabilities.
 *
 * @author Jewoo Shin
 * @since 2.0.1
 */
public final class CompositeModelCapabilitiesProvider implements ModelCapabilitiesProvider {

	private final List<ModelCapabilitiesProvider> providers;

	/**
	 * Create a composite provider that queries the given providers in iteration order.
	 * @param providers the providers to query
	 */
	public CompositeModelCapabilitiesProvider(List<ModelCapabilitiesProvider> providers) {
		Assert.notNull(providers, "providers cannot be null");
		Assert.noNullElements(providers, "providers cannot contain null elements");
		this.providers = List.copyOf(providers);
	}

	@Override
	public Optional<ModelCapabilities> getCapabilities(ModelCapabilitiesRequest request) {
		Assert.notNull(request, "request cannot be null");
		return this.providers.stream()
			.map(provider -> provider.getCapabilities(request))
			.filter(Optional::isPresent)
			.map(Optional::get)
			.findFirst();
	}

	/**
	 * Return the providers queried by this composite.
	 * @return the providers in query order
	 */
	public List<ModelCapabilitiesProvider> getProviders() {
		return this.providers;
	}

}
