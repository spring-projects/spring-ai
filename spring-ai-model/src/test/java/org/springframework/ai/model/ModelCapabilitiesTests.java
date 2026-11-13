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
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ModelCapabilitiesTests {

	@Test
	void unknownValuesRemainNull() {
		ModelCapabilities capabilities = ModelCapabilities.builder().build();

		assertThat(capabilities.getContextWindowTokens()).isNull();
		assertThat(capabilities.getMaxOutputTokens()).isNull();
		assertThat(capabilities.getSupportedFeatures()).isEmpty();
		assertThat(capabilities.getMetadata()).isEmpty();
	}

	@Test
	void exposesKnownLimitsFeaturesAndMetadata() {
		ModelCapabilities capabilities = ModelCapabilities.builder()
			.contextWindowTokens(128000)
			.maxOutputTokens(16000)
			.supportedFeatures(Set.of(ModelFeature.TOOL_CALLING, ModelFeature.STREAMING))
			.metadata(Map.of("providerModelId", "gpt-4.1"))
			.build();

		assertThat(capabilities.getContextWindowTokens()).isEqualTo(128000);
		assertThat(capabilities.getMaxOutputTokens()).isEqualTo(16000);
		assertThat(capabilities.supports(ModelFeature.TOOL_CALLING)).isTrue();
		assertThat(capabilities.supports(ModelFeature.REASONING)).isFalse();
		assertThat(capabilities.getMetadata()).containsEntry("providerModelId", "gpt-4.1");
	}

	@Test
	void exposesImmutableCollections() {
		ModelCapabilities capabilities = ModelCapabilities.builder()
			.supportedFeatures(Set.of(ModelFeature.STREAMING))
			.metadata(Map.of("providerModelId", "gpt-4.1"))
			.build();

		assertThat(capabilities.getSupportedFeatures()).isUnmodifiable();
		assertThat(capabilities.getMetadata()).isUnmodifiable();
	}

	@Test
	void rejectsNonPositiveLimits() {
		assertThatIllegalArgumentException().isThrownBy(() -> ModelCapabilities.builder().contextWindowTokens(0))
			.withMessageContaining("contextWindowTokens");
		assertThatIllegalArgumentException().isThrownBy(() -> ModelCapabilities.builder().maxOutputTokens(-1))
			.withMessageContaining("maxOutputTokens");
	}

	@Test
	void supportsRequiresFeature() {
		ModelCapabilities capabilities = ModelCapabilities.builder().build();

		assertThatIllegalArgumentException().isThrownBy(() -> capabilities.supports(null))
			.withMessageContaining("feature");
	}

}
