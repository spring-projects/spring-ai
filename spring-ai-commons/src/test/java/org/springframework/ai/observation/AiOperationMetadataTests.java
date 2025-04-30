/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.observation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AiOperationMetadata}.
 *
 * @author Thomas Vitale
 */
class AiOperationMetadataTests {

	@Test
	void whenMandatoryMetadataThenReturn() {
		var operationMetadata = AiOperationMetadata.builder().operationType("chat").provider("doofenshmirtz").build();

		assertThat(operationMetadata).isNotNull();
	}

	@Test
	void whenOperationTypeIsNullThenThrow() {
		assertThatThrownBy(() -> AiOperationMetadata.builder().provider("doofenshmirtz").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("operationType cannot be null or empty");
	}

	@Test
	void whenOperationTypeIsEmptyThenThrow() {
		assertThatThrownBy(() -> AiOperationMetadata.builder().operationType("").provider("doofenshmirtz").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("operationType cannot be null or empty");
	}

	@Test
	void whenProviderIsNullThenThrow() {
		assertThatThrownBy(() -> AiOperationMetadata.builder().operationType("chat").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("provider cannot be null or empty");
	}

	@Test
	void whenProviderIsEmptyThenThrow() {
		assertThatThrownBy(() -> AiOperationMetadata.builder().operationType("chat").provider("").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("provider cannot be null or empty");
	}

}
