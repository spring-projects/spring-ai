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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CompositeModelCapabilitiesProviderTests {

	@Test
	void returnsFirstMatchingCapabilities() {
		ModelCapabilities first = ModelCapabilities.builder()
			.contextWindowTokens(128000)
			.supportedFeatures(Set.of(ModelFeature.STREAMING))
			.build();
		ModelCapabilities second = ModelCapabilities.builder().contextWindowTokens(256000).build();
		CompositeModelCapabilitiesProvider provider = new CompositeModelCapabilitiesProvider(
				List.of(request -> Optional.empty(), request -> Optional.of(first), request -> Optional.of(second)));

		Optional<ModelCapabilities> capabilities = provider
			.getCapabilities(new ModelCapabilitiesRequest("openai", "gpt-4.1"));

		assertThat(capabilities).containsSame(first);
	}

	@Test
	void returnsEmptyWhenNoProviderMatches() {
		CompositeModelCapabilitiesProvider provider = new CompositeModelCapabilitiesProvider(
				List.of(request -> Optional.empty()));

		Optional<ModelCapabilities> capabilities = provider
			.getCapabilities(new ModelCapabilitiesRequest("openai", "unknown"));

		assertThat(capabilities).isEmpty();
	}

	@Test
	void exposesImmutableProviderList() {
		CompositeModelCapabilitiesProvider provider = new CompositeModelCapabilitiesProvider(
				List.of(request -> Optional.empty()));

		assertThat(provider.getProviders()).isUnmodifiable();
	}

	@Test
	void rejectsNullProviderElements() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new CompositeModelCapabilitiesProvider(Collections.singletonList(null)))
			.withMessageContaining("providers");
	}

}
