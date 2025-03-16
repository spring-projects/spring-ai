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
 */
class OpenAiImageOptionsTests {

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

}
