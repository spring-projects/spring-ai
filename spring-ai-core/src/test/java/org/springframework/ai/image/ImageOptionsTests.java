/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.image;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link ImageOptions}.
 *
 * @author youngmon
 * @since 0.8.1
 */
public class ImageOptionsTests {

	@Test
	void createImageOptionsTest() {
		ImageOptions options = ImageOptionsBuilder.builder().build();

		assertThat(options).isNotNull();
		assertThat(options).isInstanceOf(ImageOptions.class);
	}

	@Test
	void initImageOptionsTest() {
		ImageOptions options = ImageOptionsBuilder.builder().build();

		assertThat(options.getN()).isNull();
		assertThat(options.getModel()).isNull();
		assertThat(options.getHeight()).isNull();
		assertThat(options.getHeight()).isNull();
		assertThat(options.getWidth()).isNull();
	}

	@Test
	void assignImageOptionsTest() {
		String responseFormat = "url";
		String model = "dall-e-3";
		Integer n = 3;
		Integer width = 512;
		Integer height = 768;

		ImageOptions options = ImageOptionsBuilder.builder()
			.withResponseFormat(responseFormat)
			.withWidth(width)
			.withHeight(height)
			.withModel(model)
			.withN(n)
			.build();

		assertThat(options.getWidth()).isEqualTo(width);
		assertThat(options.getHeight()).isEqualTo(height);
		assertThat(options.getModel()).isEqualTo(model);
		assertThat(options.getN()).isEqualTo(n);
		assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
	}

	@Test
	void immutableTest() {
		ImageOptions options = ImageOptionsBuilder.builder().build();
		ImageOptions options1 = ImageOptionsBuilder.builder(options).build();

		assertThat(options).isNotSameAs(options1);
	}

}
