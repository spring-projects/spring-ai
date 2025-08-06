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
import org.junit.jupiter.api.BeforeEach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link WeaviateVectorStoreOptions}.
 *
 * @author Jonghoon Park
 */
class WeaviateVectorStoreOptionsTests {

	private WeaviateVectorStoreOptions options;

	@BeforeEach
	void setUp() {
		options = new WeaviateVectorStoreOptions();
	}

	@Test
	void shouldPassWithValidInputs() {
		options.setObjectClass("CustomObjectClass");
		options.setContentFieldName("customContentFieldName");

		assertThat(options.getObjectClass()).isEqualTo("CustomObjectClass");
		assertThat(options.getContentFieldName()).isEqualTo("customContentFieldName");
	}

	@Test
	void shouldFailWithNullObjectClass() {
		assertThatThrownBy(() -> options.setObjectClass(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("objectClass cannot be null or empty");
	}

	@Test
	void shouldFailWithEmptyObjectClass() {
		assertThatThrownBy(() -> options.setObjectClass("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("objectClass cannot be null or empty");
	}

	@Test
	void shouldFailWithWhitespaceOnlyObjectClass() {
		assertThatThrownBy(() -> options.setObjectClass("   ")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("objectClass cannot be null or empty");
	}

	@Test
	void shouldFailWithNullContentFieldName() {
		assertThatThrownBy(() -> options.setContentFieldName(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("contentFieldName cannot be null or empty");
	}

	@Test
	void shouldFailWithEmptyContentFieldName() {
		assertThatThrownBy(() -> options.setContentFieldName("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("contentFieldName cannot be null or empty");
	}

	@Test
	void shouldFailWithWhitespaceOnlyContentFieldName() {
		assertThatThrownBy(() -> options.setContentFieldName("   ")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("contentFieldName cannot be null or empty");
	}

	@Test
	void shouldFailWithNullMetaFieldPrefix() {
		assertThatThrownBy(() -> options.setMetaFieldPrefix(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("metaFieldPrefix can be empty but not null");
	}

	@Test
	void shouldPassWithEmptyMetaFieldPrefix() {
		options.setMetaFieldPrefix("");
		assertThat(options.getMetaFieldPrefix()).isEqualTo("");
	}

	@Test
	void shouldPassWithValidMetaFieldPrefix() {
		options.setMetaFieldPrefix("meta_");
		assertThat(options.getMetaFieldPrefix()).isEqualTo("meta_");
	}

	@Test
	void shouldPassWithWhitespaceMetaFieldPrefix() {
		options.setMetaFieldPrefix("   ");
		assertThat(options.getMetaFieldPrefix()).isEqualTo("   ");
	}

	@Test
	void shouldHandleDefaultValues() {
		// Test that default constructor sets appropriate defaults
		WeaviateVectorStoreOptions defaultOptions = new WeaviateVectorStoreOptions();

		// Verify getters don't throw exceptions with default state
		// Note: Adjust these assertions based on actual default values in your
		// implementation
		assertThat(defaultOptions.getObjectClass()).isNotNull();
		assertThat(defaultOptions.getContentFieldName()).isNotNull();
		assertThat(defaultOptions.getMetaFieldPrefix()).isNotNull();
	}

	@Test
	void shouldHandleSpecialCharactersInObjectClass() {
		String objectClassWithSpecialChars = "Object_Class-123";
		options.setObjectClass(objectClassWithSpecialChars);
		assertThat(options.getObjectClass()).isEqualTo(objectClassWithSpecialChars);
	}

	@Test
	void shouldHandleSpecialCharactersInContentFieldName() {
		String contentFieldWithSpecialChars = "content_field_name";
		options.setContentFieldName(contentFieldWithSpecialChars);
		assertThat(options.getContentFieldName()).isEqualTo(contentFieldWithSpecialChars);
	}

	@Test
	void shouldHandleSpecialCharactersInMetaFieldPrefix() {
		String metaPrefixWithSpecialChars = "meta-prefix_";
		options.setMetaFieldPrefix(metaPrefixWithSpecialChars);
		assertThat(options.getMetaFieldPrefix()).isEqualTo(metaPrefixWithSpecialChars);
	}

	@Test
	void shouldHandleMultipleSetterCallsOnSameField() {
		options.setObjectClass("FirstObjectClass");
		assertThat(options.getObjectClass()).isEqualTo("FirstObjectClass");

		options.setObjectClass("SecondObjectClass");
		assertThat(options.getObjectClass()).isEqualTo("SecondObjectClass");

		options.setContentFieldName("firstContentField");
		assertThat(options.getContentFieldName()).isEqualTo("firstContentField");

		options.setContentFieldName("secondContentField");
		assertThat(options.getContentFieldName()).isEqualTo("secondContentField");
	}

	@Test
	void shouldPreserveStateAfterPartialSetup() {
		options.setObjectClass("PartialObjectClass");

		// Attempt to set invalid content field
		assertThatThrownBy(() -> options.setContentFieldName(null)).isInstanceOf(IllegalArgumentException.class);

		// Verify object class is still set correctly
		assertThat(options.getObjectClass()).isEqualTo("PartialObjectClass");
	}

	@Test
	void shouldValidateCaseSensitivity() {
		options.setObjectClass("TestClass");
		assertThat(options.getObjectClass()).isEqualTo("TestClass");

		options.setObjectClass("testclass");
		assertThat(options.getObjectClass()).isEqualTo("testclass");
		assertThat(options.getObjectClass()).isNotEqualTo("TestClass");
	}

}
