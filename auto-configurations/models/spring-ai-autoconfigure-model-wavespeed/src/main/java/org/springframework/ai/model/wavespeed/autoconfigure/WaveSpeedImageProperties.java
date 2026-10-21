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

package org.springframework.ai.model.wavespeed.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.wavespeed.api.WaveSpeedApi;
import org.springframework.ai.wavespeed.api.WaveSpeedImageOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the WaveSpeed AI image model.
 *
 * @author Zeyi Cheng
 * @since 2.0.1
 */
@ConfigurationProperties(WaveSpeedImageProperties.CONFIG_PREFIX)
public class WaveSpeedImageProperties extends WaveSpeedParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.wavespeed.image";

	public static final String DEFAULT_IMAGE_MODEL = WaveSpeedApi.DEFAULT_IMAGE_MODEL;

	/**
	 * The model to use for image generation. The model is passed in the URL as a path
	 * parameter.
	 */
	private String model = DEFAULT_IMAGE_MODEL;

	/**
	 * The size of the generated image as {width}*{height}, for example 1024*1024.
	 */
	private @Nullable String size;

	/**
	 * The width of the generated image, in pixels. Ignored when size is set.
	 */
	private @Nullable Integer width;

	/**
	 * The height of the generated image, in pixels. Ignored when size is set.
	 */
	private @Nullable Integer height;

	/**
	 * The random seed used for generation, or -1 for a random seed.
	 */
	private @Nullable Integer seed;

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public @Nullable String getSize() {
		return this.size;
	}

	public void setSize(@Nullable String size) {
		this.size = size;
	}

	public @Nullable Integer getWidth() {
		return this.width;
	}

	public void setWidth(@Nullable Integer width) {
		this.width = width;
	}

	public @Nullable Integer getHeight() {
		return this.height;
	}

	public void setHeight(@Nullable Integer height) {
		this.height = height;
	}

	public @Nullable Integer getSeed() {
		return this.seed;
	}

	public void setSeed(@Nullable Integer seed) {
		this.seed = seed;
	}

	public WaveSpeedImageOptions toOptions() {
		return WaveSpeedImageOptions.builder()
			.model(this.model)
			.size(this.size)
			.width(this.width)
			.height(this.height)
			.seed(this.seed)
			.build();
	}

}
