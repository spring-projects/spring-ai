/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.vectorstore.weaviate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link WeaviateVectorStoreOptions}.
 *
 * @author Jonghoon Park
 */
class WeaviateVectorStoreOptionsTests {

	@Test
	void shouldPassWithValidInputs() {
		WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();
		options.setObjectClass("CustomObjectClass");
		options.setContentFieldName("customContentFieldName");
	}

	@Test
	void shouldFailWithNullObjectClass() {
		WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();

		assertThatThrownBy(() -> options.setObjectClass(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("objectClass cannot be null or empty");
	}

	@Test
	void shouldFailWithEmptyObjectClass() {
		WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();

		assertThatThrownBy(() -> options.setObjectClass("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("objectClass cannot be null or empty");
	}

	@Test
	void shouldFailWithNullContentFieldName() {
		WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();

		assertThatThrownBy(() -> options.setContentFieldName(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("contentFieldName cannot be null or empty");
	}

	@Test
	void shouldFailWithEmptyContentFieldName() {
		WeaviateVectorStoreOptions options = new WeaviateVectorStoreOptions();

		assertThatThrownBy(() -> options.setContentFieldName("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("contentFieldName cannot be null or empty");
	}

}
