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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
		this.options = new WeaviateVectorStoreOptions();
	}

	@Test
	void shouldPassWithValidInputs() {
		this.options.setObjectClass("CustomObjectClass");
		this.options.setContentFieldName("customContentFieldName");

		assertThat(this.options.getObjectClass()).isEqualTo("CustomObjectClass");
		assertThat(this.options.getContentFieldName()).isEqualTo("customContentFieldName");
	}

	@Test
	void shouldFailWithNullObjectClass() {
		assertThatThrownBy(() -> this.options.setObjectClass(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("objectClass cannot be null or empty");
	}

	@Test
	void shouldFailWithEmptyObjectClass() {
		assertThatThrownBy(() -> this.options.setObjectClass("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("objectClass cannot be null or empty");
	}

	@Test
	void shouldFailWithWhitespaceOnlyObjectClass() {
		assertThatThrownBy(() -> this.options.setObjectClass("   ")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("objectClass cannot be null or empty");
	}

	@Test
	void shouldFailWithNullContentFieldName() {
		assertThatThrownBy(() -> this.options.setContentFieldName(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("contentFieldName cannot be null or empty");
	}

	@Test
	void shouldFailWithEmptyContentFieldName() {
		assertThatThrownBy(() -> this.options.setContentFieldName("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("contentFieldName cannot be null or empty");
	}

	@Test
	void shouldFailWithWhitespaceOnlyContentFieldName() {
		assertThatThrownBy(() -> this.options.setContentFieldName("   ")).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("contentFieldName cannot be null or empty");
	}

	@Test
	void shouldFailWithNullMetaFieldPrefix() {
		assertThatThrownBy(() -> this.options.setMetaFieldPrefix(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("metaFieldPrefix can be empty but not null");
	}

	@Test
	void shouldPassWithEmptyMetaFieldPrefix() {
		this.options.setMetaFieldPrefix("");
		assertThat(this.options.getMetaFieldPrefix()).isEqualTo("");
	}

	@Test
	void shouldPassWithValidMetaFieldPrefix() {
		this.options.setMetaFieldPrefix("meta_");
		assertThat(this.options.getMetaFieldPrefix()).isEqualTo("meta_");
	}

	@Test
	void shouldPassWithWhitespaceMetaFieldPrefix() {
		this.options.setMetaFieldPrefix("   ");
		assertThat(this.options.getMetaFieldPrefix()).isEqualTo("   ");
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
		this.options.setObjectClass(objectClassWithSpecialChars);
		assertThat(this.options.getObjectClass()).isEqualTo(objectClassWithSpecialChars);
	}

	@Test
	void shouldHandleSpecialCharactersInContentFieldName() {
		String contentFieldWithSpecialChars = "content_field_name";
		this.options.setContentFieldName(contentFieldWithSpecialChars);
		assertThat(this.options.getContentFieldName()).isEqualTo(contentFieldWithSpecialChars);
	}

	@Test
	void shouldHandleSpecialCharactersInMetaFieldPrefix() {
		String metaPrefixWithSpecialChars = "meta-prefix_";
		this.options.setMetaFieldPrefix(metaPrefixWithSpecialChars);
		assertThat(this.options.getMetaFieldPrefix()).isEqualTo(metaPrefixWithSpecialChars);
	}

	@Test
	void shouldHandleMultipleSetterCallsOnSameField() {
		this.options.setObjectClass("FirstObjectClass");
		assertThat(this.options.getObjectClass()).isEqualTo("FirstObjectClass");

		this.options.setObjectClass("SecondObjectClass");
		assertThat(this.options.getObjectClass()).isEqualTo("SecondObjectClass");

		this.options.setContentFieldName("firstContentField");
		assertThat(this.options.getContentFieldName()).isEqualTo("firstContentField");

		this.options.setContentFieldName("secondContentField");
		assertThat(this.options.getContentFieldName()).isEqualTo("secondContentField");
	}

	@Test
	void shouldPreserveStateAfterPartialSetup() {
		this.options.setObjectClass("PartialObjectClass");

		// Attempt to set invalid content field
		assertThatThrownBy(() -> this.options.setContentFieldName(null)).isInstanceOf(IllegalArgumentException.class);

		// Verify object class is still set correctly
		assertThat(this.options.getObjectClass()).isEqualTo("PartialObjectClass");
	}

	@Test
	void shouldValidateCaseSensitivity() {
		this.options.setObjectClass("TestClass");
		assertThat(this.options.getObjectClass()).isEqualTo("TestClass");

		this.options.setObjectClass("testclass");
		assertThat(this.options.getObjectClass()).isEqualTo("testclass");
		assertThat(this.options.getObjectClass()).isNotEqualTo("TestClass");
	}

}
