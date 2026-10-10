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

package org.springframework.ai.wavespeed;

import org.junit.jupiter.api.Test;

import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.wavespeed.api.WaveSpeedApi;
import org.springframework.ai.wavespeed.api.WaveSpeedImageOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link WaveSpeedImageOptions}.
 *
 * @author Zeyi Cheng
 */
class WaveSpeedImageOptionsTests {

	@Test
	void shouldPreferRuntimeOptionsOverDefaultOptions() {
		WaveSpeedImageModel imageModel = new WaveSpeedImageModel(mock(WaveSpeedApi.class));

		WaveSpeedImageOptions defaultOptions = WaveSpeedImageOptions.builder()
			.model("default-model")
			.size("1024*1024")
			.seed(1234)
			.build();

		WaveSpeedImageOptions runtimeOptions = WaveSpeedImageOptions.builder()
			.model("runtime-model")
			.size("2048*2048")
			.seed(5678)
			.build();

		WaveSpeedImageOptions merged = imageModel.mergeOptions(runtimeOptions, defaultOptions);

		assertThat(merged.getModel()).isEqualTo("runtime-model");
		assertThat(merged.getSize()).isEqualTo("2048*2048");
		assertThat(merged.getSeed()).isEqualTo(5678);
	}

	@Test
	void shouldFallBackToDefaultOptionsWhenRuntimeOptionsAreEmpty() {
		WaveSpeedImageModel imageModel = new WaveSpeedImageModel(mock(WaveSpeedApi.class));

		WaveSpeedImageOptions defaultOptions = WaveSpeedImageOptions.builder()
			.model("default-model")
			.size("1024*1024")
			.seed(1234)
			.build();

		WaveSpeedImageOptions merged = imageModel.mergeOptions(WaveSpeedImageOptions.builder().build(), defaultOptions);

		assertThat(merged.getModel()).isEqualTo("default-model");
		assertThat(merged.getSize()).isEqualTo("1024*1024");
		assertThat(merged.getSeed()).isEqualTo(1234);
	}

	@Test
	void shouldMergePortableImageOptions() {
		WaveSpeedImageModel imageModel = new WaveSpeedImageModel(mock(WaveSpeedApi.class));

		WaveSpeedImageOptions defaultOptions = WaveSpeedImageOptions.builder().model("default-model").build();

		ImageOptions runtimeOptions = ImageOptionsBuilder.builder().width(1280).height(720).build();

		WaveSpeedImageOptions merged = imageModel.mergeOptions(runtimeOptions, defaultOptions);

		assertThat(merged.getModel()).isEqualTo("default-model");
		assertThat(merged.getWidth()).isEqualTo(1280);
		assertThat(merged.getHeight()).isEqualTo(720);
		assertThat(merged.toSizeString()).isEqualTo("1280*720");
	}

	@Test
	void sizeShouldTakePrecedenceOverWidthAndHeight() {
		WaveSpeedImageOptions options = WaveSpeedImageOptions.builder()
			.size("1024*1024")
			.width(512)
			.height(512)
			.build();

		assertThat(options.toSizeString()).isEqualTo("1024*1024");
	}

	@Test
	void sizeStringShouldBeNullWhenNotConfigured() {
		assertThat(WaveSpeedImageOptions.builder().build().toSizeString()).isNull();
		assertThat(WaveSpeedImageOptions.builder().width(512).build().toSizeString()).isNull();
	}

}
