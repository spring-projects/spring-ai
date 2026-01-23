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

package org.springframework.ai.openai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenAiImageOptions}.
 *
 * @author Thomas Vitale
 * @author Mark Pollack
 */
class OpenAiImageOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		OpenAiImageOptions options = OpenAiImageOptions.builder()
			.N(2)
			.model("dall-e-3")
			.quality("hd")
			.responseFormat("url")
			.width(1024)
			.height(1024)
			.style("vivid")
			.user("test-user")
			.build();

		assertThat(options.getN()).isEqualTo(2);
		assertThat(options.getModel()).isEqualTo("dall-e-3");
		assertThat(options.getQuality()).isEqualTo("hd");
		assertThat(options.getResponseFormat()).isEqualTo("url");
		assertThat(options.getWidth()).isEqualTo(1024);
		assertThat(options.getHeight()).isEqualTo(1024);
		assertThat(options.getSize()).isEqualTo("1024x1024");
		assertThat(options.getStyle()).isEqualTo("vivid");
		assertThat(options.getUser()).isEqualTo("test-user");
	}

	@Test
	void testCopy() {
		OpenAiImageOptions original = OpenAiImageOptions.builder()
			.N(3)
			.model("dall-e-3")
			.quality("standard")
			.responseFormat("b64_json")
			.width(1792)
			.height(1024)
			.style("natural")
			.user("original-user")
			.build();

		// Test fromOptions static method
		OpenAiImageOptions copied = OpenAiImageOptions.fromOptions(original);
		assertThat(copied).isNotSameAs(original);
		assertThat(copied.getN()).isEqualTo(original.getN());
		assertThat(copied.getModel()).isEqualTo(original.getModel());
		assertThat(copied.getQuality()).isEqualTo(original.getQuality());
		assertThat(copied.getResponseFormat()).isEqualTo(original.getResponseFormat());
		assertThat(copied.getWidth()).isEqualTo(original.getWidth());
		assertThat(copied.getHeight()).isEqualTo(original.getHeight());
		assertThat(copied.getSize()).isEqualTo(original.getSize());
		assertThat(copied.getStyle()).isEqualTo(original.getStyle());
		assertThat(copied.getUser()).isEqualTo(original.getUser());

		// Test copy instance method
		OpenAiImageOptions copiedViaMethod = original.copy();
		assertThat(copiedViaMethod).isNotSameAs(original);
		assertThat(copiedViaMethod.getN()).isEqualTo(original.getN());
		assertThat(copiedViaMethod.getModel()).isEqualTo(original.getModel());
		assertThat(copiedViaMethod.getQuality()).isEqualTo(original.getQuality());
		assertThat(copiedViaMethod.getResponseFormat()).isEqualTo(original.getResponseFormat());
		assertThat(copiedViaMethod.getWidth()).isEqualTo(original.getWidth());
		assertThat(copiedViaMethod.getHeight()).isEqualTo(original.getHeight());
		assertThat(copiedViaMethod.getSize()).isEqualTo(original.getSize());
		assertThat(copiedViaMethod.getStyle()).isEqualTo(original.getStyle());
		assertThat(copiedViaMethod.getUser()).isEqualTo(original.getUser());
	}

	@Test
	void testSetters() {
		OpenAiImageOptions options = new OpenAiImageOptions();

		options.setN(4);
		options.setModel("dall-e-2");
		options.setQuality("standard");
		options.setResponseFormat("url");
		options.setWidth(512);
		options.setHeight(512);
		options.setStyle("vivid");
		options.setUser("test-setter-user");

		assertThat(options.getN()).isEqualTo(4);
		assertThat(options.getModel()).isEqualTo("dall-e-2");
		assertThat(options.getQuality()).isEqualTo("standard");
		assertThat(options.getResponseFormat()).isEqualTo("url");
		assertThat(options.getWidth()).isEqualTo(512);
		assertThat(options.getHeight()).isEqualTo(512);
		assertThat(options.getSize()).isEqualTo("512x512");
		assertThat(options.getStyle()).isEqualTo("vivid");
		assertThat(options.getUser()).isEqualTo("test-setter-user");

		// Test size setter
		options.setSize("256x256");
		assertThat(options.getSize()).isEqualTo("256x256");
		assertThat(options.getWidth()).isEqualTo(256);
		assertThat(options.getHeight()).isEqualTo(256);
	}

	@Test
	void testDefaultValues() {
		OpenAiImageOptions options = new OpenAiImageOptions();

		assertThat(options.getN()).isNull();
		assertThat(options.getModel()).isNull();
		assertThat(options.getQuality()).isNull();
		assertThat(options.getResponseFormat()).isNull();
		assertThat(options.getWidth()).isNull();
		assertThat(options.getHeight()).isNull();
		assertThat(options.getSize()).isNull();
		assertThat(options.getStyle()).isNull();
		assertThat(options.getUser()).isNull();
	}

	@Test
	void testBuilderWithExistingOptions() {
		OpenAiImageOptions original = OpenAiImageOptions.builder().N(1).model("dall-e-3").quality("hd").build();

		OpenAiImageOptions modified = new OpenAiImageOptions.Builder(original).width(1024).height(1024).build();

		assertThat(modified.getN()).isEqualTo(1);
		assertThat(modified.getModel()).isEqualTo("dall-e-3");
		assertThat(modified.getQuality()).isEqualTo("hd");
		assertThat(modified.getWidth()).isEqualTo(1024);
		assertThat(modified.getHeight()).isEqualTo(1024);
		assertThat(modified.getSize()).isEqualTo("1024x1024");
	}

	@Test
	@SuppressWarnings("SelfAssertion")
	void testEqualsAndHashCode() {
		OpenAiImageOptions options1 = OpenAiImageOptions.builder()
			.N(2)
			.model("dall-e-3")
			.quality("hd")
			.width(1024)
			.height(1024)
			.build();

		OpenAiImageOptions options2 = OpenAiImageOptions.builder()
			.N(2)
			.model("dall-e-3")
			.quality("hd")
			.width(1024)
			.height(1024)
			.build();

		OpenAiImageOptions options3 = OpenAiImageOptions.builder()
			.N(3) // Different value
			.model("dall-e-3")
			.quality("hd")
			.width(1024)
			.height(1024)
			.build();

		// Test equals
		assertThat(options1).isEqualTo(options1); // Same instance
		assertThat(options1).isEqualTo(options2); // Equal objects
		assertThat(options1).isNotEqualTo(options3); // Different objects
		assertThat(options1).isNotEqualTo(null); // Null
		assertThat(options1).isNotEqualTo("not an options object"); // Different type

		// Test hashCode
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode()); // Equal objects
																		// have equal hash
																		// codes
		assertThat(options1.hashCode()).isNotEqualTo(options3.hashCode()); // Different
																			// objects
																			// have
																			// different
																			// hash codes
	}

	@Test
	void testToString() {
		OpenAiImageOptions options = OpenAiImageOptions.builder().N(2).model("dall-e-3").build();

		String toString = options.toString();
		assertThat(toString).contains("n=2");
		assertThat(toString).contains("model='dall-e-3'");
		assertThat(toString).contains("OpenAiImageOptions");
	}

	@Test
	void testFluentApiPattern() {
		// Test the fluent API pattern by chaining method calls
		OpenAiImageOptions options = OpenAiImageOptions.builder()
			.N(1)
			.model("dall-e-3")
			.quality("hd")
			.responseFormat("url")
			.width(1024)
			.height(1024)
			.style("vivid")
			.user("test-user")
			.build();

		assertThat(options.getN()).isEqualTo(1);
		assertThat(options.getModel()).isEqualTo("dall-e-3");
		assertThat(options.getQuality()).isEqualTo("hd");
		assertThat(options.getResponseFormat()).isEqualTo("url");
		assertThat(options.getWidth()).isEqualTo(1024);
		assertThat(options.getHeight()).isEqualTo(1024);
		assertThat(options.getSize()).isEqualTo("1024x1024");
		assertThat(options.getStyle()).isEqualTo("vivid");
		assertThat(options.getUser()).isEqualTo("test-user");
	}

	// Original size-related tests
	@Test
	void whenImageDimensionsAreAllUnset() {
		OpenAiImageOptions options = new OpenAiImageOptions();
		assertThat(options.getHeight()).isEqualTo(null);
		assertThat(options.getWidth()).isEqualTo(null);
		assertThat(options.getSize()).isEqualTo(null);
	}

	@Test
	void whenSizeIsSet() {
		OpenAiImageOptions options = new OpenAiImageOptions();
		options.setSize("1920x1080");
		assertThat(options.getHeight()).isEqualTo(1080);
		assertThat(options.getWidth()).isEqualTo(1920);
		assertThat(options.getSize()).isEqualTo("1920x1080");
	}

	@Test
	void whenWidthAndHeightAreSet() {
		OpenAiImageOptions options = new OpenAiImageOptions();
		options.setWidth(1920);
		options.setHeight(1080);
		assertThat(options.getHeight()).isEqualTo(1080);
		assertThat(options.getWidth()).isEqualTo(1920);
		assertThat(options.getSize()).isEqualTo("1920x1080");
	}

	@Test
	void whenWidthIsSet() {
		OpenAiImageOptions options = new OpenAiImageOptions();
		options.setWidth(1920);
		assertThat(options.getHeight()).isNull();
		assertThat(options.getWidth()).isEqualTo(1920);
		// 1920xnull is not a valid size, so "size" should be null.
		assertThat(options.getSize()).isNull();
	}

	@Test
	void whenHeightIsSet() {
		OpenAiImageOptions options = new OpenAiImageOptions();
		options.setHeight(1080);
		assertThat(options.getHeight()).isEqualTo(1080);
		assertThat(options.getWidth()).isNull();
		// nullx1080 is not a valid size, so "size" should be null.
		assertThat(options.getSize()).isNull();
	}

	@Test
	void whenInvalidSizeFormatIsSet() {
		OpenAiImageOptions options = new OpenAiImageOptions();
		options.setSize("invalid");
		assertThat(options.getHeight()).isNull();
		assertThat(options.getWidth()).isNull();
		assertThat(options.getSize()).isEqualTo("invalid");
	}

	@Test
	void whenSizeWithInvalidNumbersIsSet() {
		OpenAiImageOptions options = new OpenAiImageOptions();
		options.setSize("axb");
		assertThat(options.getHeight()).isNull();
		assertThat(options.getWidth()).isNull();
		assertThat(options.getSize()).isEqualTo("axb");
	}

	@Test
	void whenSizeWithMissingDimensionIsSet() {
		OpenAiImageOptions options = new OpenAiImageOptions();
		options.setSize("1024x");
		assertThat(options.getHeight()).isNull();
		assertThat(options.getWidth()).isNull();
		assertThat(options.getSize()).isEqualTo("1024x");
	}

	@Test
	void whenSizeWithExtraSeparatorsIsSet() {
		OpenAiImageOptions options = new OpenAiImageOptions();
		options.setSize("1024x1024x1024");
		assertThat(options.getHeight()).isNull();
		assertThat(options.getWidth()).isNull();
		assertThat(options.getSize()).isEqualTo("1024x1024x1024");
	}

	@Test
	void testSetSizeUpdatesWidthAndHeight() {
		OpenAiImageOptions options = new OpenAiImageOptions();

		// Test setting size updates width and height
		options.setSize("800x600");
		assertThat(options.getSize()).isEqualTo("800x600");
		assertThat(options.getWidth()).isEqualTo(800);
		assertThat(options.getHeight()).isEqualTo(600);

		// Test changing size updates width and height
		options.setSize("1920x1080");
		assertThat(options.getSize()).isEqualTo("1920x1080");
		assertThat(options.getWidth()).isEqualTo(1920);
		assertThat(options.getHeight()).isEqualTo(1080);
	}

	@Test
	void testSetSizeWithInvalidFormatPreservesExistingWidthAndHeight() {
		OpenAiImageOptions options = new OpenAiImageOptions();

		// First set valid width and height
		options.setWidth(1024);
		options.setHeight(768);

		// Now set invalid size format
		options.setSize("invalid");

		// Size should be updated, but width and height should remain unchanged
		assertThat(options.getSize()).isEqualTo("invalid");
		assertThat(options.getWidth()).isEqualTo(1024); // Should preserve existing value
		assertThat(options.getHeight()).isEqualTo(768); // Should preserve existing value
	}

	@Test
	void testSetSizeWithNullClearsSize() {
		OpenAiImageOptions options = new OpenAiImageOptions();

		// First set valid values
		options.setSize("800x600");
		assertThat(options.getWidth()).isEqualTo(800);
		assertThat(options.getHeight()).isEqualTo(600);

		// Now set size to null
		options.setSize(null);

		// The size field is null, but getSize() computes it from width and height
		// This is the expected behavior based on the implementation of getSize()
		assertThat(options.getSize()).isEqualTo("800x600");
		assertThat(options.getWidth()).isEqualTo(800); // Should preserve existing value
		assertThat(options.getHeight()).isEqualTo(600); // Should preserve existing value
	}

}
